package com.example.enrollment.application;

import java.util.List;

public interface EnrollmentUseCases {
    EnrollmentDto enroll(long studentId, long teachingClassId, String key);

    List<ClassItemDto> timetable(long studentId, String semester);

    void withdraw(long studentId, long enrollmentId);

    EnrollmentDto requestStatus(long studentId, String key);
}
