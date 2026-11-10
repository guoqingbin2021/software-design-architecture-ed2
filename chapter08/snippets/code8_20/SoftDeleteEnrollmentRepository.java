public interface SoftDeleteEnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
        update Enrollment e
           set e.deleted = true, e.deletedAt = CURRENT_TIMESTAMP,
               e.version = e.version + 1
         where e.id = :id and e.deleted = false
           and e.status = :withdrawn and e.version = :version
        """)
    int softDeleteById(
            @Param("id") Long id,
            @Param("withdrawn") EnrollmentStatus withdrawn,
            @Param("version") long expectedVersion);

    Optional<Enrollment> findByIdAndDeletedFalse(Long id);

    Page<Enrollment> findByStudentIdAndDeletedFalse(Long studentId, Pageable pageable);
}
