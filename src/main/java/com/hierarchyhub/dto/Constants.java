package com.hierarchyhub.dto;

public class Constants {
    public static final String CHILDREN = "children";
    public static final String PARENT_OF = "parent_of";
    public static final String PARENT = "parent";
    public static final String ADDITIONAL_PROPERTIES_DOT = "additionalProperties.";
    public static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    public static final String ID = "id";
    public static final String PARENT_ORG_ID   = "parentOrgId";
    public static final String ORGANIZATIONS   = "organizations";
    public static final String SUCCESSFUL = "Successful";
    public static final String FAILED = "Failed";
    public static final String MESSAGE = "message";
    public static final String RESPONSE = "response";
    public static final String SUCCESS = "success";

    // org-hierarchy v2 (link/move/unlink/read/hierarchy) response envelope api ids
    public static final String API_LINK = "api.org.hierarchy.link";
    public static final String API_MOVE = "api.org.hierarchy.move";
    public static final String API_UNLINK = "api.org.hierarchy.unlink";
    public static final String API_READ = "api.org.hierarchy.read";
    public static final String API_HIERARCHY = "api.org.hierarchy";
    public static final String API_BULK_LINK = "api.org.hierarchy.bulk.link";

    // /bulk/link sheet: a fixed sheet name and column headers, both matched case-insensitively
    public static final String BULK_LINK_SHEET_NAME = "referenceSheet";
    public static final String BULK_LINK_COLUMN_PARENT = "parentOrgId";
    public static final String BULK_LINK_COLUMN_CHILD = "childOrgId";
    // /bulk/link always responds at submission time, before any row is processed - this is the only status it ever reports
    public static final String BULK_LINK_STATUS_INITIATED = "INITIATED";

    // response envelope status used by the ApiResponse.success/failure factories (uppercase, distinct from the
    // pre-existing lowercase SUCCESS/FAILED above so no existing caller's behaviour changes)
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    // the caller's signed access token, verified in-service rather than trusted from an upstream-resolved header
    public static final String AUTH_TOKEN_HEADER = "x-authenticated-user-token";

    // one consistent message per outcome, instead of a different phrasing per code path that reaches the same outcome
    public static final String MSG_ORG_ADDED_TO_HIERARCHY = "Successfully added organisation to the hierarchy";
    public static final String MSG_ORG_REMOVED_FROM_HIERARCHY = "Successfully removed organisation from the hierarchy";
    public static final String MSG_ORG_MOVED_IN_HIERARCHY = "Successfully moved organisation to the hierarchy";

    private Constants() {
        throw new IllegalStateException("Utility class");
    }
}
