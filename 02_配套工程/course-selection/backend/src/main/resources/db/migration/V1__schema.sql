CREATE TABLE student (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_no VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  credit_limit DECIMAL(4,1) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT ck_student_credit CHECK (credit_limit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teacher (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_no VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  credits DECIMAL(4,1) NOT NULL,
  CONSTRAINT ck_course_credits CHECK (credits > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  semester VARCHAR(30) NOT NULL,
  capacity INT NOT NULL,
  enrolled_count INT NOT NULL DEFAULT 0,
  day_of_week INT NOT NULL,
  start_period INT NOT NULL,
  end_period INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  CONSTRAINT fk_class_course FOREIGN KEY (course_id) REFERENCES course(id),
  CONSTRAINT fk_class_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
  CONSTRAINT ck_class_capacity CHECK (capacity > 0 AND enrolled_count >= 0 AND enrolled_count <= capacity),
  CONSTRAINT ck_class_slot CHECK (day_of_week BETWEEN 1 AND 7 AND start_period BETWEEN 1 AND 12 AND end_period BETWEEN start_period AND 12),
  CONSTRAINT ck_class_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE enrollment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  score DECIMAL(5,2) NULL,
  enrolled_at DATETIME(6) NOT NULL,
  withdrawn_at DATETIME(6) NULL,
  active_class_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN class_id ELSE NULL END) STORED,
  CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES student(id),
  CONSTRAINT fk_enrollment_class FOREIGN KEY (class_id) REFERENCES teaching_class(id),
  CONSTRAINT uq_enrollment_active UNIQUE (student_id,active_class_id),
  CONSTRAINT ck_enrollment_status CHECK (status IN ('ACTIVE','WITHDRAWN')),
  CONSTRAINT ck_enrollment_score CHECK (score IS NULL OR score BETWEEN 0 AND 100),
  CONSTRAINT ck_enrollment_withdrawal CHECK ((status='ACTIVE' AND withdrawn_at IS NULL) OR (status='WITHDRAWN' AND withdrawn_at IS NOT NULL)),
  INDEX ix_enrollment_class_status (class_id,status),
  INDEX ix_enrollment_student_status (student_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE enrollment_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  request_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  class_id BIGINT NOT NULL,
  enrollment_id BIGINT NOT NULL,
  response_json TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_enrollment_request UNIQUE (student_id,request_key),
  CONSTRAINT fk_request_student FOREIGN KEY (student_id) REFERENCES student(id),
  CONSTRAINT fk_request_class FOREIGN KEY (class_id) REFERENCES teaching_class(id),
  CONSTRAINT fk_request_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE app_user (
  username VARCHAR(40) PRIMARY KEY,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL,
  person_id BIGINT NOT NULL,
  CONSTRAINT ck_app_user_role CHECK (role IN ('STUDENT','TEACHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
