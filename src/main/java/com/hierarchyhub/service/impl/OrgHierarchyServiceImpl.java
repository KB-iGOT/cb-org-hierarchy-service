package com.hierarchyhub.service.impl;

import com.hierarchyhub.dto.Constants;
import com.hierarchyhub.config.OrgHierarchyProperties;
import com.hierarchyhub.config.OrgHierarchyTransactionConfig;
import com.hierarchyhub.dto.AssociationRef;
import com.hierarchyhub.dto.BatchOperationResponse;
import com.hierarchyhub.dto.ChildOrgSummary;
import com.hierarchyhub.dto.HierarchyResponse;
import com.hierarchyhub.dto.LinkOrgRequest;
import com.hierarchyhub.dto.OrgOperationResult;
import com.hierarchyhub.dto.MoveOrgRequest;
import com.hierarchyhub.dto.MoveOrgResponse;
import com.hierarchyhub.dto.OrgTreeNode;
import com.hierarchyhub.dto.ReadChildrenRequest;
import com.hierarchyhub.dto.ReadChildrenResponse;
import com.hierarchyhub.dto.UnlinkOrgRequest;
import com.hierarchyhub.exception.ErrorCode;
import com.hierarchyhub.exception.ServiceException;
import com.hierarchyhub.model.ApiResponse;
import com.hierarchyhub.model.OrganisationRecord;
import com.hierarchyhub.model.OrgNode;
import com.hierarchyhub.repository.OrgHierarchyRepository;
import com.hierarchyhub.service.OrgHierarchyService;
import com.hierarchyhub.util.AccessTokenValidator;
import com.hierarchyhub.util.AppUtil;
import com.hierarchyhub.validation.OrgHierarchyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * All the business rules for link / move / unlink: auth, the ministry/state check, row locking, idempotency, cycle
 * prevention, and the child counters. Every write method is one PostgreSQL transaction - it locks the rows it touches
 * in sorted id order (so two writers on the same rows queue instead of deadlocking), re-checks the state it depends on
 * under that lock, and only then writes. Every method returns the finished {@link ApiResponse} envelope: building it
 * here, not in the controller, keeps the API id, version and result shape together with the logic that produces them.
 */
@Service
public class OrgHierarchyServiceImpl implements OrgHierarchyService {

    private static final Logger log = LoggerFactory.getLogger(OrgHierarchyServiceImpl.class);

    private final OrgHierarchyRepository repository;
    private final OrgHierarchyValidator validator;
    private final AccessTokenValidator accessTokenValidator;
    private final int maxDepth;

    public OrgHierarchyServiceImpl(OrgHierarchyRepository repository, OrgHierarchyValidator validator,
                                    AccessTokenValidator accessTokenValidator, OrgHierarchyProperties props) {
        this.repository = repository;
        this.validator = validator;
        this.accessTokenValidator = accessTokenValidator;
        this.maxDepth = props.getMaxDepth();
    }

    @Override
    @Transactional(OrgHierarchyTransactionConfig.ORG_HIERARCHY_TX_MANAGER)
    public ApiResponse linkOrgToHierarchy(LinkOrgRequest request, String accessToken) {
        String userId = fetchUserIdFromToken(accessToken);
        String parentId = request.getRequest().getParentOrgId();
        List<String> childIds = identifiersOf(request.getRequest().getAssociations());
        log.info("linkOrgToHierarchy request: parentId={}, childIds={}", parentId, childIds);

        TreeSet<String> rowsToLock = sorted(childIds);
        rowsToLock.add(parentId);
        repository.lockRows(rowsToLock);
        requireExisting(parentId, "Parent " + parentId + " is not in the hierarchy: map it first (parents are mapped top-down)");

        List<OrgOperationResult> results = new ArrayList<>();
        for (String childId : childIds) {
            results.add(attachChildUnderParent(parentId, childId, userId));
        }
        String message = summaryMessage("added", "to", Constants.MSG_ORG_ADDED_TO_HIERARCHY, results);
        logOutcome("linkOrgToHierarchy", parentId, results);
        return versionedResponse(Constants.API_LINK, new BatchOperationResponse(message));
    }

    /** Attaches one child under {@code parentId}; every rule violation is reported on its own result instead of failing the whole batch. */
    private OrgOperationResult attachChildUnderParent(String parentId, String childId, String userId) {
        try {
            validator.requireDistinct(childId, parentId);
            Map<String, OrganisationRecord> masters = validator.checkMinistryOrStateCompliance(childId, parentId);

            Optional<OrgNode> existingChild = repository.findById(childId);
            if (!existingChild.isPresent()) {
                String childName = masters.containsKey(childId) ? nameOrId(masters.get(childId)) : childId;
                Optional<OrgNode> created = repository.createMapped(childId, childName, parentId, userId);
                if (created.isPresent()) {
                    repository.incrementChildCount(parentId);
                    log.debug("attachChildUnderParent: created and attached childId={} under parentId={}", childId, parentId);
                    return OrgOperationResult.success(childId, Constants.MSG_ORG_ADDED_TO_HIERARCHY);
                }
                existingChild = repository.findById(childId); // another request created it first; fall through as an existing org
            }

            OrgNode child = existingChild.orElseThrow(() -> notFound(childId));
            if (parentId.equals(child.parentId())) {
                log.debug("attachChildUnderParent: childId={} already under parentId={}, no-op", childId, parentId);
                return OrgOperationResult.success(childId, Constants.MSG_ORG_ADDED_TO_HIERARCHY); // idempotent no-op
            }
            if (child.parentId() != null) {
                throw new ServiceException(ErrorCode.ALREADY_HAS_PARENT,
                        "Org " + childId + " is already under " + child.parentId() + "; use /move");
            }
            if (child.isL0()) {
                throw new ServiceException(ErrorCode.IS_AN_L0,
                        "Org " + childId + " is a ministry or state: it cannot be attached under another org");
            }
            requireNoCycle(childId, parentId);

            repository.setParent(childId, parentId, userId);
            repository.incrementChildCount(parentId);
            log.debug("attachChildUnderParent: attached existing childId={} under parentId={}", childId, parentId);
            return OrgOperationResult.success(childId, Constants.MSG_ORG_ADDED_TO_HIERARCHY);
        } catch (ServiceException e) {
            log.warn("attachChildUnderParent failed: childId={}, parentId={}, reason={}", childId, parentId, e.getMessage());
            return OrgOperationResult.failure(childId, e.getMessage());
        }
    }

    @Override
    @Transactional(OrgHierarchyTransactionConfig.ORG_HIERARCHY_TX_MANAGER)
    public OrgOperationResult attachChild(String parentId, String childId, String userId) {
        repository.lockRows(sorted(childId, parentId));
        if (!repository.findById(parentId).isPresent()) {
            return OrgOperationResult.failure(childId,
                    "Parent " + parentId + " is not in the hierarchy: map it first (parents are mapped top-down)");
        }
        return attachChildUnderParent(parentId, childId, userId);
    }

    @Override
    @Transactional(OrgHierarchyTransactionConfig.ORG_HIERARCHY_TX_MANAGER)
    public ApiResponse moveOrgInHierarchy(MoveOrgRequest request, String accessToken) {
        String userId = fetchUserIdFromToken(accessToken);
        String orgId = request.getRequest().getOrgId();
        String newParentId = request.getRequest().getNewParentOrgId();
        log.info("moveOrgInHierarchy request: orgId={}, newParentId={}", orgId, newParentId);

        validator.requireDistinct(orgId, newParentId);
        validator.checkMinistryOrStateCompliance(orgId, newParentId);

        OrgNode before = repository.findById(orgId).orElseThrow(() -> notFound(orgId));
        if (before.isL0()) {
            throw new ServiceException(ErrorCode.IS_AN_L0, "Org " + orgId + " is a ministry or state: it cannot be moved");
        }
        if (before.parentId() == null) {
            throw new ServiceException(ErrorCode.NOT_LINKED, "Org " + orgId + " is unlinked: use /link, not /move");
        }

        repository.lockRows(sorted(orgId, before.parentId(), newParentId));
        OrgNode org = requireUnchangedParent(orgId, before.parentId());
        requireExisting(newParentId, "Parent " + newParentId + " is not in the hierarchy");

        if (newParentId.equals(org.parentId())) {
            log.info("moveOrgInHierarchy done: orgId={} already under newParentId={}, no-op", orgId, newParentId);
            MoveOrgResponse noOp = new MoveOrgResponse(orgId, org.parentId(), newParentId, false,
                    "Organisation is already linked under this parent"); // idempotent no-op, genuinely distinct from a move
            return versionedResponse(Constants.API_MOVE, noOp);
        }
        requireNoCycle(orgId, newParentId);

        String oldParentId = org.parentId();
        repository.setParent(orgId, newParentId, userId);
        repository.decrementChildCount(oldParentId);
        repository.incrementChildCount(newParentId);
        log.info("moveOrgInHierarchy done: orgId={}, oldParentId={}, newParentId={}", orgId, oldParentId, newParentId);
        MoveOrgResponse moved = new MoveOrgResponse(orgId, oldParentId, newParentId, true, Constants.MSG_ORG_MOVED_IN_HIERARCHY);
        return versionedResponse(Constants.API_MOVE, moved);
    }

    @Override
    @Transactional(OrgHierarchyTransactionConfig.ORG_HIERARCHY_TX_MANAGER)
    public ApiResponse unlinkOrgFromHierarchy(UnlinkOrgRequest request, String accessToken) {
        String userId = fetchUserIdFromToken(accessToken);
        String orgId = request.getRequest().getOrgId();
        boolean requireLeaf = request.getRequest().requireLeafOrDefault();
        log.info("unlinkOrgFromHierarchy request: orgId={}, requireLeaf={}", orgId, requireLeaf);

        OrgNode before = repository.findById(orgId).orElseThrow(() -> notFound(orgId));
        if (before.parentId() == null) {
            throw new ServiceException(ErrorCode.NOT_LINKED, "Org " + orgId + " has no parent");
        }

        if (requireLeaf) {
            repository.lockRows(sorted(orgId, before.parentId()));
            OrgNode org = requireUnchangedParent(orgId, before.parentId());
            if (repository.hasChildren(orgId)) {
                throw new ServiceException(ErrorCode.NOT_A_LEAF,
                        "Org " + orgId + " has children; pass requireLeaf=false to unlink it with its subtree");
            }
            repository.unlinkRow(orgId, userId);
            repository.decrementChildCount(org.parentId());
            log.info("unlinkOrgFromHierarchy done: orgId={}, parentId={}", orgId, org.parentId());
        } else {
            // the default: orgId and every descendant are unlinked together - the whole branch, not just orgId itself
            List<OrgNode> subtree = repository.findSubtree(orgId); // unlocked read; re-verified below once locked
            TreeSet<String> subtreeIds = new TreeSet<>();
            for (OrgNode node : subtree) {
                subtreeIds.add(node.id());
            }
            TreeSet<String> rowsToLock = sorted(subtreeIds);
            rowsToLock.add(before.parentId());
            repository.lockRows(rowsToLock);
            OrgNode org = requireUnchangedParent(orgId, before.parentId());
            repository.clearSubtree(subtreeIds, userId); // orgId's own original parent is NOT in subtreeIds, so it's untouched here
            repository.decrementChildCount(org.parentId());
            log.info("unlinkOrgFromHierarchy done: orgId={}, parentId={}, subtreeSize={}", orgId, org.parentId(), subtree.size());
        }
        return versionedResponse(Constants.API_UNLINK, new BatchOperationResponse(Constants.MSG_ORG_REMOVED_FROM_HIERARCHY));
    }

    @Override
    @Transactional(value = OrgHierarchyTransactionConfig.ORG_HIERARCHY_TX_MANAGER, readOnly = true)
    public ApiResponse readOrgChildren(String orgId, ReadChildrenRequest request, String accessToken) {
        fetchUserIdFromToken(accessToken);
        OrgNode root = repository.findById(orgId)
                .orElseThrow(() -> new ServiceException(ErrorCode.ORG_NOT_FOUND, "Org " + orgId + " is not in the hierarchy"));
        ReadChildrenRequest.Body body = request == null || request.getRequest() == null
                ? new ReadChildrenRequest.Body() : request.getRequest();
        int limit = body.limitOrDefault();
        boolean withDescendantCounts = body.countDescendantsOrDefault();
        String query = AppUtil.isBlank(body.getQuery()) ? null : body.getQuery().trim();
        String queryPattern = query == null ? null : AppUtil.likeContainsPattern(query);
        // explicit depth always wins; otherwise a search reaches the whole subtree (it has to, to find a match
        // anywhere under orgId), but a plain, filter-less read stays the original "direct children only" default -
        // so a caller who just passes orgId never gets the whole hierarchy back without asking for it.
        int depthLimit = body.getDepth() != null ? Math.min(body.getDepth(), maxDepth) : query != null ? maxDepth : 1;

        List<OrgNode> page;
        if (body.getOffset() != null) {
            // offset wins over cursor when both are somehow given - see ReadChildrenRequest.Body#offset
            page = repository.findDescendantsByOffset(orgId, depthLimit, query, queryPattern, body.getOffset(), limit + 1);
        } else {
            String[] cursor = decodeCursor(body.getCursor());
            page = repository.findDescendants(orgId, depthLimit, query, queryPattern,
                    cursor == null ? null : cursor[0], cursor == null ? null : cursor[1], limit + 1);
        }
        boolean hasMore = page.size() > limit;
        List<OrgNode> items = hasMore ? page.subList(0, limit) : page;

        List<ChildOrgSummary> summaries = new ArrayList<>();
        for (OrgNode node : items) {
            Integer descendantCount = withDescendantCounts ? repository.countDescendants(node.id()) : null;
            summaries.add(new ChildOrgSummary(node.id(), node.name(), node.idle(), node.childCount(), descendantCount));
        }
        // a cursor only makes sense when paging by cursor; an offset-paging caller computes its own next offset
        String nextCursor = hasMore && body.getOffset() == null ? AppUtil.encodeCursor(lastOf(items).name(), lastOf(items).id()) : null;
        ReadChildrenResponse result = new ReadChildrenResponse(root.id(), root.name(), root.idle(), root.childCount(),
                summaries, limit, hasMore, nextCursor);
        return versionedResponse(Constants.API_READ, result);
    }

    @Override
    @Transactional(value = OrgHierarchyTransactionConfig.ORG_HIERARCHY_TX_MANAGER, readOnly = true)
    public ApiResponse readOrgHierarchy(String orgId, Integer depth, String accessToken) {
        fetchUserIdFromToken(accessToken);
        requireExisting(orgId, "Org " + orgId + " is not in the hierarchy");

        List<OrgNode> rows = repository.findSubtree(orgId); // already bounded by org-hierarchy.max-depth
        Map<String, OrgNode> byId = new LinkedHashMap<>();
        Map<String, List<OrgNode>> childrenByParent = new LinkedHashMap<>();
        for (OrgNode node : rows) {
            byId.put(node.id(), node);
            if (node.parentId() != null) {
                childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node);
            }
        }
        OrgTreeNode tree = buildTree(byId.get(orgId), childrenByParent, depth, 1);
        HierarchyResponse result = new HierarchyResponse(tree, countNodes(tree));
        return versionedResponse(Constants.API_HIERARCHY, result);
    }

    private static OrgTreeNode buildTree(OrgNode node, Map<String, List<OrgNode>> childrenByParent, Integer depthLimit, int currentDepth) {
        List<OrgTreeNode> associations = new ArrayList<>();
        if (depthLimit == null || currentDepth < depthLimit) {
            for (OrgNode child : childrenByParent.getOrDefault(node.id(), Collections.emptyList())) {
                associations.add(buildTree(child, childrenByParent, depthLimit, currentDepth + 1));
            }
        }
        return new OrgTreeNode(node.id(), node.name(), node.idle(), node.childCount(), associations);
    }

    private static int countNodes(OrgTreeNode node) {
        int count = 1;
        for (OrgTreeNode child : node.getAssociations()) {
            count += countNodes(child);
        }
        return count;
    }

    private static String[] decodeCursor(String cursor) {
        if (AppUtil.isBlank(cursor)) {
            return null;
        }
        try {
            return AppUtil.decodeCursor(cursor);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(ErrorCode.INVALID_REQUEST, "Malformed cursor: " + cursor);
        }
    }

    // ================================================================ shared steps

    /** Verifies {@code accessToken} and returns the caller's user id; every public method needs a valid token to proceed. */
    private String fetchUserIdFromToken(String accessToken) {
        String userId = accessTokenValidator.resolveUserId(accessToken);
        if (userId == null) {
            log.warn("Rejected request: missing, invalid or expired access token");
            throw new ServiceException(ErrorCode.UNAUTHORIZED, "Invalid or expired access token");
        }
        return userId;
    }

    private void requireExisting(String id, String message) {
        if (!repository.findById(id).isPresent()) {
            throw new ServiceException(ErrorCode.ORG_NOT_FOUND, message);
        }
    }

    /** Re-reads {@code id} under the lock and refuses with CONCURRENT_MODIFICATION if its parent moved since the pre-check. */
    private OrgNode requireUnchangedParent(String id, String parentIdBeforeLock) {
        OrgNode node = repository.findById(id).orElseThrow(() -> notFound(id));
        if (!parentIdBeforeLock.equals(node.parentId())) {
            throw new ServiceException(ErrorCode.CONCURRENT_MODIFICATION,
                    "The parent of " + id + " changed while this request was waiting; retry");
        }
        return node;
    }

    private void requireNoCycle(String orgId, String newParentId) {
        if (repository.chainUp(newParentId).contains(orgId)) {
            throw new ServiceException(ErrorCode.CYCLE_DETECTED,
                    "Org " + orgId + " cannot be placed under " + newParentId + ": that is one of its own descendants");
        }
    }

    private static String nameOrId(OrganisationRecord record) {
        return AppUtil.defaultIfBlank(record.name(), record.id());
    }

    private static List<String> identifiersOf(List<AssociationRef> associations) {
        List<String> ids = new ArrayList<>();
        for (AssociationRef ref : associations) {
            ids.add(ref.getChildOrgId());
        }
        return ids;
    }

    /**
     * One outcome message for a whole {@code /link} batch (the only remaining batch write - {@code /unlink} became a
     * single-org operation). When every child succeeded, this is just {@code allSucceededMessage} (the plain,
     * orgId-free wording) - the orgIds themselves go to the log, not the response. A batch with any failures still
     * names the failed orgIds and their reasons, since the caller needs that to act on it.
     */
    private static String summaryMessage(String verb, String preposition, String allSucceededMessage, List<OrgOperationResult> results) {
        List<OrgOperationResult> failures = failuresOf(results);
        if (failures.isEmpty()) {
            return allSucceededMessage;
        }

        StringBuilder message = new StringBuilder(allSucceededMessage).append("; ")
                .append("Failed to ").append(verb).append(" organisation").append(failures.size() > 1 ? "s " : " ")
                .append(preposition).append(" the hierarchy for orgId").append(failures.size() > 1 ? "s: " : ": ");
        List<String> failureParts = new ArrayList<>();
        for (OrgOperationResult failure : failures) {
            failureParts.add(failure.getIdentifier() + " (" + failure.getMessage() + ")");
        }
        return message.append(String.join(", ", failureParts)).toString();
    }

    /** Logs the batch outcome at info level with every orgId involved, since the response message itself no longer carries them. */
    private static void logOutcome(String operation, String parentId, List<OrgOperationResult> results) {
        List<String> succeededIds = new ArrayList<>();
        for (OrgOperationResult result : results) {
            if (result.isSuccess()) {
                succeededIds.add(result.getIdentifier());
            }
        }
        List<OrgOperationResult> failures = failuresOf(results);
        if (failures.isEmpty()) {
            log.info("{} done: parentId={}, succeededChildIds={}", operation, parentId, succeededIds);
        } else {
            List<String> failureDetails = new ArrayList<>();
            for (OrgOperationResult failure : failures) {
                failureDetails.add(failure.getIdentifier() + " (" + failure.getMessage() + ")");
            }
            log.warn("{} done: parentId={}, succeededChildIds={}, failedChildren={}", operation, parentId, succeededIds, failureDetails);
        }
    }

    private static List<OrgOperationResult> failuresOf(List<OrgOperationResult> results) {
        List<OrgOperationResult> failures = new ArrayList<>();
        for (OrgOperationResult result : results) {
            if (!result.isSuccess()) {
                failures.add(result);
            }
        }
        return failures;
    }

    private static TreeSet<String> sorted(String... ids) {
        TreeSet<String> set = new TreeSet<>();
        for (String id : ids) {
            set.add(id);
        }
        return set;
    }

    private static TreeSet<String> sorted(Collection<String> ids) {
        return new TreeSet<>(ids);
    }

    private static OrgNode lastOf(List<OrgNode> items) {
        return items.get(items.size() - 1);
    }

    private static ServiceException notFound(String id) {
        return new ServiceException(ErrorCode.ORG_NOT_FOUND, "Org not found in hierarchy: " + id);
    }

    private static ApiResponse versionedResponse(String apiId, Object result) {
        return AppUtil.versionedResponse(apiId, result);
    }
}
