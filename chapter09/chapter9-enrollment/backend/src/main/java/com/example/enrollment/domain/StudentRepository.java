package com.example.enrollment.domain;

import java.util.Optional;

public interface StudentRepository {
    Optional<Student> findForUpdate(long id);
}
