CREATE TABLE student (
    id BIGINT NOT NULL PRIMARY KEY,
    student_number VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE teacher (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE course (
    id BIGINT NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE teaching_class (
    id BIGINT NOT NULL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    teacher_id BIGINT NULL,
    semester VARCHAR(5) NOT NULL,
    capacity INT NOT NULL,
    room VARCHAR(100) NULL,
    week_display VARCHAR(32) NULL,
    time_display VARCHAR(100) NULL,
    CONSTRAINT ck_class_capacity CHECK (capacity >= 0),
    CONSTRAINT fk_class_course FOREIGN KEY(course_id) REFERENCES course(id),
    CONSTRAINT fk_class_teacher FOREIGN KEY(teacher_id) REFERENCES teacher(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE enrollment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_enrollment_student_class UNIQUE(student_id,class_id),
    CONSTRAINT ck_enrollment_status CHECK(status IN ('ACTIVE','WITHDRAWN')),
    CONSTRAINT fk_enrollment_student FOREIGN KEY(student_id) REFERENCES student(id),
    CONSTRAINT fk_enrollment_class FOREIGN KEY(class_id) REFERENCES teaching_class(id),
    INDEX ix_active_enrollment(class_id,status,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=1001;
CREATE TABLE enrollment_request_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    request_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    class_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    response_status VARCHAR(16) NOT NULL,
    response_course_name VARCHAR(200) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_request_student_key UNIQUE(student_id,request_key),
    CONSTRAINT fk_request_student FOREIGN KEY(student_id) REFERENCES student(id),
    CONSTRAINT fk_request_class FOREIGN KEY(class_id) REFERENCES teaching_class(id),
    CONSTRAINT fk_request_enrollment FOREIGN KEY(enrollment_id) REFERENCES enrollment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
