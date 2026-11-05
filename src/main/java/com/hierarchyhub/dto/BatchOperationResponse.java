package com.hierarchyhub.dto;

/**
 * Result of a batch {@code POST /link} or {@code POST /unlink}: just the one outcome {@link #message} for the whole
 * call, naming every orgId involved - no per-child breakdown. Shared by both calls since their result shape is
 * identical.
 */
public class BatchOperationResponse {

    private final String message;

    public BatchOperationResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
