package com.igot.cb.service;

import org.neo4j.driver.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GraphService {

    private Logger log = LoggerFactory.getLogger(getClass().getName());

    @Autowired
    private Driver neo4jDriver;

    /**
     * Fetch organization hierarchy from Neo4j based on search criteria.
     */
    public List<Map<String, Object>> fetchHierarchy(String orgId, int maxLevel, Session session, Transaction transaction) {
        String query = "MATCH (root:Organization {mapid: $orgId}) " +
                "CALL apoc.path.subgraphNodes(root, { " +
                "   relationshipFilter: 'PARENT_OF', " +
                "   bfs: true, " +
                "   maxLevel: $maxLevel " +
                "}) YIELD node " +
                "OPTIONAL MATCH (node)-[:PARENT_OF]->(child) " +
                "WITH node, COLLECT(child.id) AS childIds " +
                "WITH COLLECT({ " +
                "    id: node.id, " +
                "    orgname: node.orgname, " +
                "    properties: apoc.map.clean(properties(node), ['labels'], []), " +
                "    parentOrgId: node.parentmapid, " +
                "    children: childIds " +
                "}) AS nodes " +
                "RETURN apoc.convert.toJson(nodes) AS hierarchy";

        Map<String, Object> params = new HashMap<>();
        params.put("orgId", orgId);
        params.put("maxLevel", maxLevel);

        return executeQuery(query, params, transaction);
    }

    /**
     * Fetch Level 1 organizations (root nodes).
     */
    public List<Map<String, Object>> fetchLevel1Organizations(Session session, Transaction transaction) {
        String query =
                "MATCH (l1:Organization) " +
                        "WHERE NOT (:Organization)-[:PARENT_OF]->(l1) " +
                        "OPTIONAL MATCH (l1)-[:PARENT_OF]->(child:Organization) " +
                        "WITH l1, COUNT(child) AS childCount " +
                        "RETURN properties(l1) AS organization, childCount";

        return executeQuery(query, Collections.emptyMap(), transaction);
    }

    /**
     * Search organizations by name.
     */
    public List<Map<String, Object>> searchOrganizations(String searchTerm, int maxLevel, Session session, Transaction transaction) {
        String query = "MATCH (matchedOrg:Organization) " +
                "WHERE toLower(matchedOrg.orgname) CONTAINS toLower($searchTerm) " +
                "CALL apoc.path.subgraphNodes(matchedOrg, { " +
                "    relationshipFilter: 'PARENT_OF', " +
                "    maxLevel: $maxLevel, " +
                "    bfs: true " +
                "}) YIELD node " +
                "OPTIONAL MATCH (node)-[:PARENT_OF]->(child) " +
                "WITH node, COLLECT(child.id) AS childIds " +
                "WITH COLLECT({ " +
                "    id: node.id, " +
                "    orgname: node.orgname, " +
                "    properties: apoc.map.clean(properties(node), ['labels'], []), " +
                "    parentOrgId: node.parentmapid, " +
                "    children: childIds " +
                "}) AS nodes " +
                "RETURN apoc.convert.toJson(nodes) AS hierarchy";

        Map<String, Object> params = new HashMap<>();
        params.put("searchTerm", searchTerm);
        params.put("maxLevel", maxLevel);

        return executeQuery(query, params, transaction);
    }

    /**
     * Helper method to execute Neo4j queries and return results as a list of maps.
     */
    private List<Map<String, Object>> executeQuery(String query, Map<String, Object> parameters, Transaction transaction) {
        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            StatementResult result = transaction.run(query, parameters);

            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> recordMap = new HashMap<>();

                for (String key : record.keys()) {
                    recordMap.put(key, record.get(key).asObject());
                }

                resultList.add(recordMap);
            }
        } catch (Exception e) {
            log.error("Error executing Neo4j query: {}", query, e);
        }

        return resultList;
    }
}