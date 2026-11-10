package com.example.enrollment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teaching_class")
public class TeachingClass {
    @Id private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @Column(nullable = false, length = 5)
    private String semester;

    @Column(nullable = false)
    private int capacity;

    @Column(length = 100)
    private String room;

    @Column(name = "week_display", length = 32)
    private String week;

    @Column(name = "time_display", length = 100)
    private String time;

    protected TeachingClass() {}

    public TeachingClass(long id, Course course, String semester, int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be nonnegative");
        this.id = id;
        this.course = course;
        this.semester = semester;
        this.capacity = capacity;
    }

    public void ensureAvailable(long activeCount) {
        if (activeCount < 0) throw new IllegalArgumentException("activeCount must be nonnegative");
        if (activeCount >= capacity)
            throw new BusinessException(BusinessException.Code.CLASS_FULL, "教学班容量已满");
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

    public String getRoom() {
        return room;
    }

    public String getWeek() {
        return week;
    }

    public String getTime() {
        return time;
    }
}
