package edu.textbook.selection.api;

import edu.textbook.selection.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class ApiDtos {
    private ApiDtos() {}

    public record SelectRequest(@NotNull @Positive @Max(9007199254740991L) Long teachingClassId) {}

    public record ScoreRequest(
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
                    BigDecimal score) {}

    public record Me(String username, String role, long personId, String name) {}

    public record ClassItem(
            long id,
            String courseCode,
            String courseName,
            String teacherName,
            int capacity,
            int enrolledCount,
            int dayOfWeek,
            int startPeriod,
            int endPeriod,
            BigDecimal credits,
            String status) {
        public static ClassItem from(TeachingClass c) {
            var slot = c.getMeetingSlot();
            return new ClassItem(
                    c.getId(),
                    c.getCourse().getCourseCode(),
                    c.getCourse().getName(),
                    c.getTeacher().getName(),
                    c.getCapacity(),
                    c.getEnrolledCount(),
                    slot.getDayOfWeek(),
                    slot.getStartPeriod(),
                    slot.getEndPeriod(),
                    c.getCourse().getCredits(),
                    c.getStatus().name());
        }
    }

    public record EnrollmentResult(
            long id, long teachingClassId, String courseName, String status, BigDecimal score) {
        public static EnrollmentResult from(Enrollment e) {
            return new EnrollmentResult(
                    e.getId(),
                    e.getTeachingClass().getId(),
                    e.getTeachingClass().getCourse().getName(),
                    e.getStatus().name(),
                    e.getScore());
        }
    }

    public record TimetableItem(
            long id,
            long teachingClassId,
            String courseCode,
            String courseName,
            String teacherName,
            int capacity,
            int enrolledCount,
            int dayOfWeek,
            int startPeriod,
            int endPeriod,
            BigDecimal credits,
            String status,
            BigDecimal score,
            String classStatus) {
        public static TimetableItem from(Enrollment e) {
            var c = e.getTeachingClass();
            var slot = c.getMeetingSlot();
            return new TimetableItem(
                    e.getId(),
                    c.getId(),
                    c.getCourse().getCourseCode(),
                    c.getCourse().getName(),
                    c.getTeacher().getName(),
                    c.getCapacity(),
                    c.getEnrolledCount(),
                    slot.getDayOfWeek(),
                    slot.getStartPeriod(),
                    slot.getEndPeriod(),
                    c.getCourse().getCredits(),
                    e.getStatus().name(),
                    e.getScore(),
                    c.getStatus().name());
        }
    }

    public record RosterItem(
            long id, long studentId, String studentName, String status, BigDecimal score) {
        public static RosterItem from(Enrollment e) {
            return new RosterItem(
                    e.getId(),
                    e.getStudent().getId(),
                    e.getStudent().getName(),
                    e.getStatus().name(),
                    e.getScore());
        }
    }
}
