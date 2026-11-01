package edu.textbook.selection.repository;
import edu.textbook.selection.domain.Enrollment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface EnrollmentRepository extends JpaRepository<Enrollment,Long> {
    interface Coordinates { Long getStudentId(); Long getClassId(); }
    // A scalar projection avoids caching a stale Enrollment before acquiring its student's lock.
    @Query("select e.student.id as studentId,e.teachingClass.id as classId from Enrollment e where e.id=:id")
    Optional<Coordinates> findCoordinates(@Param("id") long id);
    @Query("select e from Enrollment e join fetch e.teachingClass c join fetch c.course join fetch c.teacher where e.student.id=:studentId and e.status=edu.textbook.selection.domain.Enrollment.Status.ACTIVE order by c.id")
    List<Enrollment> findActiveByStudent(@Param("studentId") long studentId);
    @Query("select e from Enrollment e join fetch e.student where e.teachingClass.id=:classId and e.status=edu.textbook.selection.domain.Enrollment.Status.ACTIVE order by e.id")
    List<Enrollment> findRoster(@Param("classId") long classId);
}
