package com.hierarchyhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of {@code POST /read/{orgId}}: {@code orgId} itself (its own {@code id}/{@code name}/{@code idle}/
 * {@code childCount} - the same field names {@code GET /{orgId}/complete} uses for a node), plus one page of its
 * descendants under {@code associations} (direct children by default; deeper when {@code depth}/a {@code query} are
 * given - see {@link ReadChildrenRequest.Body}). Unlike {@code /complete}, {@code associations} is always a flat,
 * paginated list, never nested - each entry is one descendant, not a subtree.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadChildrenResponse {

    private final String id;
    private final String name;
    private final boolean idle;
    private final int childCount;
    private final List<ChildOrgSummary> associations;
    private final int limit;
    private final boolean hasMore;
    private final String nextCursor;

    public ReadChildrenResponse(String id, String name, boolean idle, int childCount, List<ChildOrgSummary> associations,
                                 int limit, boolean hasMore, String nextCursor) {
        this.id = id;
        this.name = name;
        this.idle = idle;
        this.childCount = childCount;
        this.associations = associations;
        this.limit = limit;
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isIdle() {
        return idle;
    }

    public int getChildCount() {
        return childCount;
    }

    public List<ChildOrgSummary> getAssociations() {
        return associations;
    }

    public int getLimit() {
        return limit;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
