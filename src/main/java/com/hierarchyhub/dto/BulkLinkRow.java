package com.hierarchyhub.dto;

/** One valid, parsed data row of a {@code /bulk/link} sheet: the orgId pulled out of each of its two cells. */
public final class BulkLinkRow {

    private final String parentOrgId;
    private final String childOrgId;

    public BulkLinkRow(String parentOrgId, String childOrgId) {
        this.parentOrgId = parentOrgId;
        this.childOrgId = childOrgId;
    }

    public String getParentOrgId() {
        return parentOrgId;
    }

    public String getChildOrgId() {
        return childOrgId;
    }
}
