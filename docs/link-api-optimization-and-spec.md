# Technical Specification & Optimization Guide: `POST /org/v2/hierarchy/link`

**Service:** `cb-org-hierarchy-service`  
**Endpoint:** `POST /org/v2/hierarchy/link`  
**Target Release / PR:** Bulk Hierarchy Linking & Clean Architecture Refactoring  
**Document Status:** Updated Design Specification (Ready for Review)  

---

## 1. Executive Summary & Review Objectives

The `/link` API connects one or more child organisations under a designated parent organisation within the hierarchical tree structure stored in PostgreSQL (`org_hierarchy`), validated against the Sunbird authoritative organisation registry in Apache Cassandra (`sunbird.organisation`).

### Key Architecture & Clean Code Principles:
1. **Logic Stays in Service & Validator Layers (Pure POJO DTOs)**:
   - DTOs and models (`OrgHierarchyDto`, `OrganisationRecord`) remain **100% pure data carriers** (no business logic, no domain methods).
   - All evaluation logic, checks (`hasParent`, `isAlreadyLinkedTo`), and rule guards reside strictly in **`OrgHierarchyServiceImpl`** and **`OrgHierarchyValidator`**.
2. **Clean Error Representation (`err` & `errmsg`)**:
   - The `errors` list strictly reports failed items with exact Sunbird standard field names: `{ "childOrgId": "...", "err": "...", "errmsg": "..." }`.
   - Redundant fields like `"success": false` are omitted because the list exclusively contains failed items.
3. **Elimination of Nested If-Else Ladders (Flat Early-Return Guards)**:
   - Replaced deeply nested `if-else` blocks with clean **Flat Early-Return Guard Clauses**. Each rule is an independent, testable check with zero nesting.
4. **Self-Documenting, Action-Oriented Method Names**:
   - `validateCallerAuthorizationAndFetchUserId`
   - `validateParentAndCreateHierarchy`
   - `fetchBatchOrganisationsFromTable`
   - `fetchExistingHierarchyNodes`
   - `fetchParentAncestorChain`
   - `validateAndCategorizeChild`
   - `persistBatchChildLinks`
   - `logBatchLinkSuccessSummary`
   - `buildLinkApiResponse`
5. **No Artificial `maxBatchSize` Cap**: Seamlessly handles bulk requests (300 to 500+ orgs) via chunked fetching and JDBC batch operations.
6. **Dynamic Hierarchy Type Labeling**: Generates exact entity types in error messages (e.g. `"Org ... belongs to state ... but parent ... belongs to ministry ..."`).
7. **Consistent Terminology**: Uses `"organisation table"` instead of "master" (e.g. `"Org %s not found in organisation table"`).
8. **Clean Response & Rich Audit Logging**:
   - Removed `"successfulOrgIds"` from HTTP response body.
   - Full audit trail of attached orgs is logged at `INFO` level.
   - Single failure returns HTTP `400 Bad Request` with single error string in `params.errmsg`.

---

## 2. Clean Architecture & Guard Pipeline (Eliminating If-Else Ladders)

### 2.1. Flat Early-Return Guard Clauses (No Nesting)
To make code review effortless for tech leads, the validation logic avoids nested `if-else if-else` ladders. Every validation rule evaluates its condition and immediately exits:

```java
if (childId.equals(parentId)) {
    failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.CYCLE_DETECTED.name(), Constants.ERR_CANNOT_BE_OWN_PARENT));
    return;
}
```
- Zero `else` branches.
- Cyclomatic nesting depth stays strictly at **Level 1**.
- Each guard is isolated and readable at a glance.

---

## 3. Separation of Concerns: Pure DTOs vs. Service Logic

In enterprise Spring Boot applications, putting business logic or helper methods inside DTOs/records (like `OrgHierarchyDto`) violates separation of concerns. DTOs and models should remain **pure data transfer structures**.

All checks, condition evaluations, and formatting reside strictly within **`OrgHierarchyServiceImpl`** (or `OrgHierarchyValidator`):

```java
// Logic belongs strictly in Service implementation or Validator:
private static boolean hasParent(OrgHierarchyDto orgHierarchyDto) {
    return orgHierarchyDto != null && StringUtils.hasText(orgHierarchyDto.parentId());
}

private static boolean isAlreadyLinkedTo(OrgHierarchyDto orgHierarchyDto, String candidateParentId) {
    return hasParent(orgHierarchyDto) && orgHierarchyDto.parentId().equals(candidateParentId);
}

private static boolean isUnlinked(OrgHierarchyDto orgHierarchyDto) {
    return !hasParent(orgHierarchyDto);
}
```

- **`OrgHierarchyDto`** remains a pure data holder (getters and constructor only).
- Checking methods use Spring's `StringUtils.hasText` and `CollectionUtils.isEmpty` for robust null-safety without scattering `node.parentId() == null` across business logic.

---

## 4. Standardized Error Model (`OrgLinkErrorDetail`)

To maintain complete consistency with the Sunbird / iGOT `ApiResponse` envelope (which uses `params.err` and `params.errmsg`), each entry in the `errors` list uses the exact same field names:

```java
package com.hierarchyhub.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Standard error representation for a failed child organisation in a batch operation.
 * Matches the Sunbird envelope error naming standard (err, errmsg).
 */
@Getter
@AllArgsConstructor
public class OrgLinkErrorDetail {
    private final String childOrgId;
    private final String err;
    private final String errmsg;
}
```

### Why this design is superior:
1. **No Redundant Flags**: Since the `errors` list strictly holds failures, having `"success": false` is redundant clutter and is omitted.
2. **Standard Alignment**: Uses `err` for the error code (e.g. `ORG_INACTIVE`, `MINISTRY_MISMATCH`) and `errmsg` for the descriptive reason, exactly matching `params.err` and `params.errmsg`.
3. **Clean Frontend Consumption**: UI components and client applications can directly bind `error.childOrgId`, `error.err`, and `error.errmsg`.

---

## 5. Complete Guard Validation Matrix & Priorities

| Priority | Guard Name | Check Condition | Error Code (`err`) | HTTP Status | Rejection Message Template (`errmsg`) |
| :---: | :--- | :--- | :--- | :---: | :--- |
| **0.1** | **Caller Authentication** | Access token is valid and user id is resolved | `UNAUTHORIZED` | 401 | `Invalid or expired access token` |
| **0.2** | **Caller Authorization** | User roles contain `STATE_ADMIN`, `SPV_ADMIN`, `MDO_LEADER`, or `MDO_ADMIN` | `FORBIDDEN` | 403 | `User %s does not have required roles: %s` |
| **1.1** | **Request Structure** | `parentOrgId != blank`, `associations != empty`, all `childOrgId != blank` | `INVALID_REQUEST` | 400 | `Field validation errors: %s` |
| **2.1** | **Parent Table Existence** | Parent ID exists in Cassandra `organisation` table | `ORG_NOT_FOUND` | 404 | `Parent %s not found in organisation table` |
| **2.2** | **Parent Active Status** | Parent `status == 1` in Cassandra table | `ORG_INACTIVE` | 400 | `Parent %s is inactive` |
| **2.3** | **Parent Hierarchy Status** | Parent exists in `org_hierarchy` OR is auto-creatable Root (type 16/2048) | `ORG_NOT_FOUND` | 404 | `Parent %s is not in the hierarchy: map it first (parents are mapped top-down)` |
| **3.1** | **Self-Parenting Guard** | `childOrgId.equals(parentOrgId)` | `CYCLE_DETECTED` | 400 / 409 | `An org cannot be its own parent` |
| **3.2** | **Child Table Existence** | Child ID exists in Cassandra table | `ORG_NOT_FOUND` | 400 / 404 | `Org %s not found in organisation table` |
| **3.3** | **Child Active Status** | Child `status == 1` in Cassandra table | `ORG_INACTIVE` | 400 | `Org %s is inactive` |
| **3.4** | **L0 Root Immutability** | Child `organizationType` is NOT 16 (Ministry) or 2048 (State) | `IS_AN_L0` | 400 / 409 | `Org %s is a %s (organizationType %d): it cannot be attached under another org` |
| **3.5** | **Hierarchy Type Match** | Child hierarchy type matches Parent hierarchy type | `HIERARCHY_TYPE_MISMATCH` | 400 / 409 | `Org %s belongs to the %s hierarchy but parent %s belongs to the %s hierarchy` |
| **3.6** | **Ministry/State Boundary** | Child root ID matches Parent root ID (`ministryorstateid`) | `MINISTRY_MISMATCH` | 400 / 409 | *(Dynamic wording based on types)*<br/>`Org %s belongs to %s %s but parent %s belongs to %s %s` |
| **3.7** | **Parent Conflict Guard** | `hasParent(existingNode)` and not already linked to `parentId` | `ALREADY_HAS_PARENT` | 400 / 409 | `Org %s is already under %s; use /move` |
| **3.8** | **Cycle Prevention Guard** | `parentAncestorIds.contains(childId)` is FALSE | `CYCLE_DETECTED` | 400 / 409 | `Org %s cannot be placed under %s: that is one of its own descendants` |

---

## 6. Sample Request & Response Payloads

### 6.1. Request Payloads

#### Bulk Request (e.g. 500 Orgs)
```http
POST /org/v2/hierarchy/link HTTP/1.1
Host: localhost:8888
Content-Type: application/json
x-authenticated-user-token: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

{
  "request": {
    "parentOrgId": "0142691971194388486",
    "associations": [
      { "childOrgId": "01357983854455193614" },
      { "childOrgId": "01357983854455193615" },
      { "childOrgId": "01357983854455193616" }
    ]
  }
}
```

---

### 6.2. Response Payloads

#### Scenario A: 100% Full Success (Single or Bulk)
- **HTTP Status:** `200 OK`
```json
{
  "id": "api.org.hierarchy.link",
  "ver": "v2",
  "ts": "2026-11-07 16:30:00.000",
  "params": {
    "resmsgid": "7f80db7b-1234-4567-89ab-cdef01234567",
    "msgid": "a901ef23-4567-89ab-cdef-0123456789ab",
    "err": null,
    "status": "SUCCESS",
    "errmsg": null
  },
  "responseCode": "OK",
  "result": {
    "message": "Successfully added organisation to the hierarchy",
    "totalCount": 500,
    "successCount": 500,
    "failedCount": 0
  }
}
```

#### Scenario B: Single Child Request Failed (e.g. Inactive)
- **HTTP Status:** `400 Bad Request`
- Returns single `err` and single `errmsg` string directly in `params`.
```json
{
  "id": "api.org.hierarchy.link",
  "ver": "v2",
  "ts": "2026-11-07 16:30:00.000",
  "params": {
    "resmsgid": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
    "msgid": "f0e1d2c3-b4a5-9876-5432-10fedcba9876",
    "err": "ORG_INACTIVE",
    "status": "FAILED",
    "errmsg": "Org 01357983854455193614 is inactive"
  },
  "responseCode": "BAD_REQUEST",
  "result": {}
}
```

#### Scenario C: Bulk Request with Partial Success (e.g. 5 Sent: 3 Passed, 2 Failed)
- **HTTP Status:** `200 OK`
```json
{
  "id": "api.org.hierarchy.link",
  "ver": "v2",
  "ts": "2026-11-07 16:30:00.000",
  "params": {
    "resmsgid": "4d5e6f7a-8b9c-0d1e-2f3a-4b5c6d7e8f9a",
    "msgid": "9a8b7c6d-5e4f-3a2b-1c0d-ef0123456789",
    "err": null,
    "status": "PARTIAL_SUCCESS",
    "errmsg": "Failed to link 2 of 5 organisations"
  },
  "responseCode": "OK",
  "result": {
    "message": "Partially added organisations to the hierarchy",
    "totalCount": 5,
    "successCount": 3,
    "failedCount": 2,
    "errors": [
      {
        "childOrgId": "01357983854455193616",
        "err": "ORG_INACTIVE",
        "errmsg": "Org 01357983854455193616 is inactive"
      },
      {
        "childOrgId": "01357983854455193617",
        "err": "MINISTRY_MISMATCH",
        "errmsg": "Org 01357983854455193617 belongs to state 012345 but parent 0142691971194388486 belongs to ministry 019999"
      }
    ]
  }
}
```

#### Scenario D: Bulk Request with All Failed (0 Passed, 5 Failed)
- **HTTP Status:** `400 Bad Request`
```json
{
  "id": "api.org.hierarchy.link",
  "ver": "v2",
  "ts": "2026-11-07 16:30:00.000",
  "params": {
    "resmsgid": "6a7b8c9d-0e1f-2a3b-4c5d-6e7f8a9b0c1d",
    "msgid": "0f1e2d3c-4b5a-6978-89ab-cdef01234567",
    "err": "INVALID_REQUEST",
    "status": "FAILED",
    "errmsg": "All 5 organisations failed to link"
  },
  "responseCode": "BAD_REQUEST",
  "result": {
    "message": "Failed to add organisations to the hierarchy",
    "totalCount": 5,
    "successCount": 0,
    "failedCount": 5,
    "errors": [
      {
        "childOrgId": "01357983854455193616",
        "err": "ORG_INACTIVE",
        "errmsg": "Org 01357983854455193616 is inactive"
      },
      {
        "childOrgId": "01357983854455193617",
        "err": "MINISTRY_MISMATCH",
        "errmsg": "Org 01357983854455193617 belongs to state 012345 but parent 0142691971194388486 belongs to ministry 019999"
      }
    ]
  }
}
```

---

## 7. Service Implementation Blueprint (`OrgHierarchyServiceImpl`)

All state evaluation methods reside inside the service implementation as private static helpers:

```java
package com.hierarchyhub.service.impl;

@Service
public class OrgHierarchyServiceImpl implements OrgHierarchyService {

    private static final Logger log = LoggerFactory.getLogger(OrgHierarchyServiceImpl.class);

    private final OrgHierarchyRepository orgHierarchyRepository;
    private final OrgHierarchyValidator orgHierarchyValidator;

    public OrgHierarchyServiceImpl(OrgHierarchyRepository orgHierarchyRepository,
                                   OrgHierarchyValidator orgHierarchyValidator) {
        this.orgHierarchyRepository = orgHierarchyRepository;
        this.orgHierarchyValidator = orgHierarchyValidator;
    }

    @Override
    @Transactional(OrgHierarchyTransactionConfig.ORG_HIERARCHY_TX_MANAGER)
    public ApiResponse linkOrgToHierarchy(LinkOrgRequest request, String accessToken) {
        String userId = orgHierarchyValidator.validateCallerAuthorizationAndFetchUserId(accessToken);

        String parentId = request.getRequest().getParentOrgId();
        List<String> childIds = extractUniqueChildIdentifiers(request.getRequest().getAssociations());
        log.info("Starting linkOrgToHierarchy: parentId={}, requestedChildCount={}", parentId, childIds.size());

        OrganisationRecord parentRecord = orgHierarchyValidator.validateParentAndCreateHierarchy(parentId, userId);

        // Pre-fetch all batch data in bulk (1 Cassandra IN query + 1 Postgres IN query + 1 CTE)
        Map<String, OrganisationRecord> childTableRecords = orgHierarchyValidator.fetchBatchOrganisationsFromTable(childIds);
        Map<String, OrgHierarchyDto> existingHierarchyNodes = orgHierarchyRepository.findExisting(childIds);
        Set<String> parentAncestorChain = new HashSet<>(orgHierarchyRepository.chainUp(parentId));

        int capacity = childIds.size();
        List<String> newChildIdsToInsert = new ArrayList<>(capacity);
        List<String> newChildNamesToInsert = new ArrayList<>(capacity);
        List<String> existingChildIdsToUpdate = new ArrayList<>(capacity);
        List<String> succeededChildIds = new ArrayList<>(capacity);
        List<OrgLinkErrorDetail> failureErrors = new ArrayList<>();

        for (String childId : childIds) {
            validateAndCategorizeChild(childId, parentId, parentRecord,
                    childTableRecords.get(childId),
                    existingHierarchyNodes.get(childId),
                    parentAncestorChain,
                    newChildIdsToInsert, newChildNamesToInsert,
                    existingChildIdsToUpdate, succeededChildIds, failureErrors);
        }

        // Persist database updates in batch
        persistBatchChildLinks(parentId, userId, newChildIdsToInsert, newChildNamesToInsert, existingChildIdsToUpdate);

        // Audit logging of all succeeded IDs
        logBatchLinkSuccessSummary(parentId, succeededChildIds, failureErrors.size());

        // Return standardized ApiResponse
        return buildLinkApiResponse(childIds.size(), succeededChildIds.size(), failureErrors);
    }

    /**
     * Clean, flat guard clauses with zero nested else statements.
     * Uses service-level helpers hasParent() and isAlreadyLinkedTo().
     */
    private void validateAndCategorizeChild(
            String childId, String parentId, OrganisationRecord parentRecord,
            OrganisationRecord childRecord, OrgHierarchyDto existingNode,
            Set<String> parentAncestors,
            List<String> newChildIds, List<String> newChildNames,
            List<String> updateChildIds, List<String> succeededIds,
            List<OrgLinkErrorDetail> failureErrors) {

        // Guard 1: Self-parenting check
        if (childId.equals(parentId)) {
            failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.CYCLE_DETECTED.name(), Constants.ERR_CANNOT_BE_OWN_PARENT));
            return;
        }

        // Guard 2: Table existence check
        if (childRecord == null) {
            failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.ORG_NOT_FOUND.name(),
                    String.format(Constants.ERR_ORG_NOT_FOUND_IN_TABLE, childId)));
            return;
        }

        // Guard 3: Active status check
        if (!childRecord.active()) {
            failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.ORG_INACTIVE.name(),
                    String.format(Constants.ERR_ORG_IS_INACTIVE, childId)));
            return;
        }

        // Guard 4: L0 Root check (Ministry/State root cannot be attached as child)
        if (orgHierarchyValidator.isMinistryOrStateRoot(childRecord)) {
            String typeName = orgHierarchyValidator.hierarchyTypeOf(childRecord);
            failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.IS_AN_L0.name(),
                    String.format(Constants.ERR_ORG_IS_ROOT_L0, childId, typeName, childRecord.organizationType())));
            return;
        }

        // Guard 5: Hierarchy type compatibility
        String childType = orgHierarchyValidator.hierarchyTypeOf(childRecord);
        String parentType = orgHierarchyValidator.hierarchyTypeOf(parentRecord);
        if (childType != null && parentType != null && !childType.equalsIgnoreCase(parentType)) {
            failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.HIERARCHY_TYPE_MISMATCH.name(),
                    String.format(Constants.ERR_HIERARCHY_TYPE_MISMATCH, childId, childType, parentId, parentType)));
            return;
        }

        // Guard 6: Dynamic Ministry/State boundary check
        String childRootId = orgHierarchyValidator.hierarchyRootIdOf(childRecord);
        String parentRootId = orgHierarchyValidator.hierarchyRootIdOf(parentRecord);
        if (!Objects.equals(childRootId, parentRootId)) {
            failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.MINISTRY_MISMATCH.name(),
                    String.format(Constants.ERR_DYNAMIC_MINISTRY_MISMATCH, childId, childType, childRootId, parentId, parentType, parentRootId)));
            return;
        }

        // Guard 7: New node check (does not exist in hierarchy yet)
        if (existingNode == null) {
            newChildIds.add(childId);
            newChildNames.add(AppUtil.defaultIfBlank(childRecord.name(), childId));
            succeededIds.add(childId);
            return;
        }

        // Guard 8: Idempotency check using service helper
        if (isAlreadyLinkedTo(existingNode, parentId)) {
            succeededIds.add(childId); // Idempotent success (no-op)
            return;
        }

        // Guard 9: Parent conflict check using service helper
        if (hasParent(existingNode)) {
            failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.ALREADY_HAS_PARENT.name(),
                    String.format(Constants.ERR_ALREADY_HAS_PARENT, childId, existingNode.parentId())));
            return;
        }

        // Guard 10: Cycle check
        if (parentAncestors.contains(childId)) {
            failureErrors.add(new OrgLinkErrorDetail(childId, ErrorCode.CYCLE_DETECTED.name(),
                    String.format(Constants.ERR_CYCLE_DETECTED, childId, parentId)));
            return;
        }

        // Existing unlinked node: Valid to attach
        updateChildIds.add(childId);
        succeededIds.add(childId);
    }

    // =========================================================================
    // Service-level helper methods (logic kept in service, NOT in DTOs)
    // =========================================================================

    private static boolean hasParent(OrgHierarchyDto orgHierarchyDto) {
        return orgHierarchyDto != null && StringUtils.hasText(orgHierarchyDto.parentId());
    }

    private static boolean isAlreadyLinkedTo(OrgHierarchyDto orgHierarchyDto, String candidateParentId) {
        return hasParent(orgHierarchyDto) && orgHierarchyDto.parentId().equals(candidateParentId);
    }

    private void persistBatchChildLinks(String parentId, String userId,
                                        List<String> newChildIds, List<String> newChildNames,
                                        List<String> updateChildIds) {
        if (!CollectionUtils.isEmpty(newChildIds)) {
            orgHierarchyRepository.batchInsertMapped(newChildIds, newChildNames, parentId, userId);
        }
        if (!CollectionUtils.isEmpty(updateChildIds)) {
            orgHierarchyRepository.batchSetParent(updateChildIds, parentId, userId);
        }
        int totalNewlyAttached = newChildIds.size() + updateChildIds.size();
        if (totalNewlyAttached > 0) {
            orgHierarchyRepository.incrementChildCountBy(parentId, totalNewlyAttached);
        }
    }

    private void logBatchLinkSuccessSummary(String parentId, List<String> succeededChildIds, int failedCount) {
        log.info("Batch link completed: parentId={}, attachedChildCount={}, succeededChildIds={}, failedCount={}",
                parentId, succeededChildIds.size(), succeededChildIds, failedCount);
    }

    private ApiResponse buildLinkApiResponse(int totalCount, int successCount, List<OrgLinkErrorDetail> failures) {
        // Case 1: Single item sent and failed -> 400 Bad Request with single string errmsg
        if (totalCount == 1 && !CollectionUtils.isEmpty(failures)) {
            OrgLinkErrorDetail failure = failures.get(0);
            return ApiResponse.failure(Constants.API_LINK, HttpStatus.BAD_REQUEST,
                    failure.getErr(), failure.getErrmsg());
        }

        // Case 2: All items in batch failed -> 400 Bad Request with errors list
        if (successCount == 0 && !CollectionUtils.isEmpty(failures)) {
            ApiResponse response = ApiResponse.failure(Constants.API_LINK, HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_REQUEST.name(), Constants.MSG_ALL_ORGS_FAILED_TO_LINK);
            populateResultDetails(response, Constants.MSG_ALL_ORGS_FAILED_TO_LINK, totalCount, 0, failures.size(), failures);
            return response;
        }

        // Case 3: Partial Success in batch -> 200 OK with PARTIAL_SUCCESS status
        if (!CollectionUtils.isEmpty(failures)) {
            ApiResponse response = AppUtil.versionedResponse(Constants.API_LINK, null);
            response.getParams().setStatus(Constants.STATUS_PARTIAL_SUCCESS);
            response.getParams().setErrmsg(String.format("Failed to link %d of %d organisations", failures.size(), totalCount));
            populateResultDetails(response, Constants.MSG_ORG_PARTIALLY_ADDED, totalCount, successCount, failures.size(), failures);
            return response;
        }

        // Case 4: Full Success -> 200 OK
        ApiResponse response = AppUtil.versionedResponse(Constants.API_LINK, null);
        populateResultDetails(response, Constants.MSG_ORG_ADDED_TO_HIERARCHY, totalCount, successCount, 0, null);
        return response;
    }

    private void populateResultDetails(ApiResponse response, String message, int total, int success, int failed,
                                       List<OrgLinkErrorDetail> errors) {
        response.put(Constants.MESSAGE, message);
        response.put(Constants.KEY_TOTAL_COUNT, total);
        response.put(Constants.KEY_SUCCESS_COUNT, success);
        response.put(Constants.KEY_FAILED_COUNT, failed);
        if (!CollectionUtils.isEmpty(errors)) {
            response.put(Constants.KEY_ERRORS, errors);
        }
    }
}
```
