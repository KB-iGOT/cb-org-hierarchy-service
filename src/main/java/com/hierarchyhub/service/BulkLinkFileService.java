package com.hierarchyhub.service;

import com.hierarchyhub.config.BulkLinkExecutorConfig;
import com.hierarchyhub.config.OrgHierarchyProperties;
import com.hierarchyhub.dto.BulkLinkJob;
import com.hierarchyhub.dto.BulkLinkRow;
import com.hierarchyhub.dto.BulkLinkRowFailure;
import com.hierarchyhub.dto.BulkLinkUploadResponse;
import com.hierarchyhub.dto.Constants;
import com.hierarchyhub.dto.OrgOperationResult;
import com.hierarchyhub.exception.ErrorCode;
import com.hierarchyhub.exception.ServiceException;
import com.hierarchyhub.model.ApiResponse;
import com.hierarchyhub.util.AccessTokenValidator;
import com.hierarchyhub.util.AppUtil;
import com.hierarchyhub.util.BulkLinkSheetReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@code POST /bulk/link}: an Excel upload that attaches many children under their parents in one file. The request
 * itself only reads the file into memory and parses it (so a bad sheet - wrong sheet name, missing columns - is
 * rejected with 400 immediately, before anything is queued) and then responds right away with {@code status:
 * INITIATED}; every row is then attached one at a time, in the background, on {@link
 * BulkLinkExecutorConfig#BULK_LINK_EXECUTOR}, by {@link OrgHierarchyService#attachChild} - the exact same rule set
 * {@code /link} applies to one child, so a row fails for the same reasons a live {@code /link} call would. There is
 * no job store and no status endpoint: the outcome (rows succeeded/failed, and why) is only logged at the end of the
 * run, the same way {@code /link}/{@code /move}/{@code /unlink} already log theirs.
 */
@Service
public class BulkLinkFileService {

    private static final Logger log = LoggerFactory.getLogger(BulkLinkFileService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX").withZone(ZoneOffset.UTC);

    private final OrgHierarchyService service;
    private final AccessTokenValidator accessTokenValidator;
    private final Executor executor;
    private final OrgHierarchyProperties props;

    public BulkLinkFileService(OrgHierarchyService service, AccessTokenValidator accessTokenValidator,
                                @Qualifier(BulkLinkExecutorConfig.BULK_LINK_EXECUTOR) Executor executor,
                                OrgHierarchyProperties props) {
        this.service = service;
        this.accessTokenValidator = accessTokenValidator;
        this.executor = executor;
        this.props = props;
    }

    public ApiResponse initiate(MultipartFile file, String rootOrgId, String accessToken) {
        String userId = fetchUserIdFromToken(accessToken);
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "Upload the Excel file in the form field 'file'");
        }
        if (AppUtil.isBlank(rootOrgId)) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "rootOrgId is required");
        }

        BulkLinkSheetReader.ParsedSheet parsed = BulkLinkSheetReader.read(bytesOf(file));
        int totalRows = parsed.rows().size() + parsed.rowFailures().size();
        if (totalRows > props.getBulk().getMaxRows()) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST,
                    "At most " + props.getBulk().getMaxRows() + " rows per file; split it into smaller files");
        }

        BulkLinkJob job = new BulkLinkJob(UUID.randomUUID().toString(), // log correlation only - never returned or polled
                file.getOriginalFilename(), userId, TIMESTAMP.format(Instant.now()), rootOrgId,
                totalRows, parsed.duplicateChildRowsSkipped());
        parsed.rowFailures().forEach(job::recordFailure);
        log.info("bulkLink initiated: jobId={}, fileName={}, rootOrgId={}, totalRows={}, parseRowFailures={}, duplicateChildRowsSkipped={}",
                job.getJobId(), job.getFileName(), rootOrgId, totalRows, parsed.rowFailures().size(), parsed.duplicateChildRowsSkipped());

        try {
            executor.execute(() -> runJob(job, parsed.rows(), userId));
        } catch (RejectedExecutionException e) {
            log.error("bulkLink queue full: jobId={}, fileName={}", job.getJobId(), job.getFileName());
            throw new ServiceException(ErrorCode.DEPENDENCY_FAILURE, "Bulk-link queue is full; retry later");
        }

        BulkLinkUploadResponse result = new BulkLinkUploadResponse(job.getFileName(), job.getCreatedBy(),
                job.getDateCreatedOn(), job.getRootOrgId(), Constants.BULK_LINK_STATUS_INITIATED);
        return AppUtil.versionedResponse(Constants.API_BULK_LINK, result);
    }

    private void runJob(BulkLinkJob job, List<BulkLinkRow> rows, String userId) {
        try {
            for (BulkLinkRow row : rows) {
                OrgOperationResult result = service.attachChild(row.getParentOrgId(), row.getChildOrgId(), userId);
                if (result.isSuccess()) {
                    job.recordSuccess();
                } else {
                    job.recordFailure(new BulkLinkRowFailure(null, row.getParentOrgId(), row.getChildOrgId(), result.getMessage()));
                }
            }
            if (job.getFailedRows() == 0) {
                log.info("bulkLink done: jobId={}, fileName={}, totalRows={}, succeededRows={}",
                        job.getJobId(), job.getFileName(), job.getTotalRows(), job.getSucceededRows());
            } else {
                log.warn("bulkLink done: jobId={}, fileName={}, totalRows={}, succeededRows={}, failedRows={}, failures={}",
                        job.getJobId(), job.getFileName(), job.getTotalRows(), job.getSucceededRows(), job.getFailedRows(), job.getFailures());
            }
        } catch (Exception e) {
            log.error("bulkLink job {} ({}) failed", job.getJobId(), job.getFileName(), e);
        }
    }

    private static ByteArrayInputStream bytesOf(MultipartFile file) {
        try {
            return new ByteArrayInputStream(file.getBytes());
        } catch (IOException e) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "Could not read the uploaded file: " + e.getMessage());
        }
    }

    private String fetchUserIdFromToken(String accessToken) {
        String userId = accessTokenValidator.resolveUserId(accessToken);
        if (userId == null) {
            log.warn("Rejected bulk-link request: missing, invalid or expired access token");
            throw new ServiceException(ErrorCode.UNAUTHORIZED, "Invalid or expired access token");
        }
        return userId;
    }
}
