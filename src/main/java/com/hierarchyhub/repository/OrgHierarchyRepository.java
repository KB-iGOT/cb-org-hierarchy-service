package com.hierarchyhub.repository;

import com.hierarchyhub.config.OrgHierarchyProperties;
import com.hierarchyhub.model.OrgNode;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure data access on {@code org_hierarchy}: reads, writes and row locking. No business rule is decided here (no
 * "already has a parent", "is an L0", "cycle detected", ...) - that belongs to
 * {@link com.hierarchyhub.service.impl.OrgHierarchyServiceImpl}, which calls these methods inside its own
 * {@code @Transactional} boundary.
 */
@Repository
public class OrgHierarchyRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final int maxDepth;

    public OrgHierarchyRepository(NamedParameterJdbcTemplate jdbc, OrgHierarchyProperties props) {
        this.jdbc = jdbc;
        this.maxDepth = props.getMaxDepth();
    }

    public Optional<OrgNode> findById(String id) {
        List<OrgNode> rows = jdbc.query(OrgHierarchySql.FIND_NODE, params("id", id), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** The nodes that exist among {@code ids}, keyed by id; an id missing from the map is not in the hierarchy at all. */
    public Map<String, OrgNode> findExisting(Collection<String> ids) {
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, OrgNode> out = new LinkedHashMap<>();
        for (OrgNode node : jdbc.query(OrgHierarchySql.FIND_NODES, idsParam(ids), ROW_MAPPER)) {
            out.put(node.id(), node);
        }
        return out;
    }

    /** The chain of ids from {@code id} up to its root L0 (itself first), at most {@code maxDepth} hops. Empty if {@code id} is unknown. */
    public List<String> chainUp(String id) {
        MapSqlParameterSource p = params("id", id).addValue("maxDepth", maxDepth);
        return jdbc.queryForList(OrgHierarchySql.CHAIN_UP, p, String.class);
    }

    public boolean hasChildren(String id) {
        Boolean has = jdbc.queryForObject(OrgHierarchySql.HAS_CHILDREN, params("id", id), Boolean.class);
        return Boolean.TRUE.equals(has);
    }

    /**
     * Up to {@code limit} descendants of {@code orgId}, down to {@code depthLimit} hops (1 = direct children only),
     * ordered by name then id. {@code queryExact}/{@code queryPattern} (both {@code null}, or both set together - see
     * {@link com.hierarchyhub.util.AppUtil#likeContainsPattern}) narrow this to rows whose id exactly matches or whose
     * name contains the search term. Pass {@code cursorName}/{@code cursorId} (both from the last row of a previous
     * page) to resume after it; pass both {@code null} for the first page.
     */
    public List<OrgNode> findDescendants(String orgId, int depthLimit, String queryExact, String queryPattern,
                                          String cursorName, String cursorId, int limit) {
        MapSqlParameterSource p = params("orgId", orgId).addValue("depthLimit", depthLimit).addValue("limit", limit)
                .addValue("queryExact", queryExact, Types.VARCHAR).addValue("queryPattern", queryPattern, Types.VARCHAR);
        if (cursorName == null) {
            return jdbc.query(OrgHierarchySql.DESCENDANTS_FIRST_PAGE, p, ROW_MAPPER);
        }
        p.addValue("cursorName", cursorName).addValue("cursorId", cursorId);
        return jdbc.query(OrgHierarchySql.DESCENDANTS_NEXT_PAGE, p, ROW_MAPPER);
    }

    /** Same as {@link #findDescendants}, but classic {@code limit}/{@code offset} paging - see {@link OrgHierarchySql#DESCENDANTS_PAGE_BY_OFFSET}. */
    public List<OrgNode> findDescendantsByOffset(String orgId, int depthLimit, String queryExact, String queryPattern, int offset, int limit) {
        MapSqlParameterSource p = params("orgId", orgId).addValue("depthLimit", depthLimit).addValue("offset", offset).addValue("limit", limit)
                .addValue("queryExact", queryExact, Types.VARCHAR).addValue("queryPattern", queryPattern, Types.VARCHAR);
        return jdbc.query(OrgHierarchySql.DESCENDANTS_PAGE_BY_OFFSET, p, ROW_MAPPER);
    }

    public int countDescendants(String id) {
        MapSqlParameterSource p = params("id", id).addValue("maxDepth", maxDepth);
        Integer count = jdbc.queryForObject(OrgHierarchySql.COUNT_DESCENDANTS, p, Integer.class);
        return count == null ? 0 : count;
    }

    /** Every row of the subtree rooted at {@code id} (itself included), at most {@code maxDepth} hops deep. */
    public List<OrgNode> findSubtree(String id) {
        MapSqlParameterSource p = params("id", id).addValue("maxDepth", maxDepth);
        return jdbc.query(OrgHierarchySql.SUBTREE, p, ROW_MAPPER);
    }

    /** Locks the given rows in the caller-supplied order (sort before calling, so two writers never deadlock). Must run inside a transaction. */
    public void lockRows(Collection<String> ids) {
        if (!ids.isEmpty()) {
            jdbc.query(OrgHierarchySql.LOCK_FOR_UPDATE, idsParam(ids), (rs, i) -> rs.getString("id"));
        }
    }

    /** Inserts a new child row under {@code parentId}; empty when the id already exists (another request created it first). */
    public Optional<OrgNode> createMapped(String id, String name, String parentId, String userId) {
        List<String> inserted = jdbc.queryForList(OrgHierarchySql.CREATE_MAPPED,
                params("id", id).addValue("name", name).addValue("parent", parentId).addValue("by", userId), String.class);
        return inserted.isEmpty() ? Optional.empty() : findById(id);
    }

    public void setParent(String id, String parentId, String userId) {
        jdbc.update(OrgHierarchySql.SET_PARENT, params("id", id).addValue("parent", parentId).addValue("by", userId));
    }

    public void unlinkRow(String id, String userId) {
        jdbc.update(OrgHierarchySql.UNLINK, params("id", id).addValue("by", userId));
    }

    /** Unlinks every row in {@code ids} (a whole subtree, root included) in one statement - see {@link OrgHierarchySql#CLEAR_SUBTREE}. */
    public void clearSubtree(Collection<String> ids, String userId) {
        jdbc.update(OrgHierarchySql.CLEAR_SUBTREE, idsParam(ids).addValue("by", userId));
    }

    public void incrementChildCount(String parentId) {
        jdbc.update(OrgHierarchySql.COUNT_UP, params("id", parentId).addValue("n", 1));
    }

    public void decrementChildCount(String parentId) {
        jdbc.update(OrgHierarchySql.COUNT_DOWN, params("id", parentId));
    }

    private static MapSqlParameterSource params(String name, Object value) {
        return new MapSqlParameterSource(name, value);
    }

    private static MapSqlParameterSource idsParam(Collection<String> ids) {
        return new MapSqlParameterSource("ids", ids);
    }

    private static final RowMapper<OrgNode> ROW_MAPPER = OrgHierarchyRepository::mapRow;

    private static OrgNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new OrgNode(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("parent_id"),
                rs.getBoolean("idle"),
                rs.getInt("child_count"),
                createdAt == null ? null : createdAt.toInstant(),
                rs.getString("created_by"),
                updatedAt == null ? null : updatedAt.toInstant(),
                rs.getString("updated_by"));
    }
}
