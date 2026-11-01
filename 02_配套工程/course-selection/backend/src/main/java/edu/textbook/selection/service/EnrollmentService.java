package edu.textbook.selection.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.textbook.selection.api.ApiDtos.*;
import edu.textbook.selection.api.ApiFailure;
import edu.textbook.selection.domain.*;
import edu.textbook.selection.repository.*;
import edu.textbook.selection.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.time.Clock;

@Service
@Transactional(readOnly=true)
public class EnrollmentService {
    private final StudentRepository students;
    private final TeachingClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final EnrollmentRequestRepository requests;
    private final ObjectMapper json;
    private final Clock clock;
    public EnrollmentService(StudentRepository students,TeachingClassRepository classes,EnrollmentRepository enrollments,
                             EnrollmentRequestRepository requests,ObjectMapper json,Clock clock) {
        this.students=students; this.classes=classes; this.enrollments=enrollments; this.requests=requests; this.json=json;
        this.clock=clock;
    }

    public List<ClassItem> classes(Actor actor) {
        var rows=actor.role().equals("TEACHER") ? classes.findForTeacher(actor.personId()) : classes.findAllForDisplay();
        return rows.stream().map(ClassItem::from).toList();
    }
    public List<TimetableItem> timetable(Actor actor) {
        actor.requireRole("STUDENT");
        return enrollments.findActiveByStudent(actor.personId()).stream().map(TimetableItem::from).toList();
    }
    public List<RosterItem> roster(Actor actor,long classId) {
        actor.requireRole("TEACHER");
        var target=classes.findById(classId).orElseThrow(ApiFailure::notFound);
        ensureTeacher(actor,target);
        return enrollments.findRoster(classId).stream().map(RosterItem::from).toList();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public EnrollmentResult enroll(Actor actor,long classId,String key) {
        actor.requireRole("STUDENT");
        if(key==null || !key.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("幂等键格式无效");
        // This lock serializes all requests by one student, even for different target classes.
        var student=students.lockById(actor.personId()).orElseThrow(ApiFailure::notFound);
        var previous=requests.findByStudentIdAndRequestKey(student.getId(),key);
        if(previous.isPresent()) {
            var saved=previous.get();
            if(saved.getClassId()!=classId) throw new BusinessConflict("IDEMPOTENCY_CONFLICT","同一幂等键不能用于其他教学班");
            return decode(saved.getResponseJson());
        }
        student.ensureActive();
        var target=classes.lockById(classId).orElseThrow(ApiFailure::notFound);
        var now=clock.instant();
        target.ensureSelectionWindow(now);
        var active=enrollments.findActiveByStudent(student.getId());
        var duplicate=active.stream().filter(e->e.getTeachingClass().getId()==classId).findFirst();
        if(duplicate.isPresent()) return remember(student.getId(),key,classId,EnrollmentResult.from(duplicate.get()));

        var inTerm=active.stream().filter(e->e.getTeachingClass().getSemester().equals(target.getSemester())).toList();
        if(inTerm.stream().anyMatch(e->e.getTeachingClass().getCourse().getId().equals(target.getCourse().getId())))
            throw new BusinessConflict("DUPLICATE_COURSE","同一学期不能重复选择同一课程");
        if(inTerm.stream().anyMatch(e->e.getTeachingClass().overlaps(target)))
            throw new BusinessConflict("TIME_CONFLICT","上课时间与当前课表冲突");
        BigDecimal credits=inTerm.stream().map(e->e.getTeachingClass().getCourse().getCredits())
                .reduce(BigDecimal.ZERO,BigDecimal::add).add(target.getCourse().getCredits());
        if(credits.compareTo(student.getCreditLimit())>0)
            throw new BusinessConflict("CREDIT_LIMIT","选课学分超过学生学分上限");

        target.reserveSeat();
        var enrollment=enrollments.saveAndFlush(Enrollment.create(student,target,now));
        // Flush only emits SQL. Success is returned to Controller after this transaction commits.
        return remember(student.getId(),key,classId,EnrollmentResult.from(enrollment));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void withdraw(Actor actor,long enrollmentId) {
        actor.requireRole("STUDENT");
        var location=enrollments.findCoordinates(enrollmentId).orElseThrow(ApiFailure::notFound);
        if(location.getStudentId()!=actor.personId()) throw ApiFailure.forbidden();
        students.lockById(location.getStudentId()).orElseThrow(ApiFailure::notFound);
        classes.lockById(location.getClassId()).orElseThrow(ApiFailure::notFound);
        // Load mutable state only after both locks; a scalar lookup above cannot leave stale state.
        var enrollment=enrollments.findById(enrollmentId).orElseThrow(ApiFailure::notFound);
        enrollment.withdraw(clock.instant());
        enrollments.flush();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public EnrollmentResult recordScore(Actor actor,long enrollmentId,BigDecimal value) {
        actor.requireRole("TEACHER");
        var location=enrollments.findCoordinates(enrollmentId).orElseThrow(ApiFailure::notFound);
        students.lockById(location.getStudentId()).orElseThrow(ApiFailure::notFound);
        var target=classes.lockById(location.getClassId()).orElseThrow(ApiFailure::notFound);
        ensureTeacher(actor,target);
        var enrollment=enrollments.findById(enrollmentId).orElseThrow(ApiFailure::notFound);
        target.ensureClosedForGrades();
        enrollment.recordScore(value);
        enrollments.flush();
        return EnrollmentResult.from(enrollment);
    }

    private void ensureTeacher(Actor actor,TeachingClass target) {
        if(target.getTeacher().getId()!=actor.personId()) throw ApiFailure.forbidden();
    }
    private EnrollmentResult remember(long studentId,String key,long classId,EnrollmentResult result) {
        try {
            requests.saveAndFlush(EnrollmentRequest.completed(studentId,key,classId,result.id(),json.writeValueAsString(result),clock.instant()));
            return result;
        } catch(JsonProcessingException ex) { throw new IllegalStateException("无法保存请求结果",ex); }
    }
    private EnrollmentResult decode(String snapshot) {
        try { return json.readValue(snapshot,EnrollmentResult.class); }
        catch(JsonProcessingException ex) { throw new IllegalStateException("无法读取请求结果",ex); }
    }
}
