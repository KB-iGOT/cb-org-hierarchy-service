# Prompt: Draft an API contract document for the org-hierarchy v2 APIs

Use this prompt to ask another agent (or write by hand) an API contract document to share with the UI team, for
six APIs in `cb-org-hierarchy-service`: **link**, **unlink**, **move**, **get complete hierarchy**, **bulk link
(file upload)**, and **read/search a subtree**.

---

## Prompt to give the agent

> Create an API contract document (Markdown) for six REST APIs below, meant for a frontend/UI team integrating
> against them. For each API, document: the HTTP method + URL, required headers, the request (body or multipart
> form fields, with every field, its type, and whether it's required/optional), and the success response body
> (every field, with an example JSON). Also include one 400 Bad Request example per API (validation failure) - do
> not document 401, 404, 409, 500 or any other error status for now. Use a consistent template across all six
> APIs: Endpoint, Headers, Request, Success Response (200), Error Response (400). Base path for all six:
> `/org/v2/hierarchy`.
>
> Here is the verified, current request/response shape for each API - use these exactly, don't infer or guess
> additional fields:

### 1. Link - attach one or more orgs under a parent

- **Method / URL**: `POST /org/v2/hierarchy/link`
- **Header**: `x-authenticated-user-token: <token>` (required on every call below)
- **Request body**:
  ```json
  {
    "request": {
      "parentOrgId": "0142691971194388486",
      "associations": [
        { "childOrgId": "01357983854455193614" },
        { "childOrgId": "01357983854455193615" }
      ]
    }
  }
  ```
  - `request.parentOrgId` (string, required) - the org to attach children under.
  - `request.associations` (array, required, min 1 item) - the children to attach in this call.
  - `request.associations[].childOrgId` (string, required).
- **Success response (200)**:
  ```json
  {
    "id": "api.org.hierarchy.link",
    "ver": "v2",
    "ts": "2026-11-04 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": null,
      "status": "SUCCESS",
      "errmsg": null
    },
    "responseCode": "OK",
    "result": {
      "message": "Successfully added organisation to the hierarchy"
    }
  }
  ```
  - If one or more children in the batch fail their own validation (e.g. already linked elsewhere), `result.message`
    instead reads like: `"Successfully added organisation to the hierarchy; Failed to add organisation to the
    hierarchy for orgId: 013... (reason)"`.
- **Error response (400 - validation failure, e.g. missing `parentOrgId` or empty `associations`)**:
  ```json
  {
    "id": "api.org.v2.hierarchy.link",
    "ver": "v2",
    "ts": "2026-11-04 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": "INVALID_REQUEST",
      "status": "FAILED",
      "errmsg": "request.parentOrgId: parentOrgId is required"
    },
    "responseCode": "BAD_REQUEST"
  }
  ```

### 2. Unlink - detach one org (and, by default, its whole subtree) from the hierarchy

- **Method / URL**: `POST /org/v2/hierarchy/unlink`
- **Header**: `x-authenticated-user-token: <token>`
- **Request body** - single-org, not batch:
  ```json
  {
    "request": {
      "orgId": "0142048098781020169",
      "requireLeaf": false
    }
  }
  ```
  - `request.orgId` (string, required) - the org to detach. Its current parent is looked up on the server; the
    caller does not name it.
  - `request.requireLeaf` (boolean, optional, **default `false`**) - when `false` (or omitted), `orgId` **and every
    descendant of it** are removed from the hierarchy together (each one becomes idle, with no parent - the whole
    branch is taken apart, not just moved out as a unit). Pass `true` to refuse instead whenever the org still has
    children of its own (it must already be a leaf) - a safer, narrower operation.
- **Success response (200)**:
  ```json
  {
    "id": "api.org.hierarchy.unlink",
    "ver": "v2",
    "ts": "2026-11-04 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": null,
      "status": "SUCCESS",
      "errmsg": null
    },
    "responseCode": "OK",
    "result": {
      "message": "Successfully removed organisation from the hierarchy"
    }
  }
  ```
  - `result.message` is always this plain text on success, whether or not a subtree was removed along with `orgId` -
    there is no per-org detail or count in the response. A rule violation (not found, no parent, has children when
    `requireLeaf` is `true`) comes back as an error response instead, not inside a 200.
- **Error response (400 - validation failure, e.g. missing `orgId`)**: same shape as link's 400 example above, with
  `"errmsg": "request.orgId: orgId is required"`.
- **Error response (409 - has children, `requireLeaf: true`)**: not documented here (ask was 400/200 only), but flag
  to the UI team that this is a real outcome whenever they opt into the safer, leaf-only mode: `err: "NOT_A_LEAF"`,
  HTTP 409.

### 3. Move - move an org (and its branch) to a new parent

- **Method / URL**: `POST /org/v2/hierarchy/move`
- **Header**: `x-authenticated-user-token: <token>`
- **Request body**:
  ```json
  {
    "request": {
      "orgId": "0143378202093977602",
      "newParentOrgId": "013704373873598464221"
    }
  }
  ```
  - `request.orgId` (string, required) - the org being moved.
  - `request.newParentOrgId` (string, required) - its new parent.
- **Success response (200)**:
  ```json
  {
    "id": "api.org.hierarchy.move",
    "ver": "v2",
    "ts": "2026-11-04 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": null,
      "status": "SUCCESS",
      "errmsg": null
    },
    "responseCode": "OK",
    "result": {
      "orgId": "0143378202093977602",
      "oldParentId": "0140...",
      "newParentId": "013704373873598464221",
      "changed": true,
      "message": "Successfully moved organisation to the hierarchy"
    }
  }
  ```
  - If the org is already under `newParentOrgId`, this is a no-op: `changed` is `false` and `message` is
    `"Organisation is already linked under this parent"`.
- **Error response (400 - validation failure)**: same shape as link's, with `"errmsg"` naming the missing/invalid
  field (e.g. `"request.orgId: orgId is required"`).

### 4. Get complete hierarchy - an org's whole subtree

- **Method / URL**: `GET /org/v2/hierarchy/{orgId}/complete`
  - `orgId` (path param, required) - the root of the subtree to return.
  - `depth` (query param, optional integer) - how many levels below `orgId` to include; omit for the whole subtree.
  - Example: `GET /org/v2/hierarchy/0142691971194388486/complete?depth=2`
- **Header**: `x-authenticated-user-token: <token>`
- **Request body**: none (GET request).
- **Success response (200)**:
  ```json
  {
    "id": "api.org.hierarchy.hierarchy",
    "ver": "v2",
    "ts": "2026-11-04 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": null,
      "status": "SUCCESS",
      "errmsg": null
    },
    "responseCode": "OK",
    "result": {
      "id": "0142691971194388486",
      "name": "Ministry of Example",
      "idle": false,
      "childCount": 2,
      "level": 1,
      "associations": [
        {
          "id": "01357983854455193614",
          "name": "Department A",
          "idle": false,
          "childCount": 0,
          "level": 2,
          "associations": []
        }
      ],
      "count": 2
    }
  }
  ```
  - `result.id`/`name`/`idle`/`childCount`/`associations` describe `orgId` itself; `associations` nests the same
    shape recursively for every descendant (cut off at `depth` levels below the root, if given).
  - `result.level`/each descendant's own `level` is that org's **absolute** position in the whole hierarchy (an L0
    root is level `1`), not relative to this call's `orgId` - calling `/complete` on an org nested three levels
    down still reports its real level (e.g. `4`), not `1`. Computed at read time on every call, not stored.
  - `result.count` is the total number of nodes in the subtree, `orgId` itself included.
- **Error response (400 - validation failure, e.g. `depth` is not a valid integer)**:
  ```json
  {
    "id": "api.org.v2.hierarchy.0142691971194388486.complete",
    "ver": "v2",
    "ts": "2026-11-04 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": "INVALID_REQUEST",
      "status": "FAILED",
      "errmsg": "Failed to convert value of type 'java.lang.String' to required type 'java.lang.Integer'; for input string: \"abc\""
    },
    "responseCode": "BAD_REQUEST"
  }
  ```

### 5. Bulk link - attach many orgs from an uploaded Excel file

- **Method / URL**: `POST /org/v2/hierarchy/bulk/link/{rootOrgId}`
  - `rootOrgId` (path param, required) - caller-supplied context/audit id for this upload; echoed back on the
    response, but does **not** itself constrain which parent each row attaches under (each row's own `parentOrgId`
    column does that - see below).
  - Example: `POST /org/v2/hierarchy/bulk/link/013834790802178048348`
- **Header**: `x-authenticated-user-token: <token>`
- **Request**: `multipart/form-data`, one form field:
  - `file` (file, required) - an `.xlsx` workbook with a sheet named exactly `referenceSheet` (matched
    case-insensitively), with header columns `parentOrgId` and `childOrgId`. Each data cell is expected as
    `"<display name><orgId>"` (e.g. `"Delhi Government<01425456149399142419>"`) - the id inside the last `<...>` is
    what gets used; a cell with no `<...>` at all is used as-is. `childOrgId` must be distinct per row (first
    occurrence wins, later duplicate rows are silently skipped); `parentOrgId` may repeat across rows freely. At
    most 5000 data rows per file (configurable; larger files are rejected with 400, not queued).
- **Success response (200)** - the file is accepted and queued; it is **not** linked yet:
  ```json
  {
    "id": "api.org.hierarchy.bulk.link",
    "ver": "v2",
    "ts": "2026-11-04 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": null,
      "status": "SUCCESS",
      "errmsg": null
    },
    "responseCode": "OK",
    "result": {
      "fileName": "orgHierarchyBulkMasterData.xlsx",
      "createdBy": "ceec02c6-c191-49fe-8456-66245a9a7835",
      "dateCreatedOn": "2026-11-04T09:28:58.512+00:00",
      "rootOrgId": "013834790802178048348",
      "status": "INITIATED"
    }
  }
  ```
  - `status` is always `"INITIATED"` on this response - every row is then attached one at a time in the background,
    the same rules as `/link` applied per row (ministry/state check, idempotency, cycle prevention). **There is no
    status/poll endpoint**: the outcome (rows succeeded/failed, and why) is only written to the server log, not
    returned to the caller anywhere. Flag this to the UI team explicitly - if they need to show upload progress or
    a result summary in the product, that is a gap to raise, not something already available to call.
- **Error response (400 - validation failure, e.g. no file, wrong sheet name, missing columns, or too many rows)**:
  ```json
  {
    "id": "api.org.v2.hierarchy.bulk.link.013834790802178048348",
    "ver": "v2",
    "ts": "2026-11-04 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": "INVALID_REQUEST",
      "status": "FAILED",
      "errmsg": "Sheet 'referenceSheet' not found in the uploaded file"
    },
    "responseCode": "BAD_REQUEST"
  }
  ```

### 6. Read - one page of an org's descendants, with optional search

- **Method / URL**: `POST /org/v2/hierarchy/read/{orgId}`
  - `orgId` (path param, required) - the org whose descendants to read/search; the search (if any) is always scoped
    to this org's own subtree, never outside it.
- **Header**: `x-authenticated-user-token: <token>`
- **Request body** - entirely optional; a missing/empty body behaves like every field below being omitted:
  ```json
  {
    "request": {
      "query": "delhi",
      "depth": 3,
      "limit": 20,
      "cursor": null,
      "offset": 0,
      "countDescendants": true
    }
  }
  ```
  - `request.query` (string, optional) - matches a descendant whose id exactly equals this value, or whose name
    contains it (case-insensitive); both are tried, the caller does not say which one it is.
  - `request.depth` (integer, optional, min `1`) - how many levels below `orgId` to include, capped at this
    service's configured max depth. If omitted: a plain read (no `query`) returns **direct children only**
    (depth 1); a search (`query` given) reaches the **whole subtree** instead, since it has to, to find a match
    anywhere under `orgId`.
  - `request.limit` (integer, optional, default `50`) - page size.
  - `request.cursor` (string, optional) - opaque keyset cursor from a previous page's `nextCursor`; omit for the
    first page. Ignored when `offset` is given.
  - `request.offset` (integer, optional, min `0`) - classic offset paging, an alternative to `cursor`. When given
    (even `0`), it wins over `cursor` for that call. Prefer `cursor` once a listing is large or long-lived - `offset`
    gets slower the further it skips and can skip/repeat a row if the hierarchy changes between calls.
  - `request.countDescendants` (boolean, optional, default `true`) - whether to compute each result's own total
    descendant count (a second count per row; costs an extra query per row when `true`).
- **Success response (200)**:
  ```json
  {
    "id": "api.org.hierarchy.read",
    "ver": "v2",
    "ts": "2026-11-05 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": null,
      "status": "SUCCESS",
      "errmsg": null
    },
    "responseCode": "OK",
    "result": {
      "id": "0142691971194388486",
      "name": "Ministry of Example",
      "idle": false,
      "childCount": 2,
      "level": 1,
      "associations": [
        {
          "id": "0142691971194388490",
          "name": "AIIDCO",
          "idle": false,
          "childCount": 1,
          "descendantCount": 3,
          "level": 2
        }
      ],
      "limit": 20,
      "hasMore": true,
      "nextCursor": "<opaque>"
    }
  }
  ```
  - `result.id`/`name`/`idle`/`childCount` describe `orgId` itself (same field names as "get complete hierarchy").
  - `result.level` is `orgId`'s own **absolute** level in the whole hierarchy (an L0 root is level `1`) - calling
    `/read` on a deeply-nested org still reports its real level, not `1`.
  - `result.associations` is always a **flat** list of matches (never nested, unlike "get complete hierarchy") - each
    entry is its own `id`/`name`/`idle`/`childCount`/`level`, plus `descendantCount` when `countDescendants` was true.
    Each entry's `level` is that specific descendant's own absolute level, which can vary within the same flat list
    when `depth > 1` or `query` reaches beyond direct children - it is not always `result.level + 1`.
  - `result.nextCursor` is present only when `hasMore` is true **and** paging was by `cursor` (omitted/`null` when
    paging by `offset`, or when there is no next page).
- **Error response (400 - validation failure, e.g. `depth: 0` or `offset: -1`)**:
  ```json
  {
    "id": "api.org.v2.hierarchy.read.0142691971194388486",
    "ver": "v2",
    "ts": "2026-11-05 10:15:03.123",
    "params": {
      "resmsgid": "<uuid>",
      "msgid": "<uuid>",
      "err": "INVALID_REQUEST",
      "status": "FAILED",
      "errmsg": "request.depth: depth must be at least 1"
    },
    "responseCode": "BAD_REQUEST"
  }
  ```

---

## Notes for whoever runs this prompt

- All six endpoints require the `x-authenticated-user-token` header; a missing/invalid token returns 401, which
  this contract deliberately excludes (ask was 400 and success only).
- `ver` in the envelope always reflects the real API version segment in the URL (`v2` here) - not hardcoded.
- 500/other error statuses were explicitly excluded from this round of documentation per the request; add them
  later as a separate pass if the UI team needs them.
- Bulk link (#5) is fire-and-forget: its 200 response only confirms the file was accepted, not that any row was
  actually linked. Call this out clearly in the doc so the UI team doesn't assume `status: INITIATED` ever changes
  to something they can observe via the API.
- Read (#6) is the only one of the six that is not a write and not single-purpose - it's both "list children" and
  "search this subtree" depending on which fields are sent. Make sure the doc is clear that `query` searches the
  whole subtree regardless of `depth` (they're independent controls), and that unlink (#2) now cascades by default -
  these two are easy to misdescribe if skimmed instead of read closely.
- `level` (added to #4 and #6) is never stored - it's computed fresh on every call from the org's current parent
  chain, so it's always consistent with the live hierarchy and needs no changes to link/unlink/move. Don't document
  it as a stored/indexed field.
