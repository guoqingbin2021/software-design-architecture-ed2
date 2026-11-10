package com.example.enrollment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "enrollment",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_enrollment_student_class",
                        columnNames = {"student_id", "class_id"}))
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id")
    private TeachingClass teachingClass;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 16)
    private EnrollmentStatus status;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Enrollment() {}

    public static Enrollment create(Student student, TeachingClass teachingClass, Instant now) {
        Enrollment e = new Enrollment();
        e.student = Objects.requireNonNull(student);
        e.teachingClass = Objects.requireNonNull(teachingClass);
        e.createdAt = Objects.requireNonNull(now);
        e.status = EnrollmentStatus.ACTIVE;
        e.updatedAt = now;
        return e;
    }

    public void activate(Instant now) {
        requireNotDeleted();
        status = EnrollmentStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(now);
    }

    public void withdraw(Instant now) {
        requireNotDeleted();
        if (isActive()) {
            status = EnrollmentStatus.WITHDRAWN;
            updatedAt = Objects.requireNonNull(now);
        }
    }

    public void assertOwnedBy(long studentId) {
        if (student.getId() != studentId)
            throw new org.springframework.security.access.AccessDeniedException("只能操作本人选课");
    }

    private void requireNotDeleted() {
        if (deleted) throw BusinessException.notFound();
    }

    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE && !deleted;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public TeachingClass getTeachingClass() {
        return teachingClass;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
