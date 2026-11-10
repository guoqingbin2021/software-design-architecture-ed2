package com.example.enrollment.interfaces.http;

import com.example.enrollment.application.*;
import com.example.enrollment.infrastructure.security.CampusPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('STUDENT')")
public class EnrollmentController {
    private final EnrollmentUseCases service;

    public EnrollmentController(EnrollmentUseCases service) {
        this.service = service;
    }

    @PostMapping("/enrollments")
    public EnrollmentDto enroll(
            @AuthenticationPrincipal CampusPrincipal principal,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EnrollmentRequest request) {
        return service.enroll(principal.getStudentId(), request.teachingClassIdAsLong(), key);
    }

    @GetMapping("/enrollments/me")
    public ResponseEntity<List<ClassItemDto>> timetable(
            @AuthenticationPrincipal CampusPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.timetable(principal.getStudentId(), "2026秋"));
    }

    @DeleteMapping("/enrollments/{enrollmentId}")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal CampusPrincipal principal, @PathVariable String enrollmentId) {
        service.withdraw(principal.getStudentId(), InputPolicy.id(enrollmentId, "enrollmentId"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/enrollment-requests/{key}")
    public ResponseEntity<EnrollmentDto> requestStatus(
            @AuthenticationPrincipal CampusPrincipal principal, @PathVariable String key) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.requestStatus(principal.getStudentId(), key));
    }
}
