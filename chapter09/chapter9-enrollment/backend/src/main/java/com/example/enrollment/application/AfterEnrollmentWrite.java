package com.example.enrollment.application;

/** Production implementation is a no-op; tests replace it to fail after SQL writes, before commit. */
@FunctionalInterface
public interface AfterEnrollmentWrite {
    void beforeCommit();
}
