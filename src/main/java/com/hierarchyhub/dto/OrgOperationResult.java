package com.hierarchyhub.dto;

/**
 * Outcome of one org in a batch {@code /link} or {@code /unlink} call: a short, human-readable {@link #message}. One
 * org failing its own rule does not stop the rest of the batch from being attempted.
 */
public class OrgOperationResult {

    private final String identifier;
    private final boolean success;
    private final String message;

    private OrgOperationResult(String identifier, boolean success, String message) {
        this.identifier = identifier;
        this.success = success;
        this.message = message;
    }

    public static OrgOperationResult success(String identifier, String message) {
        return new OrgOperationResult(identifier, true, message);
    }

    public static OrgOperationResult failure(String identifier, String message) {
        return new OrgOperationResult(identifier, false, message);
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
