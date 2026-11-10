package com.example.enrollment.interfaces.http;

import com.example.enrollment.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException ex, HttpServletRequest req) {
        int status =
                switch (ex.getCode()) {
                    case VALIDATION_ERROR -> 400;
                    case NOT_FOUND -> 404;
                    case CLASS_FULL, IDEMPOTENCY_CONFLICT -> 409;
                };
        return error(status, ex.getCode().name(), ex.getMessage(), ex.getFields(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        return error(400, "VALIDATION_ERROR", "输入不符合接口约定", fields, req);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiError> malformed(Exception ex, HttpServletRequest req) {
        String field = "body";
        if (ex instanceof MissingServletRequestParameterException e) field = e.getParameterName();
        if (ex instanceof MissingRequestHeaderException e) field = e.getHeaderName();
        return error(
                400, "VALIDATION_ERROR", "请求字段、类型或格式不符合约定", Map.of(field, "检查必填项、未知字段及字符串类型"), req);
    }

    @ExceptionHandler({
        org.springframework.web.HttpRequestMethodNotSupportedException.class,
        org.springframework.web.HttpMediaTypeNotSupportedException.class,
        org.springframework.web.servlet.resource.NoResourceFoundException.class
    })
    public ResponseEntity<ApiError> httpContract(Exception ex, HttpServletRequest req) {
        var http = (org.springframework.web.ErrorResponse) ex;
        String code = http.getStatusCode().value() == 404 ? "NOT_FOUND" : "VALIDATION_ERROR";
        return ResponseEntity.status(http.getStatusCode())
                .headers(http.getHeaders())
                .cacheControl(CacheControl.noStore())
                .body(
                        new ApiError(
                                code,
                                "请求路径、HTTP方法或内容类型不符合接口约定",
                                Map.of(),
                                RequestIdFilter.id(req)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        log.error("requestId={} request failed", RequestIdFilter.id(req), ex);
        return error(500, "INTERNAL_ERROR", "服务器未能完成请求；写入结果不明时请按原请求键核对", Map.of(), req);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(Exception ex, HttpServletRequest req) {
        return error(403, "FORBIDDEN", "当前身份没有该操作权限", Map.of(), req);
    }

    private ResponseEntity<ApiError> error(
            int status,
            String code,
            String message,
            Map<String, String> fields,
            HttpServletRequest req) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(code, message, fields, RequestIdFilter.id(req)));
    }
}
