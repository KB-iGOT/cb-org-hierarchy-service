package com.hierarchyhub.dto;

/** {@code POST /bulk/link}'s response: the upload is accepted and queued, {@code status} is always {@code INITIATED}. */
public class BulkLinkUploadResponse {

    private final String fileName;
    private final String createdBy;
    private final String dateCreatedOn;
    private final String rootOrgId;
    private final String status;

    public BulkLinkUploadResponse(String fileName, String createdBy, String dateCreatedOn, String rootOrgId, String status) {
        this.fileName = fileName;
        this.createdBy = createdBy;
        this.dateCreatedOn = dateCreatedOn;
        this.rootOrgId = rootOrgId;
        this.status = status;
    }

    public String getFileName() {
        return fileName;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getDateCreatedOn() {
        return dateCreatedOn;
    }

    public String getRootOrgId() {
        return rootOrgId;
    }

    public String getStatus() {
        return status;
    }
}
