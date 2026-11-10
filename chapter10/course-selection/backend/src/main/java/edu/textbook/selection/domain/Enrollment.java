package edu.textbook.selection.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "enrollment")
public class Enrollment {
    public enum Status {
        ACTIVE,
        WITHDRAWN
    }

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
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Enrollment() {}

    public static Enrollment create(Student student, TeachingClass teachingClass, Instant now) {
        Enrollment result = new Enrollment();
        result.student = student;
        result.teachingClass = teachingClass;
        result.status = Status.ACTIVE;
        result.enrolledAt = now;
        return result;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public void withdraw(Instant now) {
        if (!isActive()) return;
        if (score != null) throw new BusinessConflict("SCORED_ENROLLMENT", "已有成绩，不能退课");
        teachingClass.ensureSelectionWindow(now);
        teachingClass.releaseSeat();
        status = Status.WITHDRAWN;
        withdrawnAt = now;
    }

    public void recordScore(BigDecimal value) {
        if (!isActive()) throw new BusinessConflict("ENROLLMENT_INACTIVE", "只能为有效选课录入成绩");
        if (value == null
                || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(new BigDecimal("100")) > 0
                || value.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("成绩必须为0至100且最多两位小数");
        score = value;
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

    public Status getStatus() {
        return status;
    }

    public BigDecimal getScore() {
        return score;
    }
}
