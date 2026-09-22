package com.tickethub.common.exception;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> properties;

    public ApiException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of());
    }

    public ApiException(ErrorCode errorCode, String detail, Map<String, Object> properties) {
        super(detail);
        this.errorCode = errorCode;
        this.properties = properties == null ? Map.of() : properties;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Map<String, Object> getProperties() { return properties; }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }
}
