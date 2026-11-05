package com.hierarchyhub.dto;

/**
 * One row of a {@code /bulk/link} file that did not end up linked: either rejected while reading the sheet
 * ({@code rowNumber} set, orgIds possibly blank) or rejected by the same rule {@code /link} itself enforces
 * ({@code rowNumber} is {@code null} - it was already a valid row, the failure is business, not a parsing one).
 * Immutable: built once, only ever logged, never serialized or mutated afterwards.
 */
public final class BulkLinkRowFailure {

    private final Integer rowNumber;
    private final String parentOrgId;
    private final String childOrgId;
    private final String reason;

    public BulkLinkRowFailure(Integer rowNumber, String parentOrgId, String childOrgId, String reason) {
        this.rowNumber = rowNumber;
        this.parentOrgId = parentOrgId;
        this.childOrgId = childOrgId;
        this.reason = reason;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public String getParentOrgId() {
        return parentOrgId;
    }

    public String getChildOrgId() {
        return childOrgId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return (rowNumber == null ? "" : "row " + rowNumber + ": ") + parentOrgId + " -> " + childOrgId + " (" + reason + ")";
    }
}
