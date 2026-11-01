package edu.textbook.selection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP requests and committed MySQL reads; intentionally no @Transactional test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(CourseSelectionIntegrationTest.TestClockConfiguration.class)
class CourseSelectionIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock testClock;
    @Autowired edu.textbook.selection.repository.TeachingClassRepository classRepository;
    static final String PASSWORD = "Chapter10!";

    @BeforeEach void resetCommittedData() {
        testClock.set(Instant.parse("2026-10-31T12:00:00Z"));
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_request_insert");
        jdbc.update("DELETE FROM enrollment_request");
        jdbc.update("DELETE FROM enrollment");
        jdbc.update("UPDATE teaching_class SET enrolled_count=0,status=CASE WHEN id=5 THEN 'CLOSED' ELSE 'OPEN' END");
        jdbc.update("UPDATE student SET credit_limit=12.0");
    }

    ResponseEntity<String> request(String user, HttpMethod method, String path, Object body, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) headers.set("Idempotency-Key", key);
        return (user == null ? http : http.withBasicAuth(user, PASSWORD))
                .exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
    ResponseEntity<String> enroll(String user, long classId, String key) {
        return request(user, HttpMethod.POST, "/api/enrollments", Map.of("teachingClassId", classId), key);
    }
    JsonNode body(ResponseEntity<String> response) throws Exception { return json.readTree(response.getBody()); }
    long id(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).withFailMessage("Expected committed enrollment, received %s: %s",response.getStatusCode(),response.getBody()).isEqualTo(200);
        return body(response).get("id").asLong();
    }
    int scalar(String sql) { return jdbc.queryForObject(sql, Integer.class); }
    void error(ResponseEntity<String> response, int status, String code) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(body(response).get("code").asText()).isEqualTo(code);
        assertThat(body(response).get("requestId").asText()).isNotBlank();
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test void authenticatesSeededIdentityAndQueriesClassesOnMysql() throws Exception {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.");
        var me = request("student1", HttpMethod.GET, "/api/me", null, null);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(body(me).get("personId").asLong()).isEqualTo(1);
        assertThat(body(me).get("role").asText()).isEqualTo("STUDENT");
        var classes = request("student1", HttpMethod.GET, "/api/teaching-classes", null, null);
        assertThat(classes.getStatusCode().value()).isEqualTo(200);
        assertThat(body(classes).size()).isEqualTo(6);
        assertThat(body(classes).get(0).get("capacity").asInt()).isEqualTo(1);
        var teacherClasses=request("teacher2",HttpMethod.GET,"/api/teaching-classes",null,null);
        assertThat(body(teacherClasses)).hasSize(2);
    }
    @Test void requiresAuthenticationAndEnforcesRoles() throws Exception {
        error(request(null, HttpMethod.GET, "/api/me", null, null), 401, "UNAUTHENTICATED");
        error(enroll("teacher1", 1, "teacher-key"), 403, "FORBIDDEN");
        error(request("student1", HttpMethod.GET, "/api/teaching-classes/1/roster", null, null), 403, "FORBIDDEN");
        error(request("teacher2", HttpMethod.GET, "/api/teaching-classes/1/roster", null, null), 403, "FORBIDDEN");
    }
    @Test void selectsQueriesTimetableAndWithdrawsWithHistory() throws Exception {
        var selected = enroll("student1", 1, "first");
        assertThat(selected.getStatusCode().value()).isEqualTo(200);
        long enrollmentId = id(selected);
        var timetable = request("student1", HttpMethod.GET, "/api/enrollments/me", null, null);
        assertThat(body(timetable)).hasSize(1);
        assertThat(body(timetable).get(0).get("teachingClassId").asLong()).isEqualTo(1);
        assertThat(request("student1", HttpMethod.DELETE, "/api/enrollments/"+enrollmentId, null, null).getStatusCode().value()).isEqualTo(204);
        assertThat(request("student1", HttpMethod.DELETE, "/api/enrollments/"+enrollmentId, null, null).getStatusCode().value()).isEqualTo(204);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE status='WITHDRAWN'")).isEqualTo(1);
        assertThat(scalar("SELECT enrolled_count FROM teaching_class WHERE id=1")).isZero();
        assertThat(body(request("student1", HttpMethod.GET, "/api/enrollments/me", null, null))).isEmpty();
    }
    @Test void replaysRequestWithoutResurrectingWithdrawnEnrollment() throws Exception {
        var first = enroll("student1", 1, "same-key"); long oldId=id(first);
        assertThat(enroll("student1", 1, "same-key").getBody()).isEqualTo(first.getBody());
        request("student1", HttpMethod.DELETE, "/api/enrollments/"+oldId, null, null);
        var replay = enroll("student1", 1, "same-key");
        assertThat(replay.getBody()).isEqualTo(first.getBody());
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE status='ACTIVE'")).isZero();
        assertThat(scalar("SELECT enrolled_count FROM teaching_class WHERE id=1")).isZero();
        var next = enroll("student1", 1, "new-intent");
        assertThat(id(next)).isNotEqualTo(oldId);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment")).isEqualTo(2);
        error(enroll("student1", 3, "same-key"), 409, "IDEMPOTENCY_CONFLICT");
    }
    @Test void duplicateActiveSelectionWithNewKeyDoesNotConsumeAnotherSeat() throws Exception {
        var first=enroll("student1", 1, "one");
        assertThat(id(enroll("student1", 1, "two"))).isEqualTo(id(first));
        assertThat(scalar("SELECT enrolled_count FROM teaching_class WHERE id=1")).isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment_request")).isEqualTo(2);
    }
    @Test void rejectsClosedFullConflictingDuplicateCourseAndCreditLimit() throws Exception {
        error(enroll("student1", 5, "closed"),409,"CLASS_CLOSED");
        assertThat(enroll("student1",1,"ok").getStatusCode().value()).isEqualTo(200);
        error(enroll("student2",1,"full"),409,"CLASS_FULL");
        error(enroll("student1",2,"time"),409,"TIME_CONFLICT");
        error(enroll("student1",6,"course"),409,"DUPLICATE_COURSE");
        jdbc.update("UPDATE student SET credit_limit=3 WHERE id=1");
        error(enroll("student1",3,"credits"),409,"CREDIT_LIMIT");
        assertThat(scalar("SELECT COUNT(*) FROM enrollment")).isEqualTo(1);
    }
    @Test void validatesInputAndDoesNotTrustSelfReportedIdentity() throws Exception {
        error(enroll("student1",1,null),400,"VALIDATION_ERROR");
        error(enroll("student1",999,"missing"),404,"NOT_FOUND");
        error(request("student1",HttpMethod.POST,"/api/enrollments",Map.of("teachingClassId",1,"studentId",2),"forged"),400,"VALIDATION_ERROR");
        error(request("student1",HttpMethod.POST,"/api/enrollments",Map.of("teachingClassId",-1),"negative"),400,"VALIDATION_ERROR");
        assertThat(scalar("SELECT COUNT(*) FROM enrollment")).isZero();
    }
    @Test void checksStudentAndTeacherOwnershipAndScoreBoundaries() throws Exception {
        long enrollmentId=id(enroll("student1",1,"score"));
        error(request("student2",HttpMethod.DELETE,"/api/enrollments/"+enrollmentId,null,null),403,"FORBIDDEN");
        error(request("teacher2",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",85),null),403,"FORBIDDEN");
        error(request("student1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",85),null),403,"FORBIDDEN");
        error(request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",-1),null),400,"VALIDATION_ERROR");
        error(request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",101),null),400,"VALIDATION_ERROR");
        error(request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",85.125),null),400,"VALIDATION_ERROR");
        error(request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",java.util.Collections.singletonMap("score",null),null),400,"VALIDATION_ERROR");
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE score IS NOT NULL")).isZero();
        error(request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",85),null),409,"CLASS_NOT_CLOSED");
        jdbc.update("UPDATE teaching_class SET status='CLOSED' WHERE id=1");
        for(int value: List.of(0,100,85)) {
            var score=request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",value),null);
            assertThat(score.getStatusCode().value()).isEqualTo(200);
            assertThat(body(score).get("score").asInt()).isEqualTo(value);
        }
        var roster=request("teacher1",HttpMethod.GET,"/api/teaching-classes/1/roster",null,null);
        assertThat(body(roster)).hasSize(1);
        assertThat(body(roster).get(0).get("id").asLong()).isEqualTo(enrollmentId);
        error(request("student1",HttpMethod.DELETE,"/api/enrollments/"+enrollmentId,null,null),409,"SCORED_ENROLLMENT");
    }
    @Test void closedClassBlocksWithdrawalAndWithdrawnEnrollmentCannotBeScored() throws Exception {
        long enrollmentId=id(enroll("student1",1,"close"));
        jdbc.update("UPDATE teaching_class SET status='CLOSED' WHERE id=1");
        error(request("student1",HttpMethod.DELETE,"/api/enrollments/"+enrollmentId,null,null),409,"CLASS_CLOSED");
        jdbc.update("UPDATE teaching_class SET status='OPEN' WHERE id=1");
        request("student1",HttpMethod.DELETE,"/api/enrollments/"+enrollmentId,null,null);
        jdbc.update("UPDATE teaching_class SET status='CLOSED' WHERE id=1");
        error(request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",85),null),409,"ENROLLMENT_INACTIVE");
    }
    @Test void concurrentStudentsCompeteForOneSeatWithoutOverbooking() throws Exception {
        var responses=concurrently(() -> enroll("student1",1,"race1"),()->enroll("student2",1,"race2"));
        assertThat(responses.stream().map(r->r.getStatusCode().value())).containsExactlyInAnyOrder(200,409);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE class_id=1 AND status='ACTIVE'")).isEqualTo(1);
        assertThat(scalar("SELECT enrolled_count FROM teaching_class WHERE id=1")).isEqualTo(1);
    }
    @Test void concurrentSameStudentConflictingClassesAreSerialized() throws Exception {
        var responses=concurrently(() -> enroll("student1",1,"student-race1"),()->enroll("student1",2,"student-race2"));
        assertThat(responses.stream().map(r->r.getStatusCode().value())).containsExactlyInAnyOrder(200,409);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE student_id=1 AND status='ACTIVE'")).isEqualTo(1);
        assertThat(scalar("SELECT SUM(enrolled_count) FROM teaching_class")).isEqualTo(1);
    }
    @Test void concurrentSameIdempotencyKeyReturnsOneCommittedResult() throws Exception {
        var responses=concurrently(() -> enroll("student1",1,"same-race"),()->enroll("student1",1,"same-race"));
        assertThat(responses.stream().map(r->r.getStatusCode().value())).containsExactly(200,200);
        assertThat(responses.get(0).getBody()).isEqualTo(responses.get(1).getBody());
        assertThat(scalar("SELECT COUNT(*) FROM enrollment")).isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment_request")).isEqualTo(1);
    }
    @Test void databaseFailureRollsBackEnrollmentSeatAndIdempotencyRecord() throws Exception {
        // Fault injection exists only in this test: fail after enrollment SQL is flushed.
        jdbc.execute("CREATE TRIGGER test_fail_request_insert BEFORE INSERT ON enrollment_request FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test-only failure after enrollment flush'");
        try {
            var response=enroll("student1",1,"rollback");
            error(response,500,"INTERNAL_ERROR");
            // JdbcTemplate opens a different connection, after the HTTP transaction completed.
            assertThat(scalar("SELECT COUNT(*) FROM enrollment")).isZero();
            assertThat(scalar("SELECT COUNT(*) FROM enrollment_request")).isZero();
            assertThat(scalar("SELECT enrolled_count FROM teaching_class WHERE id=1")).isZero();
        } finally { jdbc.execute("DROP TRIGGER IF EXISTS test_fail_request_insert"); }
        assertThat(enroll("student1",1,"rollback").getStatusCode().value()).isEqualTo(200);
    }
    @Test void databaseUniqueConstraintAllowsHistoryButRejectsTwoActiveRelations() throws Exception {
        long selected=id(enroll("student1",1,"unique"));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update(
                "INSERT INTO enrollment(student_id,class_id,status,enrolled_at) VALUES(1,1,'ACTIVE',UTC_TIMESTAMP(6))"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        request("student1",HttpMethod.DELETE,"/api/enrollments/"+selected,null,null);
        assertThat(enroll("student1",1,"after-unique").getStatusCode().value()).isEqualTo(200);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE student_id=1 AND class_id=1")).isEqualTo(2);
    }
    @Test void inactiveStudentCannotSelectAndRequestKeysAreValidated() throws Exception {
        error(enroll("student1",1,"invalid key"),400,"VALIDATION_ERROR");
        jdbc.update("UPDATE student SET active=FALSE WHERE id=3");
        try { error(enroll("student3",3,"inactive"),409,"STUDENT_INACTIVE"); }
        finally { jdbc.update("UPDATE student SET active=TRUE WHERE id=3"); }
        assertThat(scalar("SELECT COUNT(*) FROM enrollment")).isZero();
    }
    @Test void repeatedGradePutIsIdempotentAndWithdrawalCannotRaceAClosedClassGrade() throws Exception {
        long enrollmentId=id(enroll("student1",1,"grade-race"));
        jdbc.update("UPDATE teaching_class SET status='CLOSED' WHERE id=1");
        var responses=concurrently(
                ()->request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",85),null),
                ()->request("student1",HttpMethod.DELETE,"/api/enrollments/"+enrollmentId,null,null));
        assertThat(responses.get(0).getStatusCode().value()).isEqualTo(200);
        assertThat(responses.get(1).getStatusCode().value()).isEqualTo(409);
        var repeated=request("teacher1",HttpMethod.PUT,"/api/enrollments/"+enrollmentId+"/score",Map.of("score",85),null);
        assertThat(repeated.getStatusCode().value()).isEqualTo(200);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE status='ACTIVE' AND score=85")).isEqualTo(1);
        assertThat(scalar("SELECT enrolled_count FROM teaching_class WHERE id=1")).isEqualTo(1);
    }
    @Test void creditLimitAllowsExactUpperBoundaryAndRejectsNextCourse() throws Exception {
        jdbc.update("UPDATE student SET credit_limit=6 WHERE id=1");
        assertThat(enroll("student1",1,"credit-three").getStatusCode().value()).isEqualTo(200);
        assertThat(enroll("student1",3,"credit-six").getStatusCode().value()).isEqualTo(200);
        error(enroll("student1",4,"credit-eight"),409,"CREDIT_LIMIT");
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE student_id=1 AND status='ACTIVE'")).isEqualTo(2);
    }
    @Test void unsupportedMethodAndMediaTypeRemainClientErrors() throws Exception {
        var method=request("student1",HttpMethod.POST,"/api/me",Map.of(),null);
        error(method,405,"METHOD_NOT_ALLOWED");
        assertThat(method.getHeaders().getAllow()).contains(HttpMethod.GET);
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.TEXT_PLAIN);
        var media=http.withBasicAuth("teacher1",PASSWORD).exchange("/api/enrollments/1/score",HttpMethod.PUT,new HttpEntity<>("85",headers),String.class);
        error(media,415,"UNSUPPORTED_MEDIA_TYPE");
    }
    @Test void selectionWindowIsLeftClosedRightOpenAndOldKeysReplayAfterDeadline() throws Exception {
        // A missing migration is an assertion failure in the RED baseline, not an invalid SQL error.
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_class' AND column_name='open_at'")).isEqualTo(1);
        jdbc.update("UPDATE teaching_class SET open_at='2026-10-31 10:00:00',close_at='2026-10-31 12:00:00' WHERE id=3");
        try {
            assertThat(classRepository.findById(3L).orElseThrow()).extracting("openAt").isEqualTo(Instant.parse("2026-10-31T10:00:00Z"));
            testClock.set(Instant.parse("2026-10-31T09:59:59.999999Z"));
            error(enroll("student1",3,"window-early"),409,"WINDOW_NOT_OPEN");
            testClock.set(Instant.parse("2026-10-31T10:00:00Z"));
            long atOpen=id(enroll("student1",3,"window-open"));
            assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(enrolled_at,'%Y-%m-%dT%H:%i:%s.%f') FROM enrollment WHERE id=?",String.class,atOpen))
                    .isEqualTo("2026-10-31T10:00:00.000000");
            testClock.set(Instant.parse("2026-10-31T09:59:59.999999Z"));
            error(request("student1",HttpMethod.DELETE,"/api/enrollments/"+atOpen,null,null),409,"WINDOW_NOT_OPEN");
            testClock.set(Instant.parse("2026-10-31T10:00:00Z"));
            assertThat(request("student1",HttpMethod.DELETE,"/api/enrollments/"+atOpen,null,null).getStatusCode().value()).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(withdrawn_at,'%Y-%m-%dT%H:%i:%s.%f') FROM enrollment WHERE id=?",String.class,atOpen))
                    .isEqualTo("2026-10-31T10:00:00.000000");
            testClock.set(Instant.parse("2026-10-31T11:59:59.999999Z"));
            long beforeClose=id(enroll("student1",3,"window-before-close"));
            assertThat(request("student1",HttpMethod.DELETE,"/api/enrollments/"+beforeClose,null,null).getStatusCode().value()).isEqualTo(204);
            var last=enroll("student1",3,"window-last");
            long lastId=id(last);
            testClock.set(Instant.parse("2026-10-31T12:00:00Z"));
            assertThat(enroll("student1",3,"window-last").getBody()).isEqualTo(last.getBody());
            error(enroll("student2",3,"window-deadline"),409,"WINDOW_CLOSED");
            error(request("student1",HttpMethod.DELETE,"/api/enrollments/"+lastId,null,null),409,"WINDOW_CLOSED");
            assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE class_id=3 AND status='ACTIVE'")).isEqualTo(1);
            assertThat(scalar("SELECT enrolled_count FROM teaching_class WHERE id=3")).isEqualTo(1);
        } finally {
            jdbc.update("UPDATE teaching_class SET open_at='2020-01-01 00:00:00',close_at='2100-01-01 00:00:00' WHERE id=3");
        }
    }
    /** Test-only controllable time: no production HTTP switch or clock override. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant=new AtomicReference<>(Instant.parse("2026-10-31T12:00:00Z"));
        void set(Instant next) { instant.set(next); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        @Override public Instant instant() { return instant.get(); }
    }
    @TestConfiguration static class TestClockConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @SafeVarargs final List<ResponseEntity<String>> concurrently(Callable<ResponseEntity<String>>... calls) throws Exception {
        var gate=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(calls.length);
        try {
            var futures=java.util.Arrays.stream(calls).map(call->pool.submit(()->{gate.await();return call.call();})).toList();
            gate.countDown();
            var results=new java.util.ArrayList<ResponseEntity<String>>();
            for(var future:futures) results.add(future.get(30,TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); }
    }
}
