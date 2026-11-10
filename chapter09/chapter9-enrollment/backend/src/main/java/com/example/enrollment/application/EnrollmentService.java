package com.example.enrollment.application;

import com.example.enrollment.domain.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EnrollmentService implements EnrollmentUseCases {
    private final StudentRepository students;
    private final TeachingClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final EnrollmentRequestRepository requests;
    private final Clock clock;
    private final AfterEnrollmentWrite afterWrite;

    public EnrollmentService(
            StudentRepository students,
            TeachingClassRepository classes,
            EnrollmentRepository enrollments,
            EnrollmentRequestRepository requests,
            Clock clock,
            AfterEnrollmentWrite afterWrite) {
        this.students = students;
        this.classes = classes;
        this.enrollments = enrollments;
        this.requests = requests;
        this.clock = clock;
        this.afterWrite = afterWrite;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentDto enroll(long studentId, long teachingClassId, String key) {
        InputPolicy.key(key);
        Student student =
                students.findForUpdate(studentId).orElseThrow(BusinessException::notFound);
        var done = requests.findByStudentIdAndRequestKey(studentId, key);
        if (done.isPresent()) {
            done.get().verifySameInput(teachingClassId);
            return EnrollmentDto.replay(done.get());
        }
        TeachingClass tc =
                classes.findForUpdate(teachingClassId).orElseThrow(BusinessException::notFound);
        Enrollment e =
                enrollments
                        .findByStudentIdAndTeachingClassId(studentId, teachingClassId)
                        .orElse(null);
        if (e != null && e.isDeleted()) throw BusinessException.notFound();
        Instant now = clock.instant();
        if (e == null || !e.isActive()) {
            long active =
                    enrollments.countByTeachingClassIdAndStatusAndDeletedFalse(
                            teachingClassId, EnrollmentStatus.ACTIVE);
            tc.ensureAvailable(active);
            if (e == null) e = Enrollment.create(student, tc, now);
            else e.activate(now);
            e = enrollments.saveAndFlush(e);
        }
        EnrollmentDto result = EnrollmentDto.from(e);
        requests.saveAndFlush(EnrollmentRequestLog.completed(studentId, key, e, now));
        afterWrite.beforeCommit();
        return result;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ClassItemDto> timetable(long studentId, String semester) {
        return enrollments.findTimetable(studentId, InputPolicy.semester(semester)).stream()
                .map(ClassItemDto::from)
                .toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(long studentId, long enrollmentId) {
        students.findForUpdate(studentId).orElseThrow(BusinessException::notFound);
        Enrollment e =
                enrollments.findUndeleted(enrollmentId).orElseThrow(BusinessException::notFound);
        // A found record still requires explicit ownership authorization.
        e.assertOwnedBy(studentId);
        if (e.isDeleted()) throw BusinessException.notFound();
        classes.findForUpdate(e.getTeachingClass().getId())
                .orElseThrow(BusinessException::notFound);
        e.withdraw(clock.instant());
        enrollments.saveAndFlush(e);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public EnrollmentDto requestStatus(long studentId, String key) {
        return EnrollmentDto.replay(
                requests.findByStudentIdAndRequestKey(studentId, InputPolicy.key(key))
                        .orElseThrow(BusinessException::notFound));
    }
}
