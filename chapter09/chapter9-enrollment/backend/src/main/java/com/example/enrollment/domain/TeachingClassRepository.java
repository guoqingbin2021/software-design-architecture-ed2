package com.example.enrollment.domain;

import java.util.Optional;

public interface TeachingClassRepository {
    Optional<TeachingClass> findForUpdate(long id);
}
