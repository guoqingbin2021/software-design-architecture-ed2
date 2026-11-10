package com.example.enrollment.domain;

import java.util.Optional;

public interface EnrollmentRequestRepository {
    Optional<EnrollmentRequestLog> findByStudentIdAndRequestKey(long studentId, String key);

    void saveAndFlush(EnrollmentRequestLog log);
}
