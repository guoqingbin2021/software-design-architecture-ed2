-- 代码10.3：在全部请求结束后，用新的数据库连接查询班1。
-- 正常竞争后两项计数应为1；恢复初始数据或故障回滚后应为0。
SELECT
    c.id,
    c.capacity,
    c.enrolled_count,
    COUNT(e.id) AS active_count
FROM
    teaching_class c
    LEFT JOIN enrollment e ON e.class_id = c.id
    AND e.status = 'ACTIVE'
WHERE
    c.id = 1
GROUP BY
    c.id,
    c.capacity,
    c.enrolled_count;
