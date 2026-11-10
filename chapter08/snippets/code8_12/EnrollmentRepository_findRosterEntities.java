@Query(
        """
        select e from Enrollment e join fetch e.student
        where e.teachingClass.id = :classId
          and e.status = :status and e.deleted = false
        order by e.id
        """)
List<Enrollment> findRosterEntities(
        @Param("classId") Long classId, @Param("status") EnrollmentStatus status);
