package com.example.enrollment.domain;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository {
    Optional<Enrollment> findByStudentIdAndTeachingClassId(long studentId, long classId);

    Optional<Enrollment> findUndeleted(long enrollmentId);

    long countByTeachingClassIdAndStatusAndDeletedFalse(long classId, EnrollmentStatus status);

    List<Enrollment> findTimetable(long studentId, String semester);

    Enrollment saveAndFlush(Enrollment enrollment);
}
