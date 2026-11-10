package edu.textbook.selection.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "course")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code", nullable = false, length = 30)
    private String courseCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal credits;

    protected Course() {}

    public Long getId() {
        return id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCredits() {
        return credits;
    }
}
