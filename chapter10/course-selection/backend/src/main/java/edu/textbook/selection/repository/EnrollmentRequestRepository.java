package edu.textbook.selection.repository;

import edu.textbook.selection.domain.EnrollmentRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRequestRepository extends JpaRepository<EnrollmentRequest, Long> {
    Optional<EnrollmentRequest> findByStudentIdAndRequestKey(long studentId, String requestKey);
}
