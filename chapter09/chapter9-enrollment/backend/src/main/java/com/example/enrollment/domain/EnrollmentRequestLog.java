package com.example.enrollment.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "enrollment_request_log",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_request_student_key",
                        columnNames = {"student_id", "request_key"}))
public class EnrollmentRequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "request_key", nullable = false, length = 80)
    private String requestKey;

    @Column(name = "class_id", nullable = false)
    private Long teachingClassId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "response_status", nullable = false, length = 16)
    private EnrollmentStatus responseStatus;

    @Column(name = "response_course_name", nullable = false, length = 200)
    private String responseCourseName;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected EnrollmentRequestLog() {}

    public static EnrollmentRequestLog completed(
            long studentId, String key, Enrollment e, Instant now) {
        EnrollmentRequestLog log = new EnrollmentRequestLog();
        log.studentId = studentId;
        log.requestKey = key;
        log.teachingClassId = e.getTeachingClass().getId();
        log.enrollmentId = e.getId();
        log.responseStatus = e.getStatus();
        log.responseCourseName = e.getTeachingClass().getCourse().getName();
        log.completedAt = now;
        return log;
    }

    public void verifySameInput(long classId) {
        if (teachingClassId != classId)
            throw new BusinessException(
                    BusinessException.Code.IDEMPOTENCY_CONFLICT, "原请求键对应另一教学班，不能更改原请求体");
    }

    public Long getTeachingClassId() {
        return teachingClassId;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public EnrollmentStatus getResponseStatus() {
        return responseStatus;
    }

    public String getResponseCourseName() {
        return responseCourseName;
    }
}
