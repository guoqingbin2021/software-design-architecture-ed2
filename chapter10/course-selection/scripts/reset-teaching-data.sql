-- 本脚本只用于恢复course_demo中的虚构教学数据；执行前停止后端。
-- 表结构须已由Flyway V1—V5建立。本脚本不改表结构及迁移记录。
USE course_demo;

START TRANSACTION;

DELETE FROM enrollment_request;

DELETE FROM enrollment;

DELETE FROM app_user;

DELETE FROM teaching_class;

DELETE FROM course;

DELETE FROM teacher;

DELETE FROM student;

-- Teaching credentials only: BCrypt(Chapter10!), cost 10.
INSERT INTO
    student (id, student_no, name, credit_limit, active)
VALUES
    (1, 'S2026001', '张敏', 12.0, TRUE),
    (2, 'S2026002', '李明', 12.0, TRUE),
    (3, 'S2026003', '王芳', 12.0, TRUE);

INSERT INTO
    teacher (id, teacher_no, name)
VALUES
    (1, 'T001', '陈老师'),
    (2, 'T002', '刘老师');

INSERT INTO
    app_user (username, password_hash, role, person_id)
VALUES
    (
        'student1',
        '$2a$10$gs9eWu.JBsLi16C3K6oWG.zsbivUcznQ7cRqmwgxr38tX/sLJmmyW',
        'STUDENT',
        1
    ),
    (
        'student2',
        '$2a$10$gs9eWu.JBsLi16C3K6oWG.zsbivUcznQ7cRqmwgxr38tX/sLJmmyW',
        'STUDENT',
        2
    ),
    (
        'student3',
        '$2a$10$gs9eWu.JBsLi16C3K6oWG.zsbivUcznQ7cRqmwgxr38tX/sLJmmyW',
        'STUDENT',
        3
    ),
    (
        'teacher1',
        '$2a$10$gs9eWu.JBsLi16C3K6oWG.zsbivUcznQ7cRqmwgxr38tX/sLJmmyW',
        'TEACHER',
        1
    ),
    (
        'teacher2',
        '$2a$10$gs9eWu.JBsLi16C3K6oWG.zsbivUcznQ7cRqmwgxr38tX/sLJmmyW',
        'TEACHER',
        2
    );

INSERT INTO
    course (id, course_code, name, credits)
VALUES
    (1, 'SE301', '软件设计与体系结构', 3.0),
    (2, 'CS302', '数据库原理', 3.0),
    (3, 'SE303', 'Web应用开发', 3.0),
    (4, 'SE304', '软件测试', 2.0),
    (5, 'CS305', '计算机网络', 3.0);

INSERT INTO
    teaching_class (
        id,
        course_id,
        teacher_id,
        semester,
        capacity,
        enrolled_count,
        day_of_week,
        start_period,
        end_period,
        status
    )
VALUES
    (1, 1, 1, '2026-2027-1', 1, 0, 1, 1, 2, 'OPEN'),
    (2, 2, 1, '2026-2027-1', 30, 0, 1, 2, 3, 'OPEN'),
    (3, 3, 1, '2026-2027-1', 30, 0, 2, 1, 2, 'OPEN'),
    (4, 4, 2, '2026-2027-1', 30, 0, 3, 1, 2, 'OPEN'),
    (5, 5, 2, '2026-2027-1', 30, 0, 4, 1, 2, 'CLOSED');

-- Additional fixture: same course as class 1, different time.
INSERT INTO
    teaching_class (
        id,
        course_id,
        teacher_id,
        semester,
        capacity,
        enrolled_count,
        day_of_week,
        start_period,
        end_period,
        status
    )
VALUES
    (6, 1, 1, '2026-2027-1', 30, 0, 5, 1, 2, 'OPEN');

-- A closed class with one active ungraded student for the teacher's grading demonstration.
INSERT INTO
    enrollment (
        id,
        student_id,
        class_id,
        status,
        score,
        enrolled_at,
        withdrawn_at
    )
VALUES
    (
        1,
        3,
        5,
        'ACTIVE',
        NULL,
        '2026-10-01 00:00:00',
        NULL
    );

UPDATE teaching_class
SET
    enrolled_count = 1
WHERE
    id = 5;

COMMIT;
