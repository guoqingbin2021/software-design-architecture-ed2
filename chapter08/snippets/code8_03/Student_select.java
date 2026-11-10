public Enrollment select(TeachingClass teachingClass) {
    teachingClass.ensureAvailable();
    boolean duplicated =
            enrollments.stream().anyMatch(e -> e.belongsTo(teachingClass) && e.isActive());
    if (duplicated) {
        throw new DomainException("不能重复选同一教学班");
    }
    Enrollment enrollment = Enrollment.create(this, teachingClass);
    enrollments.add(enrollment);
    teachingClass.addEnrollment(enrollment);
    return enrollment;
}
