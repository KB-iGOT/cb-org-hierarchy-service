package com.hierarchyhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One direct child in a {@code POST /read/{orgId}} page. {@link #descendantCount} is null when it wasn't requested. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChildOrgSummary {

    private final String id;
    private final String name;
    private final boolean idle;
    private final int childCount;
    private final Integer descendantCount;

    public ChildOrgSummary(String id, String name, boolean idle, int childCount, Integer descendantCount) {
        this.id = id;
        this.name = name;
        this.idle = idle;
        this.childCount = childCount;
        this.descendantCount = descendantCount;
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

    public Integer getDescendantCount() {
        return descendantCount;
    }
}
