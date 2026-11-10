package com.example.enrollment.application;

import com.example.enrollment.domain.BusinessException;
import java.util.Map;

public final class InputPolicy {
    private InputPolicy() {}

    public static long id(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}"))
            throw invalid(field, "须为正整数的十进制字符串");
        try {
            long result = Long.parseLong(value);
            if (result > 9007199254740991L) throw invalid(field, "标识超出JavaScript安全整数范围");
            return result;
        } catch (NumberFormatException ex) {
            throw invalid(field, "标识超出Long有效范围");
        }
    }

    public static String key(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._-]{1,80}"))
            throw invalid("Idempotency-Key", "须为1至80个字母、数字、点、下划线或连字符");
        return key;
    }

    public static String semester(String semester) {
        if (semester == null || !semester.matches("[0-9]{4}(春|秋)"))
            throw invalid("semester", "须为四位年份加春或秋，例如2026秋");
        return semester;
    }

    public static BusinessException invalid(String field, String message) {
        return new BusinessException(
                BusinessException.Code.VALIDATION_ERROR, "输入不符合接口约定", Map.of(field, message));
    }
}
