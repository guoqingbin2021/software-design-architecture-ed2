@Transactional
public Long enroll(Long studentId, Long classId, Long operatorId) {
    authorization.checkCanEnroll(operatorId, studentId);
    Student student =
            studentRepository
                    .findForUpdate(studentId)
                    .orElseThrow(() -> new DomainException("学生不存在"));
    TeachingClass clazz =
            classRepository
                    .findForUpdate(classId)
                    .orElseThrow(() -> new DomainException("教学班不存在"));
    student.ensureEligible();
    Instant now = clock.instant();
    policy.check(student, clazz, now);

    Enrollment old =
            enrollmentRepository
                    .findByStudentIdAndTeachingClassId(studentId, classId)
                    .orElse(null);
    if (old != null && (old.isDeleted() || old.getStatus() == EnrollmentStatus.ACTIVE)) {
        throw new DomainException("记录已删除或已处于选中状态");
    }
    long active =
            enrollmentRepository.countByTeachingClassIdAndStatusAndDeletedFalse(
                    classId, EnrollmentStatus.ACTIVE);
    clazz.ensureAvailable(active);
    Enrollment result;
    if (old == null) {
        result = Enrollment.create(student, clazz, now);
        result = enrollmentRepository.save(result);
    } else {
        old.activate(now);
        result = old;
    }
    enrollmentRepository.flush();
    return result.getId();
}
