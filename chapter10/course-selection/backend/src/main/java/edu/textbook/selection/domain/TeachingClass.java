package edu.textbook.selection.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "teaching_class")
public class TeachingClass {
    public enum Status {
        OPEN,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @Column(nullable = false, length = 30)
    private String semester;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Embedded private MeetingSlot meetingSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "open_at", nullable = false)
    private Instant openAt;

    @Column(name = "close_at", nullable = false)
    private Instant closeAt;

    protected TeachingClass() {}

    public void ensureOpen() {
        if (status != Status.OPEN) throw new BusinessConflict("CLASS_CLOSED", "教学班未开放选退课");
    }

    public void ensureSelectionWindow(Instant now) {
        ensureOpen();
        if (now.isBefore(openAt)) throw new BusinessConflict("WINDOW_NOT_OPEN", "选退课时间尚未开始");
        if (!now.isBefore(closeAt)) throw new BusinessConflict("WINDOW_CLOSED", "选退课时间已经截止");
    }

    public void reserveSeat() {
        ensureOpen();
        if (enrolledCount >= capacity) throw new BusinessConflict("CLASS_FULL", "教学班名额已满");
        enrolledCount++;
    }

    public void ensureClosedForGrades() {
        if (status != Status.CLOSED)
            throw new BusinessConflict("CLASS_NOT_CLOSED", "选课阶段结束后才能录入成绩");
    }

    public void releaseSeat() {
        ensureOpen();
        if (enrolledCount <= 0) throw new IllegalStateException("教学班人数与选课记录不一致");
        enrolledCount--;
    }

    public boolean overlaps(TeachingClass other) {
        return semester.equals(other.semester) && meetingSlot.overlaps(other.meetingSlot);
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public String getSemester() {
        return semester;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public MeetingSlot getMeetingSlot() {
        return meetingSlot;
    }

    public Status getStatus() {
        return status;
    }
}
