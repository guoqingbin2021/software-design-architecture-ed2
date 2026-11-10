package com.example.enrollment.interfaces.http;

import com.example.enrollment.application.InputPolicy;
import jakarta.validation.constraints.*;

public record EnrollmentRequest(@NotNull @Positive @Max(9007199254740991L) Long teachingClassId) {
    public long teachingClassIdAsLong() {
        return teachingClassId;
    }
}
