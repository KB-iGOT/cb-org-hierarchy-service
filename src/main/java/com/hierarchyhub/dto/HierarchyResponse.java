package com.hierarchyhub.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Result of {@code POST /hierarchy/{orgId}}: the requested org's own fields ({@code id}, {@code name}, {@code idle},
 * {@code childCount}, {@code associations}) flattened directly into the response, plus {@code count} - the total
 * number of nodes in the subtree, itself included.
 */
public class HierarchyResponse {

    @JsonUnwrapped
    private final OrgTreeNode org;
    private final int count;

    public HierarchyResponse(OrgTreeNode org, int count) {
        this.org = org;
        this.count = count;
    }

    public OrgTreeNode getOrg() {
        return org;
    }

    public int getCount() {
        return count;
    }
}
