package com.example.enrollment.infrastructure.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.List;

public class CampusPrincipal extends User {
    private final Long studentId;

    public CampusPrincipal(String username, String password, String role, Long studentId) {
        super(username, password, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.studentId = studentId;
    }

    public Long getStudentId() {
        return studentId;
    }
}
