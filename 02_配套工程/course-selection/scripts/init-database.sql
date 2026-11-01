-- Run once as administrator on a dedicated teaching MySQL 8.0 instance.
-- Business schema and all fixed data are imported by Flyway, not this file.
SET GLOBAL log_bin_trust_function_creators=1;
CREATE DATABASE IF NOT EXISTS course_demo CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS course_test CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'course_app'@'localhost' IDENTIFIED BY 'Chapter10Db!';
CREATE USER IF NOT EXISTS 'course_app'@'127.0.0.1' IDENTIFIED BY 'Chapter10Db!';
GRANT ALL PRIVILEGES ON course_demo.* TO 'course_app'@'localhost';
GRANT ALL PRIVILEGES ON course_test.* TO 'course_app'@'localhost';
GRANT ALL PRIVILEGES ON course_demo.* TO 'course_app'@'127.0.0.1';
GRANT ALL PRIVILEGES ON course_test.* TO 'course_app'@'127.0.0.1';
SELECT VERSION(), @@transaction_isolation, @@log_bin_trust_function_creators;
