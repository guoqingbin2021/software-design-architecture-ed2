package edu.textbook.selection.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.textbook.selection.api.ApiDtos.*;
import edu.textbook.selection.api.ApiFailure;
import edu.textbook.selection.domain.*;
import edu.textbook.selection.repository.*;
import edu.textbook.selection.security.Actor;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class EnrollmentService {
    private final StudentRepository students;
    private final TeachingClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final EnrollmentRequestRepository requests;
    private final ObjectMapper json;
    private final Clock clock;

    public EnrollmentService(
            StudentRepository students,
            TeachingClassRepository classes,
            EnrollmentRepository enrollments,
            EnrollmentRequestRepository requests,
            ObjectMapper json,
            Clock clock) {
        this.students = students;
        this.classes = classes;
        this.enrollments = enrollments;
        this.requests = requests;
        this.json = json;
        this.clock = clock;
    }

    public List<ClassItem> classes(Actor actor) {
        var rows =
                actor.role().equals("TEACHER")
                        ? classes.findForTeacher(actor.personId())
                        : classes.findAllForDisplay();
        return rows.stream().map(ClassItem::from).toList();
    }

    public List<TimetableItem> timetable(Actor actor) {
        actor.requireRole("STUDENT");
        return enrollments.findActiveByStudent(actor.personId()).stream()
                .map(TimetableItem::from)
                .toList();
    }

    public EnrollmentResult requestStatus(Actor actor, String key) {
        actor.requireRole("STUDENT");
        if (key == null || !key.matches("[A-Za-z0-9._-]{1,80}"))
            throw new IllegalArgumentException("幂等键格式无效");
        return decode(
                requests.findByStudentIdAndRequestKey(actor.personId(), key)
                        .orElseThrow(ApiFailure::notFound)
                        .getResponseJson());
    }

    public List<RosterItem> roster(Actor actor, long classId) {
        actor.requireRole("TEACHER");
        var target = classes.findById(classId).orElseThrow(ApiFailure::notFound);
        ensureTeacher(actor, target);
        return enrollments.findRoster(classId).stream().map(RosterItem::from).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentResult enroll(Actor actor, long classId, String key) {
        actor.requireRole("STUDENT");
        if (key == null || !key.matches("[A-Za-z0-9._-]{1,80}"))
            throw new IllegalArgumentException("幂等键格式无效");
        // 先锁学生，使同一学生的课表与学分检查依次执行。
        var student = students.lockById(actor.personId()).orElseThrow(ApiFailure::notFound);
        var previous = requests.findByStudentIdAndRequestKey(student.getId(), key);
        if (previous.isPresent()) {
            var saved = previous.get();
            if (saved.getClassId() != classId)
                throw new BusinessConflict("IDEMPOTENCY_CONFLICT", "同一幂等键不能用于其他教学班");
            return decode(saved.getResponseJson());
        }
        // 确认不是旧请求后，再检查本次选课条件。
        student.ensureActive();
        // 再锁教学班，保护共享名额的检查与修改。
        var target = classes.lockById(classId).orElseThrow(ApiFailure::notFound);
        var now = clock.instant();
        target.ensureSelectionWindow(now);
        var active = enrollments.findActiveByStudent(student.getId());
        var duplicate =
                active.stream().filter(e -> e.getTeachingClass().getId() == classId).findFirst();
        if (duplicate.isPresent())
            return remember(student.getId(), key, classId, EnrollmentResult.from(duplicate.get()));

        // 只比较同一学期内的有效选课，依次检查课程、时间和学分。
        var inTerm =
                active.stream()
                        .filter(
                                e ->
                                        e.getTeachingClass()
                                                .getSemester()
                                                .equals(target.getSemester()))
                        .toList();
        if (inTerm.stream()
                .anyMatch(
                        e ->
                                e.getTeachingClass()
                                        .getCourse()
                                        .getId()
                                        .equals(target.getCourse().getId())))
            throw new BusinessConflict("DUPLICATE_COURSE", "同一学期不能重复选择同一课程");
        if (inTerm.stream().anyMatch(e -> e.getTeachingClass().overlaps(target)))
            throw new BusinessConflict("TIME_CONFLICT", "上课时间与当前课表冲突");
        BigDecimal credits =
                inTerm.stream()
                        .map(e -> e.getTeachingClass().getCourse().getCredits())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(target.getCourse().getCredits());
        if (credits.compareTo(student.getCreditLimit()) > 0)
            throw new BusinessConflict("CREDIT_LIMIT", "选课学分超过学生学分上限");

        // 人数、选课记录和请求快照在同一个事务中保存。
        target.reserveSeat();
        var enrollment = enrollments.saveAndFlush(Enrollment.create(student, target, now));
        // flush只发送SQL；事务提交后，控制器才能取得成功结果。
        return remember(student.getId(), key, classId, EnrollmentResult.from(enrollment));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Actor actor, long enrollmentId) {
        actor.requireRole("STUDENT");
        var location = enrollments.findCoordinates(enrollmentId).orElseThrow(ApiFailure::notFound);
        if (location.getStudentId() != actor.personId()) throw ApiFailure.forbidden();
        students.lockById(location.getStudentId()).orElseThrow(ApiFailure::notFound);
        classes.lockById(location.getClassId()).orElseThrow(ApiFailure::notFound);
        // 先查询定位编号，取得两把锁后再读取可变状态，避免读取旧实体。
        var enrollment = enrollments.findById(enrollmentId).orElseThrow(ApiFailure::notFound);
        enrollment.withdraw(clock.instant());
        enrollments.flush();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentResult recordScore(Actor actor, long enrollmentId, BigDecimal value) {
        actor.requireRole("TEACHER");
        var location = enrollments.findCoordinates(enrollmentId).orElseThrow(ApiFailure::notFound);
        students.lockById(location.getStudentId()).orElseThrow(ApiFailure::notFound);
        var target = classes.lockById(location.getClassId()).orElseThrow(ApiFailure::notFound);
        ensureTeacher(actor, target);
        var enrollment = enrollments.findById(enrollmentId).orElseThrow(ApiFailure::notFound);
        target.ensureClosedForGrades();
        enrollment.recordScore(value);
        enrollments.flush();
        return EnrollmentResult.from(enrollment);
    }

    private void ensureTeacher(Actor actor, TeachingClass target) {
        if (target.getTeacher().getId() != actor.personId()) throw ApiFailure.forbidden();
    }

    private EnrollmentResult remember(
            long studentId, String key, long classId, EnrollmentResult result) {
        try {
            requests.saveAndFlush(
                    EnrollmentRequest.completed(
                            studentId,
                            key,
                            classId,
                            result.id(),
                            json.writeValueAsString(result),
                            clock.instant()));
            return result;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法保存请求结果", ex);
        }
    }

    private EnrollmentResult decode(String snapshot) {
        try {
            return json.readValue(snapshot, EnrollmentResult.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法读取请求结果", ex);
        }
    }
}
