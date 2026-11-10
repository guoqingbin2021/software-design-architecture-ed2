-- 习题10-9：只读，连接实际第十章独立教学库执行。第十章没有deleted字段。
-- 1 保存人数、有效记录数与容量；正常数据返回空。
SELECT
    tc.id,
    tc.capacity,
    tc.enrolled_count AS saved_count,
    COUNT(e.id) AS actual_count
FROM
    teaching_class tc
    LEFT JOIN enrollment e ON e.class_id = tc.id
    AND e.status = 'ACTIVE'
GROUP BY
    tc.id,
    tc.capacity,
    tc.enrolled_count
HAVING
    tc.enrolled_count <> COUNT(e.id)
    OR COUNT(e.id) > tc.capacity;

-- 2 同一学生同班不能同时有两条有效关系。
SELECT
    student_id,
    class_id,
    COUNT(*) AS active_count
FROM
    enrollment
WHERE
    status = 'ACTIVE'
GROUP BY
    student_id,
    class_id
HAVING
    COUNT(*) > 1;

-- 3 同一学生同学期不能同时选入同一课程的多个教学班。
SELECT
    e.student_id,
    tc.semester,
    tc.course_id,
    COUNT(*) AS active_count
FROM
    enrollment e
    JOIN teaching_class tc ON tc.id = e.class_id
WHERE
    e.status = 'ACTIVE'
GROUP BY
    e.student_id,
    tc.semester,
    tc.course_id
HAVING
    COUNT(*) > 1;
