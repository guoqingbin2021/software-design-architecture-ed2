package com.example.enrollment.integration;

import com.example.enrollment.application.*;
import com.example.enrollment.domain.BusinessException;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** All tests run against InnoDB through the real Spring transaction proxy and HTTP security chain. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class MySqlContract {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired EnrollmentUseCases service;
    @MockBean AfterEnrollmentWrite afterWrite;
    static final String CLASS_JSON = "{\"teachingClassId\":501}";

    record Session(MockHttpSession http, String token, String header) {}

    @BeforeEach
    void resetFixture() {
        reset(afterWrite);
        jdbc.update("delete from enrollment_request_log");
        jdbc.update("delete from enrollment");
        jdbc.update(
                "update teaching_class set capacity=30,teacher_id=null,room=null,week_display=null,time_display=null where id=501");
        jdbc.update(
                "insert into enrollment(student_id,class_id,status,deleted,created_at,updated_at) select id,501,'ACTIVE',b'0','2026-09-01','2026-09-01' from student where id between 1001 and 1029");
    }

    Session login(String username) throws Exception {
        String token =
                "Basic "
                        + Base64.getEncoder()
                                .encodeToString(
                                        (username + ":Chapter9!Demo")
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/api/me").header("Authorization", token)).andExpect(status().isOk());
        return new Session(new MockHttpSession(), token, "Authorization");
    }

    ResultActions enroll(Session s, String key, String json) throws Exception {
        return mvc.perform(
                post("/api/enrollments")
                        .header(s.header(), s.token())
                        .header(s.header(), s.token())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(json));
    }

    ResultActions withdraw(Session s, String id) throws Exception {
        return mvc.perform(
                delete("/api/enrollments/{id}", id)
                        .header(s.header(), s.token())
                        .header(s.header(), s.token()));
    }

    JsonNode result(ResultActions action) throws Exception {
        return mapper.readTree(action.andReturn().getResponse().getContentAsString());
    }

    long active() {
        return jdbc.queryForObject(
                "select count(*) from enrollment where class_id=501 and status='ACTIVE' and deleted=b'0'",
                Long.class);
    }

    long logCount() {
        return jdbc.queryForObject("select count(*) from enrollment_request_log", Long.class);
    }

    @Test
    void basicIdentityIsStatelessAndDoesNotCreateSession() throws Exception {
        Session s = login("student101");
        MvcResult result =
                mvc.perform(get("/api/me").header(s.header(), s.token()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.personId").value(101))
                        .andExpect(jsonPath("$.role").value("STUDENT"))
                        .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void explicitBasicHeaderAuthenticatesWritesWithoutCsrfCookie() throws Exception {
        MvcResult first =
                mvc.perform(
                                post("/api/enrollments")
                                        .contentType("application/json")
                                        .content(CLASS_JSON))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                        .andReturn();
        JsonNode error = mapper.readTree(first.getResponse().getContentAsString());
        assertThat(error.get("fields").isObject()).isTrue();
        assertThat(error.get("requestId").asText())
                .isEqualTo(first.getResponse().getHeader("X-Request-Id"));
        enroll(login("student101"), "example-enroll-001", CLASS_JSON).andExpect(status().isOk());
        assertThat(active()).isEqualTo(30);
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void teacherCannotUseStudentApi() throws Exception {
        Session teacher = login("teacher201");
        enroll(teacher, UUID.randomUUID().toString(), CLASS_JSON).andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/enrollments/me")
                                .param("semester", "2026秋")
                                .header(teacher.header(), teacher.token()))
                .andExpect(status().isForbidden());
        assertThat(active()).isEqualTo(29);
    }

    @Test
    void enrollmentTimetableWithdrawalAndReactivationCommit() throws Exception {
        Session s = login("student101");
        String key = UUID.randomUUID().toString();
        JsonNode e =
                result(
                        enroll(s, key, CLASS_JSON)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("ACTIVE")));
        String id = e.get("id").asText();
        assertThat(active()).isEqualTo(30);
        mvc.perform(
                        get("/api/enrollments/me")
                                .header(s.header(), s.token())
                                .param("semester", "2026秋"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(Long.parseLong(id)))
                .andExpect(jsonPath("$[0].teachingClassId").value(501))
                .andExpect(jsonPath("$[0].teacherName").isEmpty());
        withdraw(s, id).andExpect(status().isNoContent()).andExpect(content().string(""));
        withdraw(s, id).andExpect(status().isNoContent());
        assertThat(active()).isEqualTo(29);
        JsonNode replay = result(enroll(s, key, CLASS_JSON).andExpect(status().isOk()));
        assertThat(replay).isEqualTo(e);
        assertThat(active()).isEqualTo(29); // replay is historical, not a new enrollment
        JsonNode reactivated =
                result(
                        enroll(s, UUID.randomUUID().toString(), CLASS_JSON)
                                .andExpect(status().isOk()));
        assertThat(reactivated.get("id").asText()).isEqualTo(id);
        assertThat(active()).isEqualTo(30);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from enrollment where student_id=101 and class_id=501",
                                Long.class))
                .isEqualTo(1);
    }

    @Test
    void ownershipAndUnauthenticatedWithdrawalHaveNoSideEffects() throws Exception {
        Session owner = login("student101"), other = login("student102");
        String id =
                result(
                                enroll(owner, UUID.randomUUID().toString(), CLASS_JSON)
                                        .andExpect(status().isOk()))
                        .get("id")
                        .asText();
        withdraw(other, id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/enrollments/{id}", id)).andExpect(status().isUnauthorized());
        assertThat(active()).isEqualTo(30);
        mvc.perform(
                        get("/api/enrollments/me")
                                .param("semester", "2026秋")
                                .header(other.header(), other.token()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        withdraw(owner, id).andExpect(status().isNoContent());
        assertThat(active()).isEqualTo(29);
    }

    @Test
    void confirmationReplaysOriginalSnapshotAndRejectsChangedKeyInput() throws Exception {
        Session owner = login("student101"), other = login("student102");
        String key = UUID.randomUUID().toString();
        JsonNode original = result(enroll(owner, key, CLASS_JSON).andExpect(status().isOk()));
        withdraw(owner, original.get("id").asText()).andExpect(status().isNoContent());
        jdbc.update("update course set name='Changed temporarily' where id=301");
        try {
            JsonNode confirmed =
                    result(
                            mvc.perform(
                                            get("/api/enrollment-requests/{key}", key)
                                                    .header(owner.header(), owner.token()))
                                    .andExpect(status().isOk())
                                    .andExpect(header().string("Cache-Control", "no-store")));
            assertThat(confirmed).isEqualTo(original);
        } finally {
            jdbc.update("update course set name='软件设计与体系结构' where id=301");
        }
        enroll(owner, key, "{\"teachingClassId\":999}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(
                        get("/api/enrollment-requests/{key}", key)
                                .header(other.header(), other.token()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/enrollment-requests/{key}", UUID.randomUUID())
                                .header(owner.header(), owner.token()))
                .andExpect(status().isNotFound());
        assertThat(active()).isEqualTo(29);
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void strictNumericIdsAndFlexibleBoundedKeys() throws Exception {
        Session s = login("student101");
        for (String body :
                List.of(
                        "{\"teachingClassId\":\"501\"}",
                        "{\"teachingClassId\":true}",
                        "{\"teachingClassId\":501,\"studentId\":102}",
                        "{}",
                        "{\"teachingClassId\":9007199254740992}",
                        "{\"teachingClassId\":501.5}")) {
            enroll(s, UUID.randomUUID().toString(), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        enroll(s, "bad key with spaces", CLASS_JSON).andExpect(status().isBadRequest());
        enroll(s, "x".repeat(81), CLASS_JSON).andExpect(status().isBadRequest());
        withdraw(s, "0501").andExpect(status().isBadRequest());
        enroll(s, UUID.randomUUID().toString(), "{\"teachingClassId\":999}")
                .andExpect(status().isNotFound());
        assertThat(active()).isEqualTo(29);
        assertThat(logCount()).isZero();
        enroll(s, "EXAMPLE-enroll_001.v1", CLASS_JSON).andExpect(status().isOk());
    }

    @Test
    void deletedRowsAreInvisibleAndNotRecreated() throws Exception {
        Session s = login("student101");
        String id =
                result(
                                enroll(s, UUID.randomUUID().toString(), CLASS_JSON)
                                        .andExpect(status().isOk()))
                        .get("id")
                        .asText();
        jdbc.update("update enrollment set deleted=b'1' where id=?", Long.parseLong(id));
        mvc.perform(
                        get("/api/enrollments/me")
                                .param("semester", "2026秋")
                                .header(s.header(), s.token()))
                .andExpect(content().json("[]"));
        withdraw(s, id).andExpect(status().isNotFound());
        enroll(s, UUID.randomUUID().toString(), CLASS_JSON).andExpect(status().isNotFound());
        assertThat(active()).isEqualTo(29);
    }

    @Test
    void exceptionAfterSqlWritesRollsBackEnrollmentAndRequestLog() throws Exception {
        Session s = login("student101");
        String key = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("injected failure after flush, before commit"))
                .when(afterWrite)
                .beforeCommit();
        enroll(s, key, CLASS_JSON)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(active()).isEqualTo(29);
        assertThat(logCount()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from enrollment where student_id=101", Long.class))
                .isZero();
        mvc.perform(get("/api/enrollment-requests/{key}", key).header(s.header(), s.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentStudentsCompeteForLastPlace() throws Exception {
        List<Object> outcomes =
                race(
                        () -> service.enroll(101, 501, UUID.randomUUID().toString()),
                        () -> service.enroll(102, 501, UUID.randomUUID().toString()));
        assertThat(outcomes.stream().filter(EnrollmentDto.class::isInstance).count()).isEqualTo(1);
        assertThat(
                        outcomes.stream()
                                .filter(
                                        x ->
                                                x instanceof BusinessException e
                                                        && e.getCode()
                                                                == BusinessException.Code
                                                                        .CLASS_FULL)
                                .count())
                .isEqualTo(1);
        assertThat(active()).isEqualTo(30);
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyAndNewKeyNeverDuplicateRelationship() throws Exception {
        String key = UUID.randomUUID().toString();
        List<Object> outcomes =
                race(() -> service.enroll(101, 501, key), () -> service.enroll(101, 501, key));
        assertThat(outcomes).allMatch(EnrollmentDto.class::isInstance);
        assertThat(outcomes.get(0)).isEqualTo(outcomes.get(1));
        assertThat(active()).isEqualTo(30);
        assertThat(logCount()).isEqualTo(1);
        EnrollmentDto again = service.enroll(101, 501, UUID.randomUUID().toString());
        assertThat(again).isEqualTo(outcomes.get(0));
        assertThat(active()).isEqualTo(30);
        assertThat(logCount()).isEqualTo(2);
    }

    List<Object> race(Callable<EnrollmentDto> a, Callable<EnrollmentDto> b) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (var action : List.of(a, b))
                futures.add(
                        executor.submit(
                                () -> {
                                    barrier.await(10, TimeUnit.SECONDS);
                                    try {
                                        return action.call();
                                    } catch (BusinessException e) {
                                        return e;
                                    }
                                }));
            return List.of(
                    futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
