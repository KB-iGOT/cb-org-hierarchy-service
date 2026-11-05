package com.hierarchyhub.model;

import java.time.Instant;

/**
 * One row of the {@code org_hierarchy} table: an organisation that has been mapped into the tree (or declared as an L0).
 * Immutable snapshot returned by the repository after a read or a write.
 */
public final class OrgNode {

    private final String id;
    private final String name;
    private final String parentId;
    private final boolean idle;
    private final int childCount;
    private final Instant createdAt;
    private final String createdBy;
    private final Instant updatedAt;
    private final String updatedBy;

    public OrgNode(String id, String name, String parentId, boolean idle, int childCount,
                   Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.idle = idle;
        this.childCount = childCount;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String parentId() {
        return parentId;
    }

    public boolean idle() {
        return idle;
    }

    public int childCount() {
        return childCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String updatedBy() {
        return updatedBy;
    }

    /** An L0 (a ministry or a state): no parent and not idle. */
    public boolean isL0() {
        return parentId == null && !idle;
    }
}
