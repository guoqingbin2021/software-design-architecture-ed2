public interface RosterView {
    String getStudentNo();

    String getStudentName();

    BigDecimal getScore();
}

@Query(
        """
        select s.studentNo as studentNo,
                  s.name as studentName,
                  e.score.value as score
           from Enrollment e join e.student s
           where e.teachingClass.id = :classId
             and e.status = :status and e.deleted = false
           order by s.studentNo""")
List<RosterView> findRoster(
        @Param("classId") Long classId, @Param("status") EnrollmentStatus status);
