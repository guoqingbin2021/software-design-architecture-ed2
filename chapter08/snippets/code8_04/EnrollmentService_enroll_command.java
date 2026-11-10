@Transactional
public Long enroll(EnrollCommand command) {
    Student student =
            studentRepository
                    .findByStudentNo(command.studentNo())
                    .orElseThrow(() -> new DomainException("学生不存在"));
    TeachingClass clazz = classRepository.getReferenceById(command.classId());
    authorization.checkCanEnroll(command.operatorId(), student);

    Enrollment enrollment = student.select(clazz);
    enrollmentRepository.save(enrollment);
    return enrollment.getId();
}
