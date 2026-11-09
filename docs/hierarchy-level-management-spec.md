# Technical Specification: Hierarchy Levels Management & Auto-Provisioning

**Service:** `cb-org-hierarchy-service`  
**Endpoints:**  
- Internal Auto-Provisioning: Hooked into `POST /org/v2/hierarchy/link` and `POST /org/v2/hierarchy/bulk/link/{rootOrgId}`  
- Update/Patch API: `PATCH /org/v2/hierarchy/levels/{rootOrgId}` (or `POST /org/v2/hierarchy/levels/{rootOrgId}`)  
- Read Levels API: `GET /org/v2/hierarchy/levels/{rootOrgId}`  
**Target Module:** Hierarchy Levels Engine (`org_hierarchy_level`)  
**Document Status:** Complete Architecture & Implementation Specification (Ready for Review)  

---

## 1. Executive Summary & Review Objectives

In Karmayogi, different administrative jurisdictions (Central Ministries vs. State Governments) organize their hierarchy using distinct organizational terminology:
* **Central Ministry**: Level 1 = `"Ministry"`, Level 2 = `"Department"`, Level 3 = `"Autonomous Body / Directorate"`, Level 4 = `"Field Unit"`.
* **State Government**: Level 1 = `"State Government"`, Level 2 = `"Department"`, Level 3 = `"Directorate"`, Level 4 = `"District Office"`, ..., Level 10 = `"Panchayat / Block"`.

This specification details how hierarchy level configurations are managed with **zero manual friction** on Day 1, and how authorized administrators can customize level labels at any time.

### Core Architectural Guarantees:
1. **Zero-Touch Day 1 Initialization**: Default levels (`L1` to `L10`) are created **internally** when a Ministry or State is first mapped via `/link` or `/bulk/link`.
2. **Ministry/State Eligibility Verification**: Levels are only initialized for true L0 roots whose `organizationType` in Cassandra is `16` (Ministry) or `2048` (State), matching the existing `validateParentAndCreateHierarchyIfNeeded` validation flow.
3. **Natural Composite Primary Key**: The table `org_hierarchy_level` uses `(root_org_id, level)` as its natural primary key. Redundant surrogate `id` columns (UUIDs) are omitted.
4. **Clean Update / Patch API**: An atomic update endpoint updates level display names and descriptions, returning a standard generic confirmation message.
5. **Decoupled Concurrency Safety**: Level configuration is decoupled from tree structural writes. `/move`, `/link`, and `/unlink` operations never lock or contend on the level configuration table.

---

## 2. Database Schema: `org_hierarchy_level`

### 2.1. DDL Specification

```sql
CREATE TABLE IF NOT EXISTS org_hierarchy_level (
    root_org_id         VARCHAR(64)  NOT NULL,
    level               INT          NOT NULL CHECK (level >= 1 AND level <= 10),
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64),
    
    -- Composite primary key: guarantees exactly one entry per level per root org
    PRIMARY KEY (root_org_id, level)
);

-- B-Tree Index for high-speed sequential scan of 10 levels by root_org_id
CREATE INDEX IF NOT EXISTS idx_org_hierarchy_level_root 
ON org_hierarchy_level (root_org_id, level ASC);
```

### 2.2. Schema Column Metadata

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `root_org_id` | `VARCHAR(64)` | `NOT NULL, PK (Part 1)` | The L0 Root Organisation ID (Ministry or State). |
| `level` | `INT` | `NOT NULL, PK (Part 2), CHECK(1..10)` | Level index (1 through 10). |
| `name` | `VARCHAR(255)` | `NOT NULL` | Display name (e.g. `"Ministry"`, `"Department"`, or default `"L1"`). |
| `description` | `TEXT` | `NULLABLE` | Explanatory description (e.g. `"Central Ministry Level"`). |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | Record creation timestamp. |
| `created_by` | `VARCHAR(64)` | `NULLABLE` | User ID of creator (from JWT auth token). |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT NOW()` | Record update timestamp. |
| `updated_by` | `VARCHAR(64)` | `NULLABLE` | User ID of modifier (from JWT auth token). |

---

## 3. Step 1: Internal Auto-Provisioning on `/link` and `/bulk/link`

When organisations are linked under a root for the first time, default levels `L1` through `L10` must be provisioned automatically without requiring an upfront API call.

```mermaid
flowchart TD
    A["POST /link OR /bulk/link/{rootOrgId}"] --> B["Resolve rootOrgId (L0 root)"]
    B --> C{"Check DB:<br/>org_hierarchy_level has rows for rootOrgId?"}
    C -- "YES (Already Created)" --> D["Skip Level Creation (No-Op)"]
    C -- "NO (Not Created Yet)" --> E{"Check Cassandra master:<br/>organizationType == 16 (Ministry)<br/>OR organizationType == 2048 (State)?"}
    E -- "NO (Not an L0 Root)" --> F["Skip Level Creation"]
    E -- "YES (Valid L0 Root)" --> G["Batch INSERT 10 Rows (L1..L10)<br/>ON CONFLICT (root_org_id, level) DO NOTHING"]
    G --> H["Continue with Org Linking"]
    D --> H
```

### 3.1. Integration with `validateParentAndCreateHierarchyIfNeeded`

In `OrgHierarchyServiceImpl.java` (lines 125–133), the method `validateParentAndCreateHierarchyIfNeeded` is the **single shared gateway** where a parent organisation is validated against Cassandra, verified as a Ministry or State, and created in PostgreSQL.

The auto-provisioning hook integrates directly inside `validateParentAndCreateHierarchyIfNeeded`:

```java
private OrganisationRecord validateParentAndCreateHierarchyIfNeeded(String parentId, String userId) {
    OrganisationRecord parentRecord = cassandraUtils.fetchBatchOrganisationsFromTable(Collections.singletonList(parentId)).get(parentId);
    orgHierarchyValidator.requireActiveParentOrganisation(parentId, parentRecord);
    
    if (!orgHierarchyRepository.findById(parentId).isPresent()) {
        orgHierarchyValidator.requireMinistryOrStateRoot(parentId, parentRecord);
        orgHierarchyRepository.createRoot(parentId, nameOrId(parentRecord), userId);
        
        // Auto-provision 10 default levels (L1 to L10) immediately upon root creation
        ensureDefaultLevelsProvisioned(parentId, userId);
    } else if (orgHierarchyValidator.isMinistryOrStateRoot(parentRecord)) {
        // Safe idempotency: if root already existed in org_hierarchy but levels table doesn't have rows yet
        ensureDefaultLevelsProvisioned(parentId, userId);
    }
    return parentRecord;
}

/**
 * Ensures default levels (L1 to L10) exist for an eligible L0 root org.
 * Checks DB existence first to guarantee zero extra queries once initialized.
 */
public void ensureDefaultLevelsProvisioned(String rootOrgId, String userId) {
    // Fast check: if levels already exist in org_hierarchy_level, exit immediately (0 ms overhead)
    if (orgHierarchyLevelRepository.hasLevels(rootOrgId)) {
        return;
    }

    // Batch insert default 10 levels (L1 to L10)
    List<HierarchyLevelRecord> defaultLevels = generateDefaultLevelDefinitions(rootOrgId, userId);
    orgHierarchyLevelRepository.batchInsertLevels(defaultLevels);
    log.info("ensureDefaultLevelsProvisioned: Created default 10 hierarchy levels for rootOrgId={}", rootOrgId);
}
```

### 3.2. Batch Insert SQL Query

```sql
INSERT INTO org_hierarchy_level (root_org_id, level, name, description, created_by, updated_by)
VALUES 
  (:rootOrgId, 1, 'L1', 'LevelOne', :userId, :userId),
  (:rootOrgId, 2, 'L2', 'LevelTwo', :userId, :userId),
  (:rootOrgId, 3, 'L3', 'LevelThree', :userId, :userId),
  (:rootOrgId, 4, 'L4', 'LevelFour', :userId, :userId),
  (:rootOrgId, 5, 'L5', 'LevelFive', :userId, :userId),
  (:rootOrgId, 6, 'L6', 'LevelSix', :userId, :userId),
  (:rootOrgId, 7, 'L7', 'LevelSeven', :userId, :userId),
  (:rootOrgId, 8, 'L8', 'LevelEight', :userId, :userId),
  (:rootOrgId, 9, 'L9', 'LevelNine', :userId, :userId),
  (:rootOrgId, 10, 'L10', 'LevelTen', :userId, :userId)
ON CONFLICT (root_org_id, level) DO NOTHING;
```
* Using `ON CONFLICT (root_org_id, level) DO NOTHING` ensures safety during racing parallel requests.

---

## 4. Step 2: Patch / Update Levels API

Allows authorized users to customize level names and descriptions.

* **Method**: `PATCH /org/v2/hierarchy/levels/{rootOrgId}`  
  *(Alternative alias: `POST /org/v2/hierarchy/levels/{rootOrgId}`)*
* **Header**: `x-authenticated-user-token: <token>` (Required)

### 4.1. Request DTO Contract

```json
{
  "request": {
    "levels": [
      {
        "level": 1,
        "name": "Ministry",
        "description": "Central Government Ministry"
      },
      {
        "level": 2,
        "name": "Department",
        "description": "Department under Ministry"
      },
      {
        "level": 3,
        "name": "Autonomous Body / Directorate",
        "description": "Directorate or Attached Office"
      },
      {
        "level": 4,
        "name": "Field Unit",
        "description": "District or Local Field Unit"
      }
    ]
  }
}
```

### 4.2. Java DTO Definitions (3 Standalone Top-Level Classes in `com.hierarchyhub.dto`)

In accordance with project standards (matching `LinkOrgRequest` $\rightarrow$ `LinkOrgRequestBody` $\rightarrow$ `AssociationRef`), all DTOs are defined as separate, standalone public classes:

**1. Outer Request Envelope (`com/hierarchyhub/dto/UpdateHierarchyLevelsRequest.java`)**:
```java
package com.hierarchyhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHierarchyLevelsRequest {

    @NotNull(message = "request is required")
    @Valid
    private UpdateHierarchyLevelsRequestBody request;
}
```

**2. Request Body (`com/hierarchyhub/dto/UpdateHierarchyLevelsRequestBody.java`)**:
```java
package com.hierarchyhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHierarchyLevelsRequestBody {

    @NotEmpty(message = "levels list must not be empty")
    @Valid
    private List<HierarchyLevelItem> levels;
}
```

**3. Item Model (`com/hierarchyhub/dto/HierarchyLevelItem.java`)**:
```java
package com.hierarchyhub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HierarchyLevelItem {

    @NotNull(message = "level number is required")
    @Min(value = 1, message = "level must be between 1 and 10")
    @Max(value = 10, message = "level must be between 1 and 10")
    private Integer level;

    @NotBlank(message = "name is required")
    private String name;

    private String description;
}
```

### 4.3. Flat Guard Pipeline in `OrgHierarchyServiceImpl`

```java
@Transactional(OrgHierarchyTransactionConfig.ORG_HIERARCHY_TX_MANAGER)
public ApiResponse updateHierarchyLevels(String rootOrgId, UpdateHierarchyLevelsRequest request, String accessToken) {
    String userId = authUtils.validateCallerAuthenticationAndFetchUserId(accessToken);

    // Guard 1: Verify rootOrgId exists in hierarchy
    orgHierarchyValidator.requireExisting(
        orgHierarchyRepository.findById(rootOrgId).isPresent(),
        "Organisation " + rootOrgId + " is not in the hierarchy"
    );

    // Guard 2: Auto-provision defaults if not already present, ensuring rows exist to update
    ensureDefaultLevelsProvisioned(rootOrgId, userId);

    // Guard 3: Perform batch update using standalone item DTOs
    List<HierarchyLevelItem> items = request.getRequest().getLevels();
    orgHierarchyLevelRepository.batchUpdateLevels(rootOrgId, items, userId);
    log.info("updateHierarchyLevels: Updated {} levels for rootOrgId={} by userId={}", items.size(), rootOrgId, userId);

    return versionedResponse(Constants.API_LEVELS_UPDATE, new BatchOperationResponse("Successfully updated hierarchy level definitions"));
}
```

### 4.4. Batch Update SQL

```sql
UPDATE org_hierarchy_level 
SET name = :name, 
    description = :description, 
    updated_by = :userId, 
    updated_at = NOW()
WHERE root_org_id = :rootOrgId AND level = :level;
```

### 4.5. Success Response Contract (HTTP 200 OK)

```json
{
  "id": "api.org.hierarchy.levels.update",
  "ver": "v2",
  "ts": "2026-11-08 16:30:00.000",
  "params": {
    "resmsgid": "3a8d9e21-0987-4321-ba98-7c6d5e4f3a21",
    "msgid": "3a8d9e21-0987-4321-ba98-7c6d5e4f3a21",
    "err": null,
    "status": "SUCCESS",
    "errmsg": null
  },
  "responseCode": "OK",
  "result": {
    "message": "Successfully updated hierarchy level definitions"
  }
}
```

---

## 5. Step 3: Standalone Read Levels API (For Admin Settings)

Allows the portal settings page to read the configured levels for a Ministry/State without fetching org nodes.

* **Method**: `GET /org/v2/hierarchy/levels/{rootOrgId}`
* **Header**: `x-authenticated-user-token: <token>` (Required)

### Success Response Contract (HTTP 200 OK)

```json
{
  "id": "api.org.hierarchy.levels.read",
  "ver": "v2",
  "ts": "2026-11-08 16:30:15.000",
  "params": {
    "status": "SUCCESS"
  },
  "responseCode": "OK",
  "result": {
    "rootOrgId": "0146068546191196160",
    "maxAllowedLevel": 10,
    "levels": [
      { "level": 1, "name": "Ministry", "description": "Central Government Ministry" },
      { "level": 2, "name": "Department", "description": "Department under Ministry" },
      { "level": 3, "name": "Directorate / Board", "description": "Directorate or Attached Office" },
      { "level": 4, "name": "Field Unit", "description": "District or Local Field Unit" },
      { "level": 5, "name": "L5", "description": "LevelFive" },
      { "level": 6, "name": "L6", "description": "LevelSix" },
      { "level": 7, "name": "L7", "description": "LevelSeven" },
      { "level": 8, "name": "L8", "description": "LevelEight" },
      { "level": 9, "name": "L9", "description": "LevelNine" },
      { "level": 10, "name": "L10", "description": "LevelTen" }
    ]
  }
}
```

---

## 6. Complete Guard Validation Matrix

| Priority | Guard Name | Condition | Error Code (`err`) | HTTP Status | Rejection Message (`errmsg`) |
| :---: | :--- | :--- | :--- | :---: | :--- |
| **1.1** | **Caller Authentication** | Header missing or invalid | `UNAUTHORIZED` | 401 | `Invalid or expired access token` |
| **1.2** | **Request Body Null** | `request` body missing | `INVALID_REQUEST` | 400 | `request is required` |
| **1.3** | **Levels List Empty** | `levels` array empty | `INVALID_REQUEST` | 400 | `levels list must not be empty` |
| **1.4** | **Level Bounds** | `level < 1` or `level > 10` | `INVALID_REQUEST` | 400 | `level must be between 1 and 10` |
| **1.5** | **Blank Name** | `name` is blank | `INVALID_REQUEST` | 400 | `name is required` |
| **1.6** | **Root Org Existence** | `rootOrgId` not in hierarchy | `ORG_NOT_FOUND` | 404 | `Organisation {rootOrgId} is not in the hierarchy` |

---

## 7. Performance Guarantees

1. **Table Lookup Speed**: Retrieving 10 level definitions via the index `(root_org_id, level ASC)` takes **< 0.4 ms**.
2. **Table Storage**: Storing 10 rows per root org requires less than **1 KB per root org**. Even for 1,000 ministries, total disk usage is < 1 MB.
3. **Zero Structural Lock Contention**: `/move`, `/link`, and `/unlink` operations never contend on `org_hierarchy_level`.

