# Org Hierarchy API Gateway & UI Proxy Integration Specification

> **Target Services:**
> 1. `sunbird-cb-uiproxy` (`/home/adarshmalavade/Desktop/repo/sunbird-cb-uiproxy`)
> 2. `sunbird-devops` (`/home/adarshmalavade/Desktop/repo/sunbird-devops`)
>
> **Target Microservice:** `cb-org-hierarchy-service` (`/home/adarshmalavade/Desktop/repo/cb-org-hierarchy-service`)  
> **Service Port:** `8888`  
> **Base Context Path:** `/org/v2/hierarchy`

---

## 1. Executive Summary & Memory Context

The `cb-org-hierarchy-service` manages PostgreSQL-backed organisation hierarchy trees, parent path lineage updates, level configurations, and bulk linking operations. Seven specific endpoints from `OrgHierarchyController` must now be exposed securely to frontend clients through the Sunbird reverse-proxy architecture:

```
[Browser / Frontend Client]
          │
          ▼  (HTTPS /proxies/v8/...)
┌──────────────────────────────────────────────┐
│             sunbird-cb-uiproxy               │
│  - Keycloak session authentication           │
│  - Whitelist validation (apiWhiteList.ts)    │
│  - Role verification (ROLE_CHECK)            │
│  - Multipart upload streaming                │
│  - Header injection (auth token, userid, org)│
└──────────────────────┬───────────────────────┘
                       │
                       ▼  (HTTP /org/v2/hierarchy/...)
┌──────────────────────────────────────────────┐
│           Kong API Gateway (devops)          │
│  - Route matching & URI stripping            │
│  - JWT consumer authentication               │
│  - Rate limiting & request size limiting     │
│  - Statsd metrics & CORS                     │
└──────────────────────┬───────────────────────┘
                       │
                       ▼  (HTTP :8888/org/v2/hierarchy/...)
┌──────────────────────────────────────────────┐
│          cb-org-hierarchy-service            │
│  - PostgreSQL Hierarchy Repository           │
│  - Cassandra Org Master & Hierarchy Sync     │
│  - Kafka Hierarchy Event Streaming           │
└──────────────────────────────────────────────┘
```

### 1.1 Strict Endpoint Scope & Access Control Matrix

Only the following **7 endpoints** are to be exposed through `sunbird-cb-uiproxy` and `sunbird-devops`. All other internal/admin endpoints (`/org/v2/hierarchy/admin/*`, `GET /org/v2/hierarchy/levels/{rootOrgId}`, `/org/v1/*`) remain unexposed and blocked at the UI Proxy whitelist:

| HTTP Method | UI Proxy Path (`/proxies/v8`) | Kong URI (`/org`) | Backend Controller Mapping | Allowed Roles | Request Payload / Content-Type |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **GET** | `/proxies/v8/org/v2/hierarchy/:orgId/complete` | `/org/v2/hierarchy/:orgId/complete` | `@GetMapping("/{orgId}/complete")` | `PUBLIC` | Query params (`depth`, `levelRequired`) |
| **POST** | `/proxies/v8/org/v2/hierarchy/read/:orgId` | `/org/v2/hierarchy/read/:orgId` | `@PostMapping("/read/{orgId}")` | `PUBLIC` | `application/json` (`ReadChildrenRequest`) |
| **POST** | `/proxies/v8/org/v2/hierarchy/link` | `/org/v2/hierarchy/link` | `@PostMapping("/link")` | `STATE_ADMIN`, `SPV_ADMIN`, `MDO_LEADER`, `MDO_ADMIN` | `application/json` (`LinkOrgRequest`) |
| **POST** | `/proxies/v8/org/v2/hierarchy/unlink` | `/org/v2/hierarchy/unlink` | `@PostMapping("/unlink")` | `STATE_ADMIN`, `SPV_ADMIN`, `MDO_LEADER`, `MDO_ADMIN` | `application/json` (`UnlinkOrgRequest`) |
| **POST** | `/proxies/v8/org/v2/hierarchy/move` | `/org/v2/hierarchy/move` | `@PostMapping("/move")` | `STATE_ADMIN`, `SPV_ADMIN`, `MDO_LEADER`, `MDO_ADMIN` | `application/json` (`MoveOrgRequest`) |
| **POST** | `/proxies/v8/org/v2/hierarchy/bulk/link/:rootOrgId` | `/org/v2/hierarchy/bulk/link/:rootOrgId` | `@PostMapping(value = "/bulk/link/{rootOrgId}")` | `STATE_ADMIN`, `SPV_ADMIN`, `MDO_LEADER`, `MDO_ADMIN` | `multipart/form-data` (file: .xlsx/.csv) |
| **PATCH** | `/proxies/v8/org/v2/hierarchy/levels/:rootOrgId` | `/org/v2/hierarchy/levels/:rootOrgId` | `@PatchMapping("/levels/{rootOrgId}")` | `STATE_ADMIN`, `SPV_ADMIN`, `MDO_LEADER`, `MDO_ADMIN` | `application/json` (`UpdateHierarchyLevelsRequest`) |

> [!IMPORTANT]
> Role restrictions for `PUBLIC` vs admin roles (`STATE_ADMIN,SPV_ADMIN,MDO_LEADER,MDO_ADMIN`) are enforced **strictly at `sunbird-cb-uiproxy`**. No role changes or code changes are to be made inside `cb-org-hierarchy-service`.

---

## 2. Changes Required in `sunbird-cb-uiproxy`

### 2.1 File: `src/utils/whitelistApis.ts`

Each endpoint must be registered in both `API_LIST.URL` (to specify validation rules and role checks) and `API_LIST.URL_PATTERN` (for regex route compilation).

#### Additions to `API_LIST.URL`:

```typescript
        // --- Org Hierarchy v2 Public APIs ---
        '/proxies/v8/org/v2/hierarchy/:orgId/complete': {
            checksNeeded: [CHECK.ROLE],
            ROLE_CHECK: [
                ROLE.PUBLIC,
            ],
        },
        '/proxies/v8/org/v2/hierarchy/read/:orgId': {
            checksNeeded: [CHECK.ROLE],
            ROLE_CHECK: [
                ROLE.PUBLIC,
            ],
        },

        // --- Org Hierarchy v2 Admin/Leader APIs ---
        '/proxies/v8/org/v2/hierarchy/link': {
            checksNeeded: [CHECK.ROLE],
            ROLE_CHECK: [
                ROLE.STATE_ADMIN,
                ROLE.SPV_ADMIN,
                ROLE.MDO_LEADER,
                ROLE.MDO_ADMIN,
            ],
        },
        '/proxies/v8/org/v2/hierarchy/unlink': {
            checksNeeded: [CHECK.ROLE],
            ROLE_CHECK: [
                ROLE.STATE_ADMIN,
                ROLE.SPV_ADMIN,
                ROLE.MDO_LEADER,
                ROLE.MDO_ADMIN,
            ],
        },
        '/proxies/v8/org/v2/hierarchy/move': {
            checksNeeded: [CHECK.ROLE],
            ROLE_CHECK: [
                ROLE.STATE_ADMIN,
                ROLE.SPV_ADMIN,
                ROLE.MDO_LEADER,
                ROLE.MDO_ADMIN,
            ],
        },
        '/proxies/v8/org/v2/hierarchy/bulk/link/:rootOrgId': {
            checksNeeded: [CHECK.ROLE],
            ROLE_CHECK: [
                ROLE.STATE_ADMIN,
                ROLE.SPV_ADMIN,
                ROLE.MDO_LEADER,
                ROLE.MDO_ADMIN,
            ],
        },
        '/proxies/v8/org/v2/hierarchy/levels/:rootOrgId': {
            checksNeeded: [CHECK.ROLE],
            ROLE_CHECK: [
                ROLE.STATE_ADMIN,
                ROLE.SPV_ADMIN,
                ROLE.MDO_LEADER,
                ROLE.MDO_ADMIN,
            ],
        },
```

#### Additions to `API_LIST.URL_PATTERN`:

```typescript
            '/proxies/v8/org/v2/hierarchy/:orgId/complete',
            '/proxies/v8/org/v2/hierarchy/read/:orgId',
            '/proxies/v8/org/v2/hierarchy/link',
            '/proxies/v8/org/v2/hierarchy/unlink',
            '/proxies/v8/org/v2/hierarchy/move',
            '/proxies/v8/org/v2/hierarchy/bulk/link/:rootOrgId',
            '/proxies/v8/org/v2/hierarchy/levels/:rootOrgId',
```

### 2.2 File: `src/proxies_v8/proxies_v8.ts`

1. **Standard Proxying Verification:**
   Line 593 already binds `/org/*`:
   ```typescript
   proxiesV8.use('/org/*',
     proxyCreatorSunbird(express.Router(), `${CONSTANTS.KONG_API_BASE}`)
   )
   ```
   For JSON endpoints (`link`, `unlink`, `move`, `complete`, `read`, `levels`), `proxyCreatorSunbird` strips `/proxies/v8`, attaches standard Sunbird headers (including `x-authenticated-user-token` which `cb-org-hierarchy-service` expects), and forwards the request to Kong at `${CONSTANTS.KONG_API_BASE}/org/v2/hierarchy/...`.

2. **Multipart File Upload Interception for Bulk Link:**
   Line 602 contains the array of paths intercepted for multipart form-data handling. The bulk link route pattern `/org/v2/hierarchy/bulk/link/*` must be added to this array so that the uploaded Excel file under form-data key `file` is streamed directly to Kong:
   ```typescript
   proxiesV8.post([
     '/user/v1/bulkupload',
     // ... existing upload paths ...
     '/org/v2/hierarchy/bulk/link/*',
     // ... remaining paths ...
   ], (req, res) => {
     // Handles multipart extraction and submits to Kong
   })
   ```

---

## 3. Changes Required in `sunbird-devops` (Kong API Gateway)

### 3.1 File: `ansible/roles/kong-api/defaults/main.yml`

#### Step 1: Upstream Service URL Definition
Add `cb_org_hierarchy_service_url` under the service URLs section (approx line 271):
```yaml
cb_org_hierarchy_service_url: "http://cb-org-hierarchy-service:8888"
```

#### Step 2: Kong API Definitions in `kong_apis` list

Add the granular route configurations:

```yaml
  # ---------------------------------------------------------------------------
  # Org Hierarchy Service APIs (cb-org-hierarchy-service)
  # ---------------------------------------------------------------------------

  - name: orgHierarchyLink
    uris: "{{ org_service_prefix }}/v2/hierarchy/link"
    upstream_url: "{{ cb_org_hierarchy_service_url }}/org/v2/hierarchy/link"
    strip_uri: true
    plugins:
      - name: jwt
      - name: cors
      - "{{ statsd_pulgin }}"
      - name: rate-limiting
        config.policy: local
        config.hour: "{{ medium_rate_limit_per_hour }}"
        config.limit_by: credential
      - name: request-size-limiting
        config.allowed_payload_size: "{{ small_request_size_limit }}"

  - name: orgHierarchyUnlink
    uris: "{{ org_service_prefix }}/v2/hierarchy/unlink"
    upstream_url: "{{ cb_org_hierarchy_service_url }}/org/v2/hierarchy/unlink"
    strip_uri: true
    plugins:
      - name: jwt
      - name: cors
      - "{{ statsd_pulgin }}"
      - name: rate-limiting
        config.policy: local
        config.hour: "{{ medium_rate_limit_per_hour }}"
        config.limit_by: credential
      - name: request-size-limiting
        config.allowed_payload_size: "{{ small_request_size_limit }}"

  - name: orgHierarchyMove
    uris: "{{ org_service_prefix }}/v2/hierarchy/move"
    upstream_url: "{{ cb_org_hierarchy_service_url }}/org/v2/hierarchy/move"
    strip_uri: true
    plugins:
      - name: jwt
      - name: cors
      - "{{ statsd_pulgin }}"
      - name: rate-limiting
        config.policy: local
        config.hour: "{{ medium_rate_limit_per_hour }}"
        config.limit_by: credential
      - name: request-size-limiting
        config.allowed_payload_size: "{{ small_request_size_limit }}"

  - name: orgHierarchyRead
    uris: "{{ org_service_prefix }}/v2/hierarchy/read"
    upstream_url: "{{ cb_org_hierarchy_service_url }}/org/v2/hierarchy/read"
    strip_uri: true
    plugins:
      - name: jwt
      - name: cors
      - "{{ statsd_pulgin }}"
      - name: rate-limiting
        config.policy: local
        config.hour: "{{ medium_rate_limit_per_hour }}"
        config.limit_by: credential
      - name: request-size-limiting
        config.allowed_payload_size: "{{ small_request_size_limit }}"

  - name: orgHierarchyBulkLink
    uris: "{{ org_service_prefix }}/v2/hierarchy/bulk/link"
    upstream_url: "{{ cb_org_hierarchy_service_url }}/org/v2/hierarchy/bulk/link"
    strip_uri: true
    plugins:
      - name: jwt
      - name: cors
      - "{{ statsd_pulgin }}"
      - name: rate-limiting
        config.policy: local
        config.hour: "{{ medium_rate_limit_per_hour }}"
        config.limit_by: credential
      - name: request-size-limiting
        config.allowed_payload_size: "{{ medium_request_size_limit }}"

  - name: orgHierarchyLevels
    uris: "{{ org_service_prefix }}/v2/hierarchy/levels"
    upstream_url: "{{ cb_org_hierarchy_service_url }}/org/v2/hierarchy/levels"
    strip_uri: true
    plugins:
      - name: jwt
      - name: cors
      - "{{ statsd_pulgin }}"
      - name: rate-limiting
        config.policy: local
        config.hour: "{{ medium_rate_limit_per_hour }}"
        config.limit_by: credential
      - name: request-size-limiting
        config.allowed_payload_size: "{{ small_request_size_limit }}"

  - name: orgHierarchyComplete
    uris: "{{ org_service_prefix }}/v2/hierarchy"
    upstream_url: "{{ cb_org_hierarchy_service_url }}/org/v2/hierarchy"
    strip_uri: true
    plugins:
      - name: jwt
      - name: cors
      - "{{ statsd_pulgin }}"
      - name: rate-limiting
        config.policy: local
        config.hour: "{{ medium_rate_limit_per_hour }}"
        config.limit_by: credential
      - name: request-size-limiting
        config.allowed_payload_size: "{{ small_request_size_limit }}"
```

> [!NOTE]
> 1. In Kong 0.14.x, URI prefix matching uses longest-prefix evaluation. Specific sub-paths (`/org/v2/hierarchy/link`, `/org/v2/hierarchy/read`, `/org/v2/hierarchy/bulk/link`, `/org/v2/hierarchy/levels`) match their exact granular rules first.
> 2. Calls to `/org/v2/hierarchy/{orgId}/complete` match the base prefix `{{ org_service_prefix }}/v2/hierarchy`, stripping the matched prefix and forwarding `/{orgId}/complete` with query parameters to `cb_org_hierarchy_service_url`.
> 3. Bulk Link uses `{{ medium_request_size_limit }}` (10MB), aligning with `spring.servlet.multipart.max-file-size=10MB`.

---

## 4. Header Alignment Across Proxies

The `cb-org-hierarchy-service` expects:
```java
@RequestHeader(Constants.AUTH_TOKEN_HEADER) String accessToken
```
where `Constants.AUTH_TOKEN_HEADER = "x-authenticated-user-token"`.

In `sunbird-cb-uiproxy`:
- `proxyHeaders` middleware extracts the Keycloak access token from session (`extractUserToken(req)`) and injects:
  ```javascript
  req.headers['x-authenticated-user-token'] = extractUserToken(req)
  ```
- The multipart form-data handler in `proxies_v8.ts` (lines 633 & 708) also injects:
  ```javascript
  'x-authenticated-user-token': extractUserToken(req) || ''
  ```
- `Authorization: Bearer <SB_API_KEY>` is injected by `proxyHeaders` to satisfy Kong's `jwt` plugin.

Both headers pass transparently to `cb-org-hierarchy-service`, ensuring token authentication functions seamlessly.

---

## 5. Unit Testing Strategy (`sunbird-cb-uiproxy`)

Following Sunbird UI Proxy's Jest testing patterns (`tests/unit/utils/apiWhiteList.test.ts`), test cases should verify:

1. **Public Endpoint Tests:**
   - A request to `/proxies/v8/org/v2/hierarchy/dopt/complete` with `userRoles: ['PUBLIC']` is granted access (`next()` called).
   - A request to `/proxies/v8/org/v2/hierarchy/read/dopt` with `userRoles: ['PUBLIC']` is granted access (`next()` called).
   - An unauthenticated request or a session without roles returns `419` / `403`.

2. **Admin/Leader Restricted Endpoint Tests:**
   - A request to `/proxies/v8/org/v2/hierarchy/link` with `userRoles: ['PUBLIC']` receives **403 Forbidden**.
   - A request to `/proxies/v8/org/v2/hierarchy/link` with `userRoles: ['STATE_ADMIN']` passes (`next()` called).
   - A request to `/proxies/v8/org/v2/hierarchy/unlink` with `userRoles: ['MDO_ADMIN']` passes (`next()` called).
   - A request to `/proxies/v8/org/v2/hierarchy/move` with `userRoles: ['SPV_ADMIN']` passes (`next()` called).
   - A request to `/proxies/v8/org/v2/hierarchy/bulk/link/dopt` with `userRoles: ['MDO_LEADER']` passes (`next()` called).
   - A request to `/proxies/v8/org/v2/hierarchy/levels/dopt` with `userRoles: ['STATE_ADMIN']` passes (`next()` called).
   - A request to any unexposed endpoint (e.g. `/proxies/v8/org/v2/hierarchy/admin/parent-path/dopt`) is rejected with **403 Forbidden** (not whitelisted).

---

## 6. Verification and Readiness Checklist

- [x] Memory confirmed and reviewed for all 7 APIs in `cb-org-hierarchy-service`.
- [x] No code changes made to `cb-org-hierarchy-service`.
- [x] Role mappings defined strictly in `sunbird-cb-uiproxy`.
- [x] Kong upstream definitions and granular routes specified for `sunbird-devops`.
- [x] Multipart handling for `bulk/link` addressed.
- [x] Ready to apply changes to `sunbird-cb-uiproxy` and `sunbird-devops` upon confirmation.

