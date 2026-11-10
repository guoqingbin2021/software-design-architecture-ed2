package com.example.enrollment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teacher")
public class Teacher {
    @Id private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    protected Teacher() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
