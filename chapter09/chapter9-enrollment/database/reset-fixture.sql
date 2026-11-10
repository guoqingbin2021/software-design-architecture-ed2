-- ONLY for the disposable chapter9 teaching database. Stop the backend before running.
USE enrollment_ch9;

START TRANSACTION;

DELETE FROM enrollment_request_log;

DELETE FROM enrollment;

UPDATE teaching_class
SET
    capacity = 30,
    teacher_id = NULL,
    room = NULL,
    week_display = NULL,
    time_display = NULL
WHERE
    id = 501;

INSERT INTO
    enrollment (
        student_id,
        class_id,
        status,
        deleted,
        created_at,
        updated_at
    )
SELECT
    id,
    501,
    'ACTIVE',
    b'0',
    '2026-09-01 00:00:00',
    '2026-09-01 00:00:00'
FROM
    student
WHERE
    id BETWEEN 1001 AND 1029;

COMMIT;

-- IDs are intentionally NOT reset: clients must use the returned enrollmentId.
SELECT
    COUNT(*) AS expected_29
FROM
    enrollment
WHERE
    class_id = 501
    AND status = 'ACTIVE'
    AND deleted = b'0';
