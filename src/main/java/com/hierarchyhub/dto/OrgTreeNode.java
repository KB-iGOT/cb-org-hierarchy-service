package com.hierarchyhub.dto;

import java.util.List;

/** One node of a {@code POST /hierarchy/{orgId}} response: an org with its direct children nested under {@link #associations}. */
public class OrgTreeNode {

    private final String id;
    private final String name;
    private final boolean idle;
    private final int childCount;
    private final List<OrgTreeNode> associations;

    public OrgTreeNode(String id, String name, boolean idle, int childCount, List<OrgTreeNode> associations) {
        this.id = id;
        this.name = name;
        this.idle = idle;
        this.childCount = childCount;
        this.associations = associations;
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

    public List<OrgTreeNode> getAssociations() {
        return associations;
    }
}
