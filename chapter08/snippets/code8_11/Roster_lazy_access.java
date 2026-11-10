List<Enrollment> rows =
        repository.findByTeachingClassIdAndStatusAndDeletedFalse(
                classId, EnrollmentStatus.ACTIVE);
for (Enrollment e : rows) {
    String name = e.getStudent().getName();
    // 使用name和成绩组装输出
}
