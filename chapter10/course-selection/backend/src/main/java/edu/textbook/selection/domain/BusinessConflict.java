package edu.textbook.selection.domain;

public class BusinessConflict extends RuntimeException {
    private final String code;

    public BusinessConflict(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
