public interface TeachingClassRepository extends JpaRepository<TeachingClass, Long> {

    @EntityGraph(attributePaths = {"course", "teacher"})
    Optional<TeachingClass> findDetailedById(Long id);

    @Query(
            "select new edu.textbook.ch8.RosterRow("
                    + "s.studentNo, s.name, e.score.value) "
                    + "from Enrollment e join e.student s "
                    + "where e.teachingClass.id = :classId "
                    + "and e.status = :status and e.deleted = false")
    List<RosterRow> findRoster(
            @Param("classId") Long classId, @Param("status") EnrollmentStatus status);
}
