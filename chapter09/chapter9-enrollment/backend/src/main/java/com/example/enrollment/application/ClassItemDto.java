package com.example.enrollment.application;

import com.example.enrollment.domain.*;
import java.math.BigDecimal;

public record ClassItemDto(
        long id,
        long teachingClassId,
        String courseName,
        String teacherName,
        String room,
        String week,
        String time,
        String status,
        BigDecimal score) {
    public static ClassItemDto from(Enrollment e) {
        TeachingClass c = e.getTeachingClass();
        return new ClassItemDto(
                e.getId(),
                c.getId(),
                c.getCourse().getName(),
                c.getTeacher() == null ? null : c.getTeacher().getName(),
                c.getRoom(),
                c.getWeek(),
                c.getTime(),
                e.getStatus().name(),
                null);
    }
}
