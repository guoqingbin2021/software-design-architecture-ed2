package com.example.enrollment.domain;

import java.util.Map;

public class BusinessException extends RuntimeException {
    public enum Code {
        VALIDATION_ERROR,
        NOT_FOUND,
        CLASS_FULL,
        IDEMPOTENCY_CONFLICT
    }

    private final Code code;
    private final Map<String, String> fields;

    public BusinessException(Code code, String message) {
        this(code, message, Map.of());
    }

    public BusinessException(Code code, String message, Map<String, String> fields) {
        super(message);
        this.code = code;
        this.fields = Map.copyOf(fields);
    }

    public Code getCode() {
        return code;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public static BusinessException notFound() {
        return new BusinessException(Code.NOT_FOUND, "资源不存在或不在可访问范围内");
    }
}
