package com.hierarchyhub.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * {@code POST /unlink} body: {@code {"request": {"orgId": "...", "requireLeaf": false}}}. Detaches {@code orgId} from
 * its current parent (whichever that is - the caller does not name it). {@code requireLeaf} defaults to {@code
 * false}: the org is removed from the hierarchy along with its whole subtree - every descendant is unlinked too
 * (each becomes idle, with no parent, not just the org itself). Pass {@code true} to refuse instead whenever the org
 * still has children of its own (it must already be a leaf).
 */
public class UnlinkOrgRequest {

    @NotNull(message = "request is required")
    @Valid
    private Body request;

    public UnlinkOrgRequest() {
    }

    public UnlinkOrgRequest(Body request) {
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

        private Boolean requireLeaf;

        public Body() {
        }

        public Body(String orgId, Boolean requireLeaf) {
            this.orgId = orgId;
            this.requireLeaf = requireLeaf;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }

        public Boolean getRequireLeaf() {
            return requireLeaf;
        }

        public void setRequireLeaf(Boolean requireLeaf) {
            this.requireLeaf = requireLeaf;
        }

        public boolean requireLeafOrDefault() {
            return requireLeaf != null && requireLeaf;
        }
    }
}
