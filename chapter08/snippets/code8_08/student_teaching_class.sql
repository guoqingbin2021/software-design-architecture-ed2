CREATE TABLE student (
    id BIGINT auto_increment PRIMARY KEY,
    student_no VARCHAR(20) not NULL UNIQUE,
    name VARCHAR(40) not NULL
) engine = InnoDB;

CREATE TABLE teaching_class (
    id BIGINT auto_increment PRIMARY KEY,
    semester VARCHAR(20) not NULL,
    capacity INTEGER not NULL CHECK (capacity >= 0)
) engine = InnoDB;
