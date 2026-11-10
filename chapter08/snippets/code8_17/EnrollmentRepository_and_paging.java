public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    Page<Enrollment> findByTeachingClassIdAndStatusAndDeletedFalse(
            Long classId, EnrollmentStatus status, Pageable pageable);
}

Sort sort = Sort.by("enrolledAt").descending().and(Sort.by("id").descending());
Pageable pageable = PageRequest.of(page, size, sort);
Page<Enrollment> result =
        repository.findByTeachingClassIdAndStatusAndDeletedFalse(
                501L, EnrollmentStatus.ACTIVE, pageable);
