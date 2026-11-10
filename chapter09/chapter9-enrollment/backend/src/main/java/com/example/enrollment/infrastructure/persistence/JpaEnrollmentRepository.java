package com.example.enrollment.infrastructure.persistence;

import com.example.enrollment.domain.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEnrollmentRepository implements EnrollmentRepository {
    @PersistenceContext private EntityManager em;

    public Optional<Enrollment> findByStudentIdAndTeachingClassId(long studentId, long classId) {
        return em.createQuery(
                        "select e from Enrollment e where e.student.id=:studentId and e.teachingClass.id=:classId",
                        Enrollment.class)
                .setParameter("studentId", studentId)
                .setParameter("classId", classId)
                .getResultStream()
                .findFirst();
    }

    public Optional<Enrollment> findUndeleted(long enrollmentId) {
        return em.createQuery(
                        "select e from Enrollment e where e.id=:id and e.deleted=false",
                        Enrollment.class)
                .setParameter("id", enrollmentId)
                .getResultStream()
                .findFirst();
    }

    public long countByTeachingClassIdAndStatusAndDeletedFalse(
            long classId, EnrollmentStatus status) {
        return em.createQuery(
                        "select count(e) from Enrollment e where e.teachingClass.id=:classId and e.status=:status and e.deleted=false",
                        Long.class)
                .setParameter("classId", classId)
                .setParameter("status", status)
                .getSingleResult();
    }

    public List<Enrollment> findTimetable(long studentId, String semester) {
        return em.createQuery(
                        "select e from Enrollment e join fetch e.teachingClass c join fetch c.course left join fetch c.teacher where e.student.id=:studentId and c.semester=:semester and e.status=:status and e.deleted=false order by c.id",
                        Enrollment.class)
                .setParameter("studentId", studentId)
                .setParameter("semester", semester)
                .setParameter("status", EnrollmentStatus.ACTIVE)
                .getResultList();
    }

    public Enrollment saveAndFlush(Enrollment e) {
        if (e.getId() == null) em.persist(e);
        else if (!em.contains(e)) e = em.merge(e);
        em.flush();
        return e;
    }
}
