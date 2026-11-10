package edu.textbook.selection;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** 习题10-10补充：继承原测试的隔离数据、固定时钟及真实HTTP辅助方法。 */
class HistoryIsolationAnswerTest extends CourseSelectionIntegrationTest {
    @Test
    void retryingOldDeleteDoesNotWithdrawNewEnrollment() throws Exception {
        var first = enroll("student1", 1, "answer-old");
        long oldId = id(first);
        assertThat(
                        request(
                                        "student1",
                                        HttpMethod.DELETE,
                                        "/api/enrollments/" + oldId,
                                        null,
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(204);
        long newId = id(enroll("student1", 1, "answer-new"));
        assertThat(newId).isNotEqualTo(oldId);
        assertThat(
                        request(
                                        "student1",
                                        HttpMethod.DELETE,
                                        "/api/enrollments/" + oldId,
                                        null,
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(204);
        assertThat(scalar("SELECT COUNT(*) FROM enrollment WHERE status='ACTIVE'")).isEqualTo(1);
        assertThat(scalar("SELECT enrolled_count FROM teaching_class WHERE id=1")).isEqualTo(1);
        var timetable =
                body(request("student1", HttpMethod.GET, "/api/enrollments/me", null, null));
        assertThat(timetable).hasSize(1);
        assertThat(timetable.get(0).get("id").asLong()).isEqualTo(newId);
        assertThat(enroll("student1", 1, "answer-old").getBody()).isEqualTo(first.getBody());
    }
}
