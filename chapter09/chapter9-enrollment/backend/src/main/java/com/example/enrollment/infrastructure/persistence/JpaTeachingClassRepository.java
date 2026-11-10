package com.example.enrollment.infrastructure.persistence;

import com.example.enrollment.domain.*;
import jakarta.persistence.*;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTeachingClassRepository implements TeachingClassRepository {
    @PersistenceContext private EntityManager em;

    public Optional<TeachingClass> findForUpdate(long id) {
        return Optional.ofNullable(
                em.find(TeachingClass.class, id, LockModeType.PESSIMISTIC_WRITE));
    }
}
