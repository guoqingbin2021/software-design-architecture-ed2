package com.example.enrollment.interfaces.http;

import com.example.enrollment.infrastructure.security.CampusPrincipal;

public record IdentityDto(String username, String role, long personId, String name) {
    public static IdentityDto from(CampusPrincipal p) {
        String role = p.getAuthorities().iterator().next().getAuthority().substring(5);
        String name =
                switch (p.getUsername()) {
                    case "student101" -> "张敏";
                    case "student102" -> "李明";
                    default -> "教师";
                };
        return new IdentityDto(p.getUsername(), role, p.getStudentId(), name);
    }
}
