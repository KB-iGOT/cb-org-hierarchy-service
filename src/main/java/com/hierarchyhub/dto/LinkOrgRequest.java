package com.hierarchyhub.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code POST /link} body: {@code request.parentOrgId} is the parent, and {@code request.associations[].childOrgId}
 * lists one or more children to attach under it in one call. A child is created from its Cassandra record when it is
 * not in the hierarchy yet.
 */
public class LinkOrgRequest {

    @NotNull(message = "request is required")
    @Valid
    private Body request;

    public LinkOrgRequest() {
    }

    public LinkOrgRequest(Body request) {
        this.request = request;
    }

    public Body getRequest() {
        return request;
    }

    public void setRequest(Body request) {
        this.request = request;
    }

    public static class Body {

        @NotBlank(message = "parentOrgId is required")
        private String parentOrgId;

        @NotEmpty(message = "associations must list at least one child")
        @Valid
        private List<AssociationRef> associations;

        public Body() {
        }

        public Body(String parentOrgId, List<AssociationRef> associations) {
            this.parentOrgId = parentOrgId;
            this.associations = associations;
        }

        public String getParentOrgId() {
            return parentOrgId;
        }

        public void setParentOrgId(String parentOrgId) {
            this.parentOrgId = parentOrgId;
        }

        public List<AssociationRef> getAssociations() {
            return associations;
        }

        public void setAssociations(List<AssociationRef> associations) {
            this.associations = associations;
        }
    }
}
