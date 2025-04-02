package com.igot.cb.dto;

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
    public static final String REDIS_COMMON_KEY = "CB_ORG_";


    private Constants() {
        throw new IllegalStateException("Utility class");
    }
}
