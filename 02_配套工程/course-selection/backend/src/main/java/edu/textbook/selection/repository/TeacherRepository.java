package edu.textbook.selection.repository;
import edu.textbook.selection.domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TeacherRepository extends JpaRepository<Teacher,Long> {}
