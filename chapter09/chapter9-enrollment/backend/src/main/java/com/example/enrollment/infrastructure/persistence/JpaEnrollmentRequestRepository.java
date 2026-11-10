package com.example.enrollment.infrastructure.persistence;

import com.example.enrollment.domain.*;
import jakarta.persistence.*;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEnrollmentRequestRepository implements EnrollmentRequestRepository {
    @PersistenceContext private EntityManager em;

    public Optional<EnrollmentRequestLog> findByStudentIdAndRequestKey(long studentId, String key) {
        return em.createQuery(
                        "select r from EnrollmentRequestLog r where r.studentId=:studentId and r.requestKey=:key",
                        EnrollmentRequestLog.class)
                .setParameter("studentId", studentId)
                .setParameter("key", key)
                .getResultStream()
                .findFirst();
    }

    public void saveAndFlush(EnrollmentRequestLog log) {
        em.persist(log);
        em.flush();
    }
}
