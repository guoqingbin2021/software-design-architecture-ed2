CREATE TABLE enrollment (
    id BIGINT auto_increment PRIMARY KEY,
    student_id BIGINT not NULL,
    class_id BIGINT not NULL,
    status VARCHAR(16) not NULL,
    enrolled_at DATETIME(6) not NULL,
    withdrawn_at DATETIME(6),
    score_value DECIMAL(5, 2),
    deleted BOOLEAN default FALSE not NULL,
    deleted_at DATETIME(6),
    CONSTRAINT fk_enroll_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_enroll_class FOREIGN KEY (class_id) REFERENCES teaching_class (id),
    CONSTRAINT uk_enroll_student_class UNIQUE (student_id, class_id),
    CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    CHECK (
        score_value is null
        OR score_value BETWEEN 0 AND 100
    ),
    CHECK (
        deleted = FALSE
        OR status = 'WITHDRAWN'
    )
) engine = InnoDB;
