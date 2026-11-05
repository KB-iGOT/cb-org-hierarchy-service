package com.hierarchyhub.service;

import com.hierarchyhub.dto.LinkOrgRequest;
import com.hierarchyhub.dto.MoveOrgRequest;
import com.hierarchyhub.dto.OrgOperationResult;
import com.hierarchyhub.dto.ReadChildrenRequest;
import com.hierarchyhub.dto.UnlinkOrgRequest;
import com.hierarchyhub.model.ApiResponse;

/**
 * The structural writes on the org hierarchy (link, move, unlink) and the two reads that show the result of those
 * writes (a parent's direct children, and its whole subtree). The controller only forwards the request, the path
 * {@code orgId} and the caller's raw access token to these methods; every rule (auth, ministry/state compliance,
 * cycle prevention, idempotency, ...), the response envelope and the PostgreSQL transaction live in
 * {@link com.hierarchyhub.service.impl.OrgHierarchyServiceImpl}.
 */
public interface OrgHierarchyService {

    /**
     * Attaches every child in {@code request.request.associations} under {@code request.request.orgId} in one call; each
     * child is created from the organisation master if it is new. A failure on one child is reported on its own result
     * and does not stop the rest of the batch from being attempted.
     */
    ApiResponse linkOrgToHierarchy(LinkOrgRequest request, String accessToken);

    /**
     * Attaches one {@code childId} under {@code parentId}, applying the exact same rules as one child inside
     * {@link #linkOrgToHierarchy} (ministry/state compliance, idempotency, cycle prevention) in its own transaction.
     * Used by the {@code /bulk/link} file-upload job, which runs each row of the sheet through this one by one
     * instead of locking the whole file at once - rows may name different parents, unlike a single {@code /link} call.
     */
    OrgOperationResult attachChild(String parentId, String childId, String userId);

    /** Moves {@code request.orgId} (and its whole branch) to {@code request.newParentId}, which must share its ministry/state. */
    ApiResponse moveOrgInHierarchy(MoveOrgRequest request, String accessToken);

    /**
     * Detaches {@code request.request.orgId} from its current parent. {@code requireLeaf} defaults to {@code false}:
     * the whole branch is unlinked together - every descendant becomes idle too, not just {@code orgId} itself. Pass
     * {@code true} to refuse instead (NOT_A_LEAF) whenever the org still has children of its own.
     */
    ApiResponse unlinkOrgFromHierarchy(UnlinkOrgRequest request, String accessToken);

    /**
     * One page of {@code orgId}'s descendants (direct children by default; deeper with {@code depth}/{@code query} -
     * see {@link ReadChildrenRequest.Body}), ordered by name, paged by a cursor or by {@code limit}/{@code offset};
     * {@code request} may be {@code null} (defaults apply).
     */
    ApiResponse readOrgChildren(String orgId, ReadChildrenRequest request, String accessToken);

    /** {@code orgId}'s whole subtree as nested JSON, optionally cut off at {@code depth}; {@code depth} may be {@code null} (the whole subtree). */
    ApiResponse readOrgHierarchy(String orgId, Integer depth, String accessToken);
}
