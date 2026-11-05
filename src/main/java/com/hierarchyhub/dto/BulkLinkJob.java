package com.hierarchyhub.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks one {@code /bulk/link} file upload's progress while {@link com.hierarchyhub.service.BulkLinkFileService}
 * runs it in the background. Purely in-process bookkeeping for the completion log line - nothing here is persisted
 * or exposed through the API, which responds once, at submission, with just {@code BulkLinkUploadResponse}.
 */
public final class BulkLinkJob {

    /** At most this many row failures are kept (for the completion log); {@link #failedRows} still counts every one of them. */
    public static final int MAX_FAILURES_KEPT = 200;

    private final String jobId;
    private final String fileName;
    private final String createdBy;
    private final String dateCreatedOn;
    private final String rootOrgId;
    private final int totalRows;
    private final int duplicateChildRowsSkipped;

    private int succeededRows;
    private int failedRows;
    private final List<BulkLinkRowFailure> failures = new ArrayList<>();

    public BulkLinkJob(String jobId, String fileName, String createdBy, String dateCreatedOn, String rootOrgId,
                        int totalRows, int duplicateChildRowsSkipped) {
        this.jobId = jobId;
        this.fileName = fileName;
        this.createdBy = createdBy;
        this.dateCreatedOn = dateCreatedOn;
        this.rootOrgId = rootOrgId;
        this.totalRows = totalRows;
        this.duplicateChildRowsSkipped = duplicateChildRowsSkipped;
    }

    public void recordSuccess() {
        succeededRows++;
    }

    /** Records one more failed row, keeping only the first {@link #MAX_FAILURES_KEPT} in detail. */
    public void recordFailure(BulkLinkRowFailure failure) {
        failedRows++;
        if (failures.size() < MAX_FAILURES_KEPT) {
            failures.add(failure);
        }
    }

    public String getJobId() {
        return jobId;
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

    public int getTotalRows() {
        return totalRows;
    }

    public int getDuplicateChildRowsSkipped() {
        return duplicateChildRowsSkipped;
    }

    public int getSucceededRows() {
        return succeededRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public List<BulkLinkRowFailure> getFailures() {
        return failures;
    }
}
