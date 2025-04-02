package com.igot.cb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.config.ApplicationConfiguration;
import com.igot.cb.config.RedisCacheMgr;
import com.igot.cb.dto.Constants;
import com.igot.cb.dto.ApiResponse;
import org.neo4j.driver.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OrganizationService {

    private Logger log = LoggerFactory.getLogger(getClass().getName());

    @Autowired
    ApplicationConfiguration configuration;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RedisCacheMgr redisCacheMgr;

    @Autowired
    private Driver neo4jDriver;

    @Autowired
    private GraphService graphService;


    public ApiResponse fetchHierarchy(String id) {
        ApiResponse response = new ApiResponse();

        try(Session session = neo4jDriver.session(); Transaction transaction = session.beginTransaction()) {
            List<Map<String, Object>> nodes = graphService.fetchHierarchy(id, configuration.getMaxLevel(), session, transaction);

            if (nodes == null || nodes.isEmpty()) {
                response.setResponseCode(HttpStatus.NO_CONTENT);
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrmsg("No hierarchy found for orgId: " + id);
                return response;
            }
            List<Map<String, Object>> parsedNodes = new ArrayList<>();

            for (Map<String, Object> node : nodes) {
                Object hierarchyObj = node.get("hierarchy");

                if (hierarchyObj instanceof String) {
                    try {
                        List<Map<String, Object>> hierarchyList = objectMapper.readValue(
                                (String) hierarchyObj, new TypeReference<List<Map<String, Object>>>() {});
                        parsedNodes.addAll(hierarchyList);
                    } catch (Exception e) {
                        e.printStackTrace();
                        response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
                        response.getParams().setStatus(Constants.FAILED);
                        response.getParams().setErrmsg("Error parsing hierarchy JSON.");
                        return response;
                    }
                }
            }

            Map<String, Map<String, Object>> nodeMap = parsedNodes.stream()
                    .collect(Collectors.toMap(
                            n -> {
                                Map<String, Object> props = (Map<String, Object>) n.get("properties");
                                return props != null ? (String) props.get("mapid") : null;
                            },
                            n -> n,
                            (existing, replacement) -> existing
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
                return response;
            }

            // Attach children recursively
            attachChildren(root, nodeMap);

            transaction.success();

            response.setResponseCode(HttpStatus.OK);
            response.getResult().put(Constants.RESPONSE, Constants.SUCCESS);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResult(root);

        } catch (Exception e) {
            e.printStackTrace();
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrmsg(e.getMessage());
        }

        return response;
    }


    private ApiResponse buildNoContentResponse(String message) {
        ApiResponse response = new ApiResponse();
        response.setResponseCode(HttpStatus.NO_CONTENT);
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrmsg(message);
        return response;
    }

    private ApiResponse buildErrorResponse(String message, Exception e) {
        ApiResponse response = new ApiResponse();
        response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrmsg(message + ": " + e.getMessage());
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
                            });
                    response.setResponseCode(HttpStatus.OK);
                    response.getParams().setStatus(Constants.SUCCESS);
                    response.getResult().put(Constants.RESPONSE, Constants.SUCCESS);
                    response.getResult().put(Constants.ORGANIZATIONS, cachedHierarchy);
                    return response;
                }
            }

            try(Session session = neo4jDriver.session(); Transaction transaction = session.beginTransaction()) {
                String searchTerm = (String) searchCriteria.get("orgName");
                log.info("Cache miss for key: {}. Querying Neo4j...", redisKey);

                List<Map<String, Object>> nodes = new ArrayList<>();
                nodes = graphService.searchOrganizations(searchTerm, configuration.getMaxLevel(), session, transaction);


                if (nodes.isEmpty()) {
                    return buildNoContentResponse("No organizations found for given search criteria");
                }
                List<Map<String, Object>> parsedNodes = new ArrayList<>();
                for (Map<String, Object> node : nodes) {
                    if (node.containsKey("hierarchy")) {
                        String jsonString = (String) node.get("hierarchy");
                        List<Map<String, Object>> parsedHierarchy = objectMapper.readValue(
                                jsonString, new TypeReference<List<Map<String, Object>>>() {});
                        parsedNodes.addAll(parsedHierarchy);
                    }
                }

                Map<String, Map<String, Object>> nodeMap = parsedNodes.stream()
                        .filter(n -> ((Map<String, Object>) n.get("properties")).get("mapid") != null)
                        .collect(Collectors.toMap(
                                n -> (String) ((Map<String, Object>) n.get("properties")).get("mapid"),
                                n -> n));


                Set<String> allMapIds = nodeMap.keySet();
                List<Map<String, Object>> rootNodes = parsedNodes.stream()
                        .filter(n -> {
                            Object parentId = ((Map<String, Object>) n.get("properties")).get("parentmapid");
                            return parentId == null || !allMapIds.contains(parentId.toString());
                        })
                        .collect(Collectors.toList());

                if (rootNodes.isEmpty()) {
                    return buildNoContentResponse("No root nodes found for given search criteria");
                }

                for (Map<String, Object> root : rootNodes) {
                    attachChildren(root, nodeMap);
                }

                redisCacheMgr.putCache(redisKey, rootNodes, 3600);
                log.info("Stored hierarchy in Redis with key: {}", redisKey);
                transaction.success();
                response.setResponseCode(HttpStatus.OK);
                response.getParams().setStatus(Constants.SUCCESS);
                response.getResult().put(Constants.RESPONSE, Constants.SUCCESS);
                response.getResult().put(Constants.ORGANIZATIONS, rootNodes);
                return response;
            }
        } catch (Exception e) {
            log.error("Error processing search request: {}. Exception: {}", searchCriteria, e.getMessage(), e);
            return buildErrorResponse("Unexpected error while processing hierarchy search", e);
        }
    }


    public ApiResponse getLevel1Organizations() {
        ApiResponse response = new ApiResponse();

        try(Session session = neo4jDriver.session(); Transaction transaction = session.beginTransaction()) {
            List<Map<String, Object>> organizations = graphService.fetchLevel1Organizations(session, transaction);

            if (organizations.isEmpty()) {
                log.warn("No Level 1 organizations found");
                return buildNoContentResponse("No Level 1 organizations found");
            }

            log.info("Fetched {} Level 1 organizations", organizations.size());

            List<Map<String, Object>> flattenedOrganizations = new ArrayList<>();
            for (Map<String, Object> entry : organizations) {
                Map<String, Object> orgData = new HashMap<>((Map<String, Object>) entry.get("organization"));
                orgData.put("childCount", entry.get("childCount"));
                flattenedOrganizations.add(orgData);
            }
            transaction.success();
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.getResult().put(Constants.RESPONSE, Constants.SUCCESS);
            response.getResult().put(Constants.ORGANIZATIONS, flattenedOrganizations);
            return response;

        } catch (Exception e) {
            log.error("Error fetching Level 1 organizations: {}", e.getMessage(), e);
            return buildErrorResponse("An unexpected error occurred while fetching Level 1 organizations", e);
        }
    }

}
