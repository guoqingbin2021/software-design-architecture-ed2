-- 习题8-10。MySQL 8.0，同一连接执行整份脚本。
-- 仅建立answer_前缀的会话临时表；连接关闭后自动消失，不改工程表。
CREATE TEMPORARY TABLE answer_student (id BIGINT PRIMARY KEY, name VARCHAR(80));

CREATE TEMPORARY TABLE answer_class (id BIGINT PRIMARY KEY, capacity INT NOT NULL);

CREATE TEMPORARY TABLE answer_enrollment (
    id BIGINT PRIMARY KEY,
    student_id BIGINT,
    class_id BIGINT,
    status VARCHAR(16),
    deleted BOOLEAN,
    enrolled_at DATETIME(6)
);

INSERT INTO
    answer_student
VALUES
    (101, '张敏'),
    (102, '李明'),
    (103, '王宁'),
    (104, '示例104'),
    (105, '示例105');

INSERT INTO
    answer_class
VALUES
    (501, 30),
    (502, 1);

INSERT INTO
    answer_enrollment
VALUES
    (
        1001,
        101,
        501,
        'ACTIVE',
        FALSE,
        '2026-09-01 01:00:00'
    ),
    (
        1002,
        102,
        501,
        'ACTIVE',
        FALSE,
        '2026-09-01 01:00:00'
    ),
    (
        1003,
        103,
        501,
        'ACTIVE',
        FALSE,
        '2026-09-01 01:01:00'
    ),
    (
        1004,
        104,
        501,
        'WITHDRAWN',
        FALSE,
        '2026-09-01 01:00:00'
    ),
    (
        1005,
        105,
        501,
        'WITHDRAWN',
        TRUE,
        '2026-09-01 01:00:00'
    );

SELECT
    id
FROM
    answer_enrollment
WHERE
    class_id = 501
    AND status = 'ACTIVE'
    AND deleted = FALSE
ORDER BY
    enrolled_at DESC,
    id DESC
LIMIT
    2
OFFSET
    0;

SELECT
    id
FROM
    answer_enrollment
WHERE
    class_id = 501
    AND status = 'ACTIVE'
    AND deleted = FALSE
ORDER BY
    enrolled_at DESC,
    id DESC
LIMIT
    2
OFFSET
    2;

SELECT
    id
FROM
    answer_enrollment
WHERE
    class_id = 501
    AND status = 'ACTIVE'
    AND deleted = FALSE
ORDER BY
    enrolled_at DESC,
    id DESC
LIMIT
    2
OFFSET
    4;

SELECT
    COUNT(*) AS total
FROM
    answer_enrollment
WHERE
    class_id = 501
    AND status = 'ACTIVE'
    AND deleted = FALSE;

-- 初始逐班人数包括空班502。
SELECT
    tc.id,
    tc.capacity,
    COUNT(e.id) AS actual_count
FROM
    answer_class tc
    LEFT JOIN answer_enrollment e ON e.class_id = tc.id
    AND e.status = 'ACTIVE'
    AND e.deleted = FALSE
GROUP BY
    tc.id,
    tc.capacity
ORDER BY
    tc.id;

SELECT
    tc.id,
    tc.capacity,
    COUNT(e.id) AS actual_count
FROM
    answer_class tc
    LEFT JOIN answer_enrollment e ON e.class_id = tc.id
    AND e.status = 'ACTIVE'
    AND e.deleted = FALSE
GROUP BY
    tc.id,
    tc.capacity
HAVING
    COUNT(e.id) > tc.capacity;

UPDATE answer_class
SET
    capacity = 0
WHERE
    id = 502;

INSERT INTO
    answer_enrollment
VALUES
    (
        1006,
        101,
        502,
        'ACTIVE',
        FALSE,
        '2026-09-01 01:03:00'
    );

-- 预期502,0,1。
SELECT
    tc.id,
    tc.capacity,
    COUNT(e.id) AS actual_count
FROM
    answer_class tc
    LEFT JOIN answer_enrollment e ON e.class_id = tc.id
    AND e.status = 'ACTIVE'
    AND e.deleted = FALSE
GROUP BY
    tc.id,
    tc.capacity
HAVING
    COUNT(e.id) > tc.capacity;

DELETE FROM answer_enrollment
WHERE
    id = 1006;

UPDATE answer_class
SET
    capacity = 1
WHERE
    id = 502;

SELECT
    tc.id,
    tc.capacity,
    COUNT(e.id) AS actual_count
FROM
    answer_class tc
    LEFT JOIN answer_enrollment e ON e.class_id = tc.id
    AND e.status = 'ACTIVE'
    AND e.deleted = FALSE
GROUP BY
    tc.id,
    tc.capacity
HAVING
    COUNT(e.id) > tc.capacity;
