package edu.textbook.selection.api;

import edu.textbook.selection.domain.BusinessConflict;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private ResponseEntity<HttpErrorWriter.ErrorBody> error(
            HttpServletRequest request, int status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new HttpErrorWriter.ErrorBody(code, message, RequestIdFilter.id(request)));
    }

    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<?> application(ApiFailure ex, HttpServletRequest request) {
        return error(request, ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BusinessConflict.class)
    ResponseEntity<?> conflict(BusinessConflict ex, HttpServletRequest request) {
        return error(request, 409, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<?> validation(Exception ex, HttpServletRequest request) {
        return error(request, 400, "VALIDATION_ERROR", "请求字段、幂等键或成绩格式无效，请核对接口约定");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> missing(NoResourceFoundException ex, HttpServletRequest request) {
        return error(request, 404, "NOT_FOUND", "请求的对象不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(405)
                .headers(ex.getHeaders())
                .body(
                        new HttpErrorWriter.ErrorBody(
                                "METHOD_NOT_ALLOWED",
                                "此接口不支持该HTTP方法",
                                RequestIdFilter.id(request)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<?> media(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(415)
                .headers(ex.getHeaders())
                .body(
                        new HttpErrorWriter.ErrorBody(
                                "UNSUPPORTED_MEDIA_TYPE",
                                "此接口需要application/json请求体",
                                RequestIdFilter.id(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex, HttpServletRequest request) {
        LOG.error("Request failed, requestId={}", RequestIdFilter.id(request), ex);
        return error(request, 500, "INTERNAL_ERROR", "操作未能完成，请使用原幂等键重试或联系教师");
    }
}
