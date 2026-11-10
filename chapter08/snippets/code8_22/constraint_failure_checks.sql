-- 前提：已存在有效记录1001，不存在学生999
-- 以下两项分别执行；每次插入失败后执行ROLLBACK
START TRANSACTION;

INSERT INTO
    enrollment (
        student_id,
        class_id,
        status,
        enrolled_at,
        deleted
    )
SELECT
    student_id,
    class_id,
    status,
    enrolled_at,
    deleted
FROM
    enrollment
WHERE
    id = 1001;

-- 预期：违反(student_id, class_id)联合唯一约束
ROLLBACK;

START TRANSACTION;

INSERT INTO
    enrollment (
        student_id,
        class_id,
        status,
        enrolled_at,
        deleted
    )
SELECT
    999,
    class_id,
    status,
    enrolled_at,
    deleted
FROM
    enrollment
WHERE
    id = 1001;

-- 预期：违反student_id外键约束
ROLLBACK;

START TRANSACTION;

SELECT
    COUNT(*) AS active_count
FROM
    enrollment
WHERE
    class_id = 501
    AND status = 'ACTIVE'
    AND deleted = FALSE;

-- 预期：active_count为1
COMMIT;
