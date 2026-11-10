package com.example.enrollment.application;

import com.example.enrollment.domain.*;
import java.math.BigDecimal;

public record EnrollmentDto(
        long id, long teachingClassId, String courseName, String status, BigDecimal score) {
    public static EnrollmentDto from(Enrollment e) {
        return new EnrollmentDto(
                e.getId(),
                e.getTeachingClass().getId(),
                e.getTeachingClass().getCourse().getName(),
                e.getStatus().name(),
                null);
    }

    public static EnrollmentDto replay(EnrollmentRequestLog log) {
        return new EnrollmentDto(
                log.getEnrollmentId(),
                log.getTeachingClassId(),
                log.getResponseCourseName(),
                log.getResponseStatus().name(),
                null);
    }
}
