package com.hierarchyhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hierarchyhub.config.ApplicationConfiguration;
import com.hierarchyhub.config.RedisCacheMgr;
import com.hierarchyhub.dto.Constants;
import com.hierarchyhub.dto.ApiResponse;
import com.hierarchyhub.repository.OrganizationRepository;
import org.neo4j.ogm.annotation.Transient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
public class OrganizationService {

    private Logger log = LoggerFactory.getLogger(getClass().getName());

    @Autowired
    private OrganizationRepository repository;

    @Autowired
    ApplicationConfiguration configuration;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RedisCacheMgr redisCacheMgr;

    public ApiResponse fetchHierarchy(String id) {
        ApiResponse response = new ApiResponse();
        String jsonResponse = repository.findHierarchyByOrgId(id, configuration.getMaxLevel());

        try {
            if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.equals("[]")) {
                response.setResponseCode(HttpStatus.NO_CONTENT);
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrmsg("No hierarchy found for orgId: " + id);
                log.warn("No hierarchy data available for orgId: {}", id);
                return response;
            }

            List<Map<String, Object>> nodes = objectMapper.readValue(jsonResponse, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Map<String, Object>> nodeMap = nodes.stream()
                    .collect(Collectors.toMap(
                            n -> {
                                Map<String, Object> props = (Map<String, Object>) n.get("properties");
                                return props != null ? (String) props.get("mapid") : null;
                            },
                            n -> n
                    ));

            Map<String, Object> root = nodeMap.values().stream()
                    .filter(n -> {
                        Map<String, Object> props = (Map<String, Object>) n.get("properties");
                        return props != null && id.equals(props.get("mapid"));
                    })
                    .findFirst()
                    .orElse(null);

            if (root == null) {
                response.setResponseCode(HttpStatus.NO_CONTENT);
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrmsg("No root node found for orgId: " + id);
                log.warn("No root node found for orgId: {}", id);
                return response;
            }

            attachChildren(root, nodeMap);

            response.setResponseCode(HttpStatus.OK);
            response.getResult().put(Constants.RESPONSE, Constants.SUCCESS);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResult(root);

        } catch (IOException e) {
            log.error("Failed to parse json. Exception: ", e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErr(e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred. Exception: ", e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErr(e.getMessage());
        }
        return response;
    }

    private void attachChildren(Map<String, Object> node, Map<String, Map<String, Object>> nodeMap) {
        String nodeMapId = String.valueOf(((Map<String, Object>) node.get("properties")).get("mapid"));
        List<Map<String, Object>> childNodes = new ArrayList<>();

        for (Map<String, Object> potentialChild : nodeMap.values()) {
            String parentMapId = String.valueOf(((Map<String, Object>) potentialChild.get("properties")).get("parentmapid"));
            if (nodeMapId.equals(parentMapId)) {
                attachChildren(potentialChild, nodeMap);
                childNodes.add(potentialChild);
            }
        }
        node.put(Constants.CHILDREN, childNodes);
    }

    public static String generateMapid() {
        long timestamp = System.currentTimeMillis();

        Random rand = new Random();
        long randomPart = rand.nextInt(1000000);

        long mapidLong = timestamp * 1000000 + randomPart;

        DecimalFormat df = new DecimalFormat("0000000000000000000");
        return df.format(mapidLong);
    }


    public Object renameKeysRecursively(Object input) {
        if (input instanceof Map) {
            Map<String, Object> map = new HashMap<>();
            Map<?, ?> originalMap = (Map<?, ?>) input;
            for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                if (key.equalsIgnoreCase(Constants.PARENT_OF)) {
                    key = Constants.PARENT;
                }
                map.put(key, renameKeysRecursively(value));
            }
            return map;
        } else if (input instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object item : (List<?>) input) {
                list.add(renameKeysRecursively(item));
            }
            return list;
        } else {
            return input;
        }
    }

    public Object nestAdditionalPropertiesRecursive(Object input) {
        if (input instanceof Map) {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> additionalNested = new HashMap<>();
            Map<?, ?> originalMap = (Map<?, ?>) input;

            for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
                String key = entry.getKey().toString();
                Object value = nestAdditionalPropertiesRecursive(entry.getValue());

                if (key.startsWith(Constants.ADDITIONAL_PROPERTIES_DOT)) {
                    String nestedKey = key.substring("additionalProperties.".length());
                    additionalNested.put(nestedKey, value);
                } else {
                    result.put(key, value);
                }
            }
            if (!additionalNested.isEmpty()) {
                result.put(Constants.ADDITIONAL_PROPERTIES, additionalNested);
            }
            return result;
        } else if (input instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object item : (List<?>) input) {
                list.add(nestAdditionalPropertiesRecursive(item));
            }
            return list;
        } else {
            return input;
        }
    }

    public ApiResponse getSearchOrganizationHierarchy(Map<String, Object> searchCriteria) {
        ApiResponse response = new ApiResponse();

        try {
            String redisKey = objectMapper.writeValueAsString(searchCriteria);

            if (Boolean.TRUE.equals(redisCacheMgr.keyExists(redisKey))) {
                log.info("Cache hit for key: {}", redisKey);
                String cachedData = redisCacheMgr.getCache(redisKey);

                if (cachedData != null) {
                    List<Map<String, Object>> cachedHierarchy = objectMapper.readValue(
                            cachedData, new TypeReference<List<Map<String, Object>>>() {
                            }
                    );

                    response.setResponseCode(HttpStatus.OK);
                    response.getParams().setStatus(Constants.SUCCESS);
                    response.getResult().put(Constants.ORGANIZATIONS, cachedHierarchy);
                    return response;
                }

            }

            String searchTerm = (String) searchCriteria.get("orgName");
            log.info("Cache miss for key: {}. Querying Neo4j...", redisKey);
            String jsonResponse = repository.findOrganizationHierarchy(searchTerm, configuration.getMaxLevel());

            if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.equals("[]")) {
                log.warn("No organization hierarchy found for search criteria: {}", searchCriteria);
                response.setResponseCode(HttpStatus.NO_CONTENT);
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrmsg("No organizations found for given search criteria");
                response.getResult().put(Constants.ORGANIZATIONS, Collections.emptyList());
                return response;
            }

            List<Map<String, Object>> nodes = objectMapper.readValue(jsonResponse, new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Map<String, Object>> nodeMap = nodes.stream()
                    .filter(n -> ((Map<String, Object>) n.get("properties")).get("mapid") != null)
                    .collect(Collectors.toMap(n -> (String) ((Map<String, Object>) n.get("properties")).get("mapid"), n -> n));

            Set<String> allMapIds = nodes.stream()
                    .map(n -> (String) ((Map<String, Object>) n.get("properties")).get("mapid"))
                    .collect(Collectors.toSet());

            List<Map<String, Object>> rootNodes = nodes.stream()
                    .filter(n -> {
                        Object parentId = ((Map<String, Object>) n.get("properties")).get("parentmapid");
                        return parentId == null || !allMapIds.contains(parentId.toString());
                    })
                    .collect(Collectors.toList());

            if (rootNodes.isEmpty()) {
                log.warn("No root nodes found for search criteria: {}", searchCriteria);
                response.setResponseCode(HttpStatus.NO_CONTENT);
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrmsg("No root nodes found for given search criteria");
                response.getResult().put(Constants.ORGANIZATIONS, Collections.emptyList());
                return response;
            }

            for (Map<String, Object> root : rootNodes) {
                attachChildren(root, nodeMap);
            }
            redisCacheMgr.putCache(redisKey, rootNodes, 3600);
            log.info("Stored hierarchy in Redis with key: {}", redisKey);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.getResult().put(Constants.ORGANIZATIONS, rootNodes);

        } catch (Exception e) {
            log.error("Error processing search request: {}. Exception: {}", searchCriteria, e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrmsg("Unexpected error while processing hierarchy search");
        }

        return response;
    }

    public ApiResponse getLevel1Organizations() {
        ApiResponse response = new ApiResponse();

        try {
            List<Map<String, Object>> organizations = repository.getLevel1OrganizationsWithChildrenCount();

            if (organizations == null || organizations.isEmpty()) {
                log.warn("No Level 1 organizations found");
                response.setResponseCode(HttpStatus.NO_CONTENT);
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrmsg("No Level 1 organizations found");
                response.getResult().put(Constants.ORGANIZATIONS, Collections.emptyList());
                return response;
            }

            List<Map<String, Object>> flattenedOrganizations = new ArrayList<>();
            for (Map<String, Object> entry : organizations) {
                Map<String, Object> orgData = new HashMap<>((Map<String, Object>) entry.get("organization"));
                orgData.put("childCount", entry.get("childCount"));
                flattenedOrganizations.add(orgData);
            }
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.getResult().put(Constants.ORGANIZATIONS, flattenedOrganizations);

        } catch (Exception e) {
            log.error("Error fetching Level 1 organizations: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrmsg("An unexpected error occurred while fetching Level 1 organizations");
        }

        return response;
    }
}
