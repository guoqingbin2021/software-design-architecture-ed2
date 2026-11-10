package edu.textbook.selection.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teacher")
public class Teacher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_no", nullable = false, length = 30)
    private String teacherNo;

    @Column(nullable = false, length = 80)
    private String name;

    protected Teacher() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
