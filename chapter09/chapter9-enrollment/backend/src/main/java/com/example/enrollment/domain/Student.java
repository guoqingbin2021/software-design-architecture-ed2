package com.example.enrollment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "student")
public class Student {
    @Id private Long id;

    @Column(name = "student_number", nullable = false, unique = true, length = 32)
    private String studentNumber;

    @Column(nullable = false, length = 100)
    private String name;

    protected Student() {}

    public Student(long id, String studentNumber, String name) {
        this.id = id;
        this.studentNumber = studentNumber;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStudentNumber() {
        return studentNumber;
    }
}
