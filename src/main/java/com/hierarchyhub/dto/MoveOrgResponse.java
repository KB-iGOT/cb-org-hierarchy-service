package com.hierarchyhub.dto;

/** Result of {@code POST /move}: a short, human-readable {@link #message} describing what happened. */
public class MoveOrgResponse {

    private final String orgId;
    private final String oldParentId;
    private final String newParentId;
    private final boolean changed;
    private final String message;

    public MoveOrgResponse(String orgId, String oldParentId, String newParentId, boolean changed, String message) {
        this.orgId = orgId;
        this.oldParentId = oldParentId;
        this.newParentId = newParentId;
        this.changed = changed;
        this.message = message;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getOldParentId() {
        return oldParentId;
    }

    public String getNewParentId() {
        return newParentId;
    }

    public boolean isChanged() {
        return changed;
    }

    public String getMessage() {
        return message;
    }
}
