package com.example.enrollment.infrastructure;

import com.example.enrollment.application.AfterEnrollmentWrite;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AfterEnrollmentWrite afterEnrollmentWrite() {
        return () -> {};
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictJson() {
        return builder ->
                builder.postConfigurer(
                        mapper -> {
                            mapper.coercionConfigFor(LogicalType.Integer)
                                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                            var text = mapper.coercionConfigFor(LogicalType.Textual);
                            text.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                            text.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                            text.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                        });
    }
}
