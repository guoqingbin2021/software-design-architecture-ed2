package edu.textbook.selection.repository;
import edu.textbook.selection.domain.Student;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface StudentRepository extends JpaRepository<Student,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id=:id")
    Optional<Student> lockById(@Param("id") long id);
}
