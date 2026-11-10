package edu.textbook.selection.api;

/** Application-level access and lookup failure; domain conflicts have their own type. */
public class ApiFailure extends RuntimeException {
    private final int status;
    private final String code;

    public ApiFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiFailure notFound() {
        return new ApiFailure(404, "NOT_FOUND", "请求的对象不存在");
    }

    public static ApiFailure forbidden() {
        return new ApiFailure(403, "FORBIDDEN", "无权执行此操作");
    }
}
