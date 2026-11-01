-- A closed class with one active ungraded student for the teacher's grading demonstration.
INSERT INTO enrollment(id,student_id,class_id,status,score,enrolled_at,withdrawn_at)
VALUES(1,3,5,'ACTIVE',NULL,'2026-10-01 00:00:00',NULL);
UPDATE teaching_class SET enrolled_count=1 WHERE id=5;
