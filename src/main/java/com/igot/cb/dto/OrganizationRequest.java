package com.igot.cb.dto;

import java.util.Map;

public class OrganizationRequest {

    private String orgName;
    private String channel;
    private boolean isTenant;
    private String organisationType;
    private String organisationSubType;
    private String requestedBy;
    private Map<String, Object> additionalProperties;
    private String parentId;
    private boolean isParent;
    private String externalSourceId;

    public OrganizationRequest() {
    }

    public OrganizationRequest(String orgName, String channel, boolean isTenant, String organisationType, String organisationSubType, String requestedBy, Map<String, Object> properties, String parentId, Boolean isParent, String externalSourceId) {
        this.orgName = orgName;
        this.channel = channel;
        this.isTenant = isTenant;
        this.organisationType = organisationType;
        this.organisationSubType = organisationSubType;
        this.requestedBy = requestedBy;
        this.additionalProperties = properties;
        this.parentId = parentId;
        this.isParent = isParent;
        this.externalSourceId = externalSourceId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public boolean isTenant() {
        return isTenant;
    }

    public void setTenant(boolean tenant) {
        isTenant = tenant;
    }

    public String getOrganisationType() {
        return organisationType;
    }

    public void setOrganisationType(String organisationType) {
        this.organisationType = organisationType;
    }

    public String getOrganisationSubType() {
        return organisationSubType;
    }

    public void setOrganisationSubType(String organisationSubType) {
        this.organisationSubType = organisationSubType;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public boolean isParent() {
        return isParent;
    }

    public void setParent(boolean parent) {
        isParent = parent;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public String getExternalSourceId() {
        return externalSourceId;
    }

    public void setExternalSourceId(String externalSourceId) {
        this.externalSourceId = externalSourceId;
    }
}
