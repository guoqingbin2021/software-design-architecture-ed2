-- Run in a new mysql client connection after HTTP actions. Every result should be empty.
SELECT tc.id,tc.enrolled_count,COUNT(e.id) AS active_count
FROM teaching_class tc
LEFT JOIN enrollment e ON e.class_id=tc.id AND e.status='ACTIVE'
GROUP BY tc.id,tc.enrolled_count,tc.capacity
HAVING tc.enrolled_count<>COUNT(e.id) OR COUNT(e.id)>tc.capacity OR tc.enrolled_count<0;

SELECT student_id,class_id,COUNT(*) AS active_count
FROM enrollment WHERE status='ACTIVE'
GROUP BY student_id,class_id HAVING COUNT(*)>1;

SELECT e.student_id,tc.semester,tc.course_id,COUNT(*) AS active_course_count
FROM enrollment e JOIN teaching_class tc ON tc.id=e.class_id
WHERE e.status='ACTIVE'
GROUP BY e.student_id,tc.semester,tc.course_id HAVING COUNT(*)>1;

SELECT a.student_id,a.id AS first_enrollment,b.id AS second_enrollment
FROM enrollment a JOIN enrollment b ON a.student_id=b.student_id AND a.id<b.id
JOIN teaching_class ta ON ta.id=a.class_id
JOIN teaching_class tb ON tb.id=b.class_id
WHERE a.status='ACTIVE' AND b.status='ACTIVE' AND ta.semester=tb.semester
AND ta.day_of_week=tb.day_of_week AND ta.start_period<=tb.end_period AND tb.start_period<=ta.end_period;

SELECT e.student_id,tc.semester,SUM(c.credits) AS credits,MAX(s.credit_limit) AS credit_limit
FROM enrollment e JOIN teaching_class tc ON tc.id=e.class_id
JOIN course c ON c.id=tc.course_id JOIN student s ON s.id=e.student_id
WHERE e.status='ACTIVE' GROUP BY e.student_id,tc.semester
HAVING SUM(c.credits)>MAX(s.credit_limit);

SELECT id,score FROM enrollment WHERE score<0 OR score>100;
