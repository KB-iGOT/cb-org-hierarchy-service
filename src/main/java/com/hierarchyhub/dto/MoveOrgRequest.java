package com.hierarchyhub.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * {@code POST /move} body: moves {@code request.orgId} (and its whole branch) under
 * {@code request.newParentOrgId}, which must be of the same ministry or state.
 */
public class MoveOrgRequest {

    @NotNull(message = "request is required")
    @Valid
    private Body request;

    public MoveOrgRequest() {
    }

    public MoveOrgRequest(Body request) {
        this.request = request;
    }

    public Body getRequest() {
        return request;
    }

    public void setRequest(Body request) {
        this.request = request;
    }

    public static class Body {

        @NotBlank(message = "orgId is required")
        private String orgId;

        @NotBlank(message = "newParentOrgId is required")
        private String newParentOrgId;

        public Body() {
        }

        public Body(String orgId, String newParentOrgId) {
            this.orgId = orgId;
            this.newParentOrgId = newParentOrgId;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }

        public String getNewParentOrgId() {
            return newParentOrgId;
        }

        public void setNewParentOrgId(String newParentOrgId) {
            this.newParentOrgId = newParentOrgId;
        }
    }
}
