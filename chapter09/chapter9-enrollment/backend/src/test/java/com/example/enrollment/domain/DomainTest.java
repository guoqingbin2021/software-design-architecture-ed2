package com.example.enrollment.domain;

import com.example.enrollment.application.InputPolicy;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class DomainTest {
    private final Course course = new Course(301, "SE301", "软件设计与体系结构");
    private final Student student = new Student(101, "20260001", "张敏");

    @Test
    void capacityHasExactBoundary() {
        TeachingClass c = new TeachingClass(501, course, "2026秋", 30);
        c.ensureAvailable(29);
        assertThatThrownBy(() -> c.ensureAvailable(30))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BusinessException.Code.CLASS_FULL));
        assertThatThrownBy(() -> c.ensureAvailable(31)).isInstanceOf(BusinessException.class);
    }

    @Test
    void withdrawalIsRepeatableAndReactivationKeepsSameRelation() {
        var c = new TeachingClass(501, course, "2026秋", 30);
        var t = Instant.parse("2026-01-01T00:00:00Z");
        Enrollment e = Enrollment.create(student, c, t);
        assertThat(e.isActive()).isTrue();
        e.withdraw(t.plusSeconds(1));
        e.withdraw(t.plusSeconds(2));
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(e.getUpdatedAt()).isEqualTo(t.plusSeconds(1));
        e.activate(t.plusSeconds(3));
        assertThat(e.isActive()).isTrue();
        assertThat(e.getStudent()).isSameAs(student);
        assertThat(e.getTeachingClass()).isSameAs(c);
    }

    @Test
    void identifiersRejectOverflowAndNoncanonicalForms() {
        assertThat(InputPolicy.id("501", "teachingClassId")).isEqualTo(501);
        for (String value :
                new String[] {
                    "0", "-1", "0501", " 501", "9223372036854775808", "9007199254740992", "1.0"
                }) {
            assertThatThrownBy(() -> InputPolicy.id(value, "teachingClassId"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void relationshipEnforcesItsOwner() {
        Enrollment e =
                Enrollment.create(
                        student, new TeachingClass(501, course, "2026秋", 30), Instant.EPOCH);
        e.assertOwnedBy(101);
        assertThatThrownBy(() -> e.assertOwnedBy(102))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void keysUseBoundedAsciiAndSemesterFollowsChapterFormat() {
        InputPolicy.key("11111111-1111-4111-8111-111111111111");
        InputPolicy.key("EXAMPLE-enroll_001.v1");
        assertThatThrownBy(() -> InputPolicy.key("contains space"))
                .isInstanceOf(BusinessException.class);
        assertThat(InputPolicy.semester("2026秋")).isEqualTo("2026秋");
        assertThatThrownBy(() -> InputPolicy.semester("2026秋季"))
                .isInstanceOf(BusinessException.class);
    }
}
