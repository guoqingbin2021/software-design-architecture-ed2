package edu.textbook.selection.repository;
import edu.textbook.selection.domain.EnrollmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface EnrollmentRequestRepository extends JpaRepository<EnrollmentRequest,Long> {
    Optional<EnrollmentRequest> findByStudentIdAndRequestKey(long studentId,String requestKey);
}
