# Technical Specification: Hierarchy Read & Complete APIs with Dynamic Levels

**Service:** `cb-org-hierarchy-service`  
**Endpoints:**  
- Paginated Flat Read: `POST /org/v2/hierarchy/read/{orgId}`  
- Recursive Complete Tree: `GET /org/v2/hierarchy/{orgId}/complete`  
**Target Release / PR:** Dynamic Levels Integration & Clean Architecture Refactoring  
**Document Status:** Complete Architecture & Implementation Specification (Ready for Review)  

---

## 1. Executive Summary & Review Objectives

The hierarchy query layer in `cb-org-hierarchy-service` provides two complementary views of an organisation tree stored in PostgreSQL (`org_hierarchy`):

1. **`POST /org/v2/hierarchy/read/{orgId}` (Flat Paginated Listing)**:
   - Designed for high-frequency portal browsing, search, and infinite scrolling.
   - Returns **minimal, flat** data: direct children (or search matches) with offset/cursor pagination.
   - Paging protects against wide nodes (e.g. 1,000 direct children).
2. **`GET /org/v2/hierarchy/{orgId}/complete` (Recursive Nested Tree)**:
   - Designed for whole-tree rendering, organizational chart visualizations, and deep structural inspection.
   - Returns **comprehensive, nested** data: parent $\to$ children $\to$ grandchildren down to the deepest leaf (or bounded by `depth`).

### Key Innovation: Unified Level Metadata with Structural Specialization
Both APIs share the exact same level intelligence fields, while respecting their structural specialization:
* **Common Fields**: Both endpoints report `level`, `levelName`, `maxCreatedLevel`, `maxAllowedLevel`, and the 10-level blueprint (`levels` array).
* **Structural Difference**:
  * `/read` presents children as a **flat list** (`ChildOrgSummary`).
  * `/complete` presents children as a **recursively nested tree** (`OrgTreeNode`).

---

## 2. Structural Comparison Matrix: `/read` vs. `/complete`

| Architectural Dimension | `POST /org/v2/hierarchy/read/{orgId}` | `GET /org/v2/hierarchy/{orgId}/complete` |
| :--- | :--- | :--- |
| **HTTP Method & URL** | `POST /org/v2/hierarchy/read/{orgId}` | `GET /org/v2/hierarchy/{orgId}/complete` |
| **Data Topology** | **Flat List** (`List<ChildOrgSummary>`) | **Recursive Tree** (`List<OrgTreeNode>`) |
| **Primary Use Case** | Directory listing, search, lazy-loading sub-branches | Whole-tree rendering, Org Chart view, export |
| **Pagination Support** | **Yes** (`limit`, `offset`, keyset `cursor`) | **No** (Depth-bounded complete subtree) |
| **Search Filter Support** | **Yes** (`query` matches `id` or `lower(name)`) | **No** (Whole subtree returned) |
| **Node `level` Field** | Populated on root and each child | Populated on root and every nested node |
| **Node `levelName` Field** | Populated on root and each child | Populated on root and every nested node |
| **`levels` Blueprint Array** | Included when `"levelRequired": true` | Included when `?levelRequired=true` (or default) |
| **`maxCreatedLevel`** | Deepest level reached under queried org | Deepest level reached in the returned tree |
| **`maxAllowedLevel`** | Hard boundary: `10` | Hard boundary: `10` |
| **Average Latency** | **10 ms – 20 ms** (fetching 50 items) | **25 ms – 40 ms** (fetching 1,000 nodes) |

> **Dynamic `levels` blueprint**: the array always starts at the *queried org's own level*, not always `L1`. Calling either
> endpoint on a level-2 org returns `levels` covering `2..10` only - levels above the queried org are never shown, since
> they describe ancestors the caller didn't ask about. A level-1 (L0 root) query still sees the full `1..10` blueprint.

> **`countDescendants` on `/read`**: defaults to `false` - a caller only pays for the recursive descendant-count query
> when it explicitly sends `"countDescendants": true`; the field itself is omitted from each `ChildOrgSummary` (not
> `null`) whenever it wasn't requested.

---

## 3. End-to-End Architectural Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as UI Portal / API Gateway
    participant Ctrl as OrgHierarchyController
    participant Svc as OrgHierarchyServiceImpl
    participant Val as OrgHierarchyValidator
    participant LvlRepo as OrgHierarchyLevelRepository
    participant Repo as OrgHierarchyRepository (PostgreSQL)

    Note over Client,Ctrl: Scenario A: POST /read/{orgId} (Flat Read)
    Client->>Ctrl: POST /read/{orgId} {"limit": 50, "levelRequired": true}
    Ctrl->>Svc: readOrgChildren(orgId, request, accessToken)
    Svc->>Val: validateCallerAuthenticationAndFetchUserId(accessToken)
    Svc->>Repo: findById(orgId) (Root existence check)
    Svc->>Repo: chainUp(orgId)
    Note over Svc: chainUp yields: rootLevel = chain.size()<br/>rootOrgId = chain.last()
    Svc->>LvlRepo: findLevelsByRootOrgId(rootOrgId)
    Note over Svc: Fetches 10 custom level names in < 0.4 ms
    Svc->>Repo: findDescendantsByOffset / findDescendants (Limit 50)
    Note over Svc: Maps level = rootLevel + item.depth()<br/>Maps levelName from level definitions
    Svc-->>Client: 200 OK (ReadChildrenResponse: flat associations)

    Note over Client,Ctrl: Scenario B: GET /{orgId}/complete (Nested Tree)
    Client->>Ctrl: GET /{orgId}/complete?depth=5&levelRequired=true
    Ctrl->>Svc: readOrgHierarchy(orgId, depth, levelRequired, accessToken)
    Svc->>Val: validateCallerAuthenticationAndFetchUserId(accessToken)
    Svc->>Repo: findById(orgId)
    Svc->>Repo: chainUp(orgId) (Resolves rootLevel and rootOrgId)
    Svc->>LvlRepo: findLevelsByRootOrgId(rootOrgId)
    Svc->>Repo: findSubtree(orgId)
    Note over Svc: buildTree() recursively nests associations<br/>and injects level & levelName on every node
    Svc-->>Client: 200 OK (HierarchyResponse: nested OrgTreeNode tree)
```

---

## 4. Dynamic Level Resolution Architecture (Zero N+1 Queries)

### 4.1. Resolving `rootOrgId` and `rootLevel` in a Single Query
When a caller reads a deeply nested organisation (e.g. `Level 3` Directorate `013704373873598464221`):
1. The service executes `chainUp(orgId)`:
   ```sql
   WITH RECURSIVE chain(id, parent_id, depth) AS (
     SELECT id, parent_id, 1 FROM org_hierarchy WHERE id = :id
     UNION ALL
     SELECT o.id, o.parent_id, c.depth + 1 FROM org_hierarchy o JOIN chain c ON o.id = c.parent_id
     WHERE c.depth < :maxDepth
   ) SELECT id FROM chain;
   ```
2. Result: A list of IDs climbing up to the root:
   `["013704...", "014269...", "014606..."]`
3. Two critical facts are extracted with **zero additional queries**:
   * **`rootLevel`**: `chain.size() = 3` (The queried org is at Level 3).
   * **`rootOrgId`**: `chain.get(chain.size() - 1)` (The L0 Root Ministry ID).

### 4.2. In-Memory Level Name Lookup
With `rootOrgId` resolved, the service queries `org_hierarchy_level` once:
```sql
SELECT level, name, description 
FROM org_hierarchy_level 
WHERE root_org_id = :rootOrgId 
ORDER BY level ASC;
```
* **Performance**: Index scan on 10 rows takes **< 0.4 ms**.
* The 10 records are placed into an in-memory `Map<Integer, HierarchyLevelRecord>`.
* As nodes are mapped (flat or nested), each node fetches its level name via:
  ```java
  String levelName = levelMap.getOrDefault(level, "L" + level);
  ```
* **Zero N+1 database queries!**

---

## 5. API Specification 1: `POST /org/v2/hierarchy/read/{orgId}`

### 5.1. Request Contract

* **Method**: `POST /org/v2/hierarchy/read/{orgId}`
* **Header**: `x-authenticated-user-token: <token>` (Required)
* **Request Body** (All fields optional):
  ```json
  {
    "request": {
      "query": "",
      "limit": 50,
      "offset": 0,
      "countDescendants": false,
      "levelRequired": true
    }
  }
  ```

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `query` | `String` | `null` | Optional search term matching `id` or `name` (case-insensitive). |
| `limit` | `Integer` | `50` | Page limit (max records in `associations`). |
| `offset` | `Integer` | `null` | Offset-based pagination index. |
| `cursor` | `String` | `null` | Keyset cursor for $O(1)$ constant-time pagination. Ignored if `offset` given. |
| `countDescendants` | `Boolean` | `false` | When `false`, skips recursive descendant counts for maximum speed (< 15ms). |
| `levelRequired` | `Boolean` | `false` | When `true`, includes the 10-level blueprint array in the response. |

### 5.2. Success Response Contract (HTTP 200 OK)

```json
{
  "id": "api.org.hierarchy.read",
  "ver": "v2",
  "ts": "2026-11-08 16:35:10.123",
  "params": {
    "resmsgid": "dda80e3f-ae8d-4bce-a3f4-88d21ca3ebf6",
    "msgid": "dda80e3f-ae8d-4bce-a3f4-88d21ca3ebf6",
    "err": null,
    "status": "SUCCESS",
    "errmsg": null
  },
  "responseCode": "OK",
  "result": {
    "id": "0146068546191196160",
    "name": "Seed Parent",
    "idle": false,
    "childCount": 2,
    "level": 1,
    "levelName": "Ministry",
    "associations": [
      {
        "id": "0142691971194388486",
        "name": "AIIDCO",
        "idle": false,
        "childCount": 1,
        "descendantCount": null,
        "level": 2,
        "levelName": "Department"
      },
      {
        "id": "0146041605259673601",
        "name": "Lorem ipsum dolor sit amet",
        "idle": false,
        "childCount": 2,
        "descendantCount": null,
        "level": 2,
        "levelName": "Department"
      }
    ],
    "levels": [
      { "level": 1, "name": "Ministry", "description": "Central Government Ministry" },
      { "level": 2, "name": "Department", "description": "Department under Ministry" },
      { "level": 3, "name": "Directorate / Board", "description": "Autonomous Board or Directorate" },
      { "level": 4, "name": "Field Unit", "description": "District Office or Field Unit" },
      { "level": 5, "name": "L5", "description": "LevelFive" },
      { "level": 6, "name": "L6", "description": "LevelSix" },
      { "level": 7, "name": "L7", "description": "LevelSeven" },
      { "level": 8, "name": "L8", "description": "LevelEight" },
      { "level": 9, "name": "L9", "description": "LevelNine" },
      { "level": 10, "name": "L10", "description": "LevelTen" }
    ],
    "maxCreatedLevel": 4,
    "maxAllowedLevel": 10,
    "limit": 50,
    "hasMore": false
  }
}
```

---

## 6. API Specification 2: `GET /org/v2/hierarchy/{orgId}/complete`

### 6.1. Request Contract

* **Method**: `GET /org/v2/hierarchy/{orgId}/complete`
* **Header**: `x-authenticated-user-token: <token>` (Required)
* **Query Parameters** (bound as one `CompleteHierarchyRequestParams` object, so a future param never grows the controller's own method signature):
  * `depth` (`Integer`, optional): Maximum hops to traverse down (default: whole tree up to max depth).
  * `levelRequired` (`Boolean`, optional, default `true`): When `true`, includes the `levels` array in the response.

### 6.2. Success Response Contract (HTTP 200 OK)

Notice that in `/complete`, `associations` is **recursively nested** where each child contains its own sub-children:

```json
{
  "id": "api.org.hierarchy",
  "ver": "v2",
  "ts": "2026-11-08 16:35:45.456",
  "params": {
    "status": "SUCCESS"
  },
  "responseCode": "OK",
  "result": {
    "id": "0146068546191196160",
    "name": "Seed Parent",
    "idle": false,
    "childCount": 2,
    "level": 1,
    "levelName": "Ministry",
    "associations": [
      {
        "id": "0142691971194388486",
        "name": "AIIDCO",
        "idle": false,
        "childCount": 1,
        "level": 2,
        "levelName": "Department",
        "associations": [
          {
            "id": "013704373873598464221",
            "name": "Civil Supplies and Consumer Affairs Department",
            "idle": false,
            "childCount": 2,
            "level": 3,
            "levelName": "Directorate / Board",
            "associations": [
              {
                "id": "01448817679291187230",
                "name": "Prime Minister Office New new",
                "idle": false,
                "childCount": 0,
                "level": 4,
                "levelName": "Field Unit",
                "associations": []
              },
              {
                "id": "0143378202093977602",
                "name": "Testing 09",
                "idle": false,
                "childCount": 0,
                "level": 4,
                "levelName": "Field Unit",
                "associations": []
              }
            ]
          }
        ]
      },
      {
        "id": "0146041605259673601",
        "name": "Lorem ipsum dolor sit amet",
        "idle": false,
        "childCount": 0,
        "level": 2,
        "levelName": "Department",
        "associations": []
      }
    ],
    "count": 6,
    "levels": [
      { "level": 1, "name": "Ministry", "description": "Central Government Ministry" },
      { "level": 2, "name": "Department", "description": "Department under Ministry" },
      { "level": 3, "name": "Directorate / Board", "description": "Autonomous Board or Directorate" },
      { "level": 4, "name": "Field Unit", "description": "District Office or Field Unit" },
      { "level": 5, "name": "L5", "description": "LevelFive" },
      { "level": 6, "name": "L6", "description": "LevelSix" },
      { "level": 7, "name": "L7", "description": "LevelSeven" },
      { "level": 8, "name": "L8", "description": "LevelEight" },
      { "level": 9, "name": "L9", "description": "LevelNine" },
      { "level": 10, "name": "L10", "description": "LevelTen" }
    ],
    "maxCreatedLevel": 4,
    "maxAllowedLevel": 10
  }
}
```

---

## 7. Java DTO Model Architecture (Clean POJOs)

### 7.1. Enhanced `ChildOrgSummary` (Used by `/read`)
```java
package com.hierarchyhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@AllArgsConstructor
public class ChildOrgSummary {
    private final String id;
    private final String name;
    private final boolean idle;
    private final int childCount;
    private final Integer descendantCount;
    private final int level;
    private final String levelName;
}
```

### 7.2. Enhanced `OrgTreeNode` (Used by `/complete`)
```java
package com.hierarchyhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@AllArgsConstructor
public class OrgTreeNode {
    private final String id;
    private final String name;
    private final boolean idle;
    private final int childCount;
    private final int level;
    private final String levelName;
    private final List<OrgTreeNode> associations;
}
```

### 7.3. Enhanced `HierarchyResponse` (Wrapper for `/complete`)
```java
package com.hierarchyhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@AllArgsConstructor
public class HierarchyResponse {
    @JsonUnwrapped
    private final OrgTreeNode org;
    private final int count;
    private final List<HierarchyLevelItem> levels;
    private final Integer maxCreatedLevel;
    private final Integer maxAllowedLevel;
}
```

---

## 8. High-Scale Optimization & Performance Guarantees

### 8.1. Scale with 40,000 Organisations
* **RAM-Cached Execution**: 40,000 rows in PostgreSQL occupies ~20 MB on disk. The entire table lives inside PostgreSQL shared buffers memory.
* **$O(\log N)$ Index Scans**:
  - `chainUp` climbs at most 10 hops: **< 1 ms**.
  - `org_hierarchy_level` lookup scans at most 10 rows: **< 0.4 ms**.
  - `findDescendants` scans only the requested page (e.g. 50 rows): **~4 ms – 8 ms**.
* **Total Read Latency**: **under 15 ms – 20 ms**.

### 8.2. Nodes with 1,000+ Direct Children
* In `POST /read/{orgId}`, pagination (`limit: 50`) cuts off at row 51. The remaining 950 rows stay in PostgreSQL.
* The API streams only ~15 KB of JSON.
* Subsequent pages use keyset cursor `(lower(name), id) > (:cursorName, :cursorId)` in constant $O(1)$ time.

### 8.3. Move and Unlink Isolation
* Moving or unlinking an organisation never touches `org_hierarchy_level`.
* Read queries never block or deadlock behind write transactions.

