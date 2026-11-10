-- 习题8-9：对应第八章简化表；连接相应独立教学库后执行。
-- 分页第一页；第二、三页依次改为 OFFSET 2、OFFSET 4。
SELECT
    e.id,
    e.student_id,
    s.name,
    e.enrolled_at
FROM
    enrollment e
    JOIN student s ON s.id = e.student_id
WHERE
    e.class_id = 501
    AND e.status = 'ACTIVE'
    AND e.deleted = FALSE
ORDER BY
    e.enrolled_at DESC,
    e.id DESC
LIMIT
    2
OFFSET
    0;

SELECT
    COUNT(*) AS total
FROM
    enrollment
WHERE
    class_id = 501
    AND status = 'ACTIVE'
    AND deleted = FALSE;

-- 必须保留没有有效选课的教学班。第八章简化表没有enrolled_count。
SELECT
    tc.id,
    tc.capacity,
    COUNT(e.id) AS actual_count
FROM
    teaching_class tc
    LEFT JOIN enrollment e ON e.class_id = tc.id
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
    teaching_class tc
    LEFT JOIN enrollment e ON e.class_id = tc.id
    AND e.status = 'ACTIVE'
    AND e.deleted = FALSE
GROUP BY
    tc.id,
    tc.capacity
HAVING
    COUNT(e.id) > tc.capacity;
