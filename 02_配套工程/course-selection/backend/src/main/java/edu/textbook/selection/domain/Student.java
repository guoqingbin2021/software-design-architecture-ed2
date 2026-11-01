package edu.textbook.selection.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="student")
public class Student {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="student_no",nullable=false,length=30) private String studentNo;
    @Column(nullable=false,length=80) private String name;
    @Column(name="credit_limit",nullable=false,precision=4,scale=1) private BigDecimal creditLimit;
    @Column(nullable=false) private boolean active;
    protected Student() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void ensureActive() {
        if(!active) throw new BusinessConflict("STUDENT_INACTIVE","学生账户已停用");
    }
}
