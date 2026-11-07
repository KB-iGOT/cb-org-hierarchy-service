package com.hierarchyhub.service;

import com.hierarchyhub.dto.BatchLinkExecutionResult;
import com.hierarchyhub.dto.LinkOrgRequest;
import com.hierarchyhub.dto.MoveOrgRequest;
import com.hierarchyhub.dto.ReadChildrenRequest;
import com.hierarchyhub.dto.UnlinkOrgRequest;
import com.hierarchyhub.model.ApiResponse;

import java.util.List;

public interface OrgHierarchyService {

    ApiResponse linkOrgToHierarchy(LinkOrgRequest request, String accessToken);

    BatchLinkExecutionResult linkChildrenUnderParentBatch(String parentOrgId, List<String> childOrganisationIds, String userId);

    /** Moves {@code request.orgId} (and its whole branch) to {@code request.newParentId}, which must share its ministry/state. */
    ApiResponse moveOrgInHierarchy(MoveOrgRequest request, String accessToken);

    ApiResponse unlinkOrgFromHierarchy(UnlinkOrgRequest request, String accessToken);

    ApiResponse readOrgChildren(String orgId, ReadChildrenRequest request, String accessToken);

    ApiResponse readOrgHierarchy(String orgId, Integer depth, String accessToken);
}
