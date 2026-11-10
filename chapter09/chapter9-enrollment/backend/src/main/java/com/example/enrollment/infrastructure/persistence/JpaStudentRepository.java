package com.example.enrollment.infrastructure.persistence;

import com.example.enrollment.domain.*;
import jakarta.persistence.*;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaStudentRepository implements StudentRepository {
    @PersistenceContext private EntityManager em;

    public Optional<Student> findForUpdate(long id) {
        return Optional.ofNullable(em.find(Student.class, id, LockModeType.PESSIMISTIC_WRITE));
    }
}
