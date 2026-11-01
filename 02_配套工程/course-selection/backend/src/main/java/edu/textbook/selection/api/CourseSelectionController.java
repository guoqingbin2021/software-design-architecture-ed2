package edu.textbook.selection.api;

import edu.textbook.selection.api.ApiDtos.*;
import edu.textbook.selection.security.Actor;
import edu.textbook.selection.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api")
public class CourseSelectionController {
    private final EnrollmentService service;
    public CourseSelectionController(EnrollmentService service) { this.service=service; }
    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal Actor actor) { return new Me(actor.getUsername(),actor.role(),actor.personId(),actor.name()); }
    @GetMapping("/teaching-classes")
    public List<ClassItem> classes(@AuthenticationPrincipal Actor actor) { return service.classes(actor); }
    @PostMapping("/enrollments")
    public EnrollmentResult enroll(@AuthenticationPrincipal Actor actor,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SelectRequest request) {
        return service.enroll(actor,request.teachingClassId(),key);
    }
    @GetMapping("/enrollments/me")
    public List<TimetableItem> timetable(@AuthenticationPrincipal Actor actor) { return service.timetable(actor); }
    @DeleteMapping("/enrollments/{id}")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Actor actor,@PathVariable long id) {
        service.withdraw(actor,id); return ResponseEntity.noContent().build();
    }
    @GetMapping("/teaching-classes/{id}/roster")
    public List<RosterItem> roster(@AuthenticationPrincipal Actor actor,@PathVariable long id) { return service.roster(actor,id); }
    @PutMapping("/enrollments/{id}/score")
    public EnrollmentResult score(@AuthenticationPrincipal Actor actor,@PathVariable long id,@Valid @RequestBody ScoreRequest request) {
        return service.recordScore(actor,id,request.score());
    }
}
