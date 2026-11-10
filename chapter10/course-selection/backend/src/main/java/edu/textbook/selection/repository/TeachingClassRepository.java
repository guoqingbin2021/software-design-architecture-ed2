package edu.textbook.selection.repository;

import edu.textbook.selection.domain.TeachingClass;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TeachingClassRepository extends JpaRepository<TeachingClass, Long> {
    // Keep this lock to the class row; fetch joins would also lock unrelated course/teacher rows.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TeachingClass c where c.id=:id")
    Optional<TeachingClass> lockById(@Param("id") long id);

    @Query("select c from TeachingClass c join fetch c.course join fetch c.teacher order by c.id")
    List<TeachingClass> findAllForDisplay();

    @Query(
            "select c from TeachingClass c join fetch c.course join fetch c.teacher where"
                    + " c.teacher.id=:teacherId order by c.id")
    List<TeachingClass> findForTeacher(@Param("teacherId") long teacherId);
}
