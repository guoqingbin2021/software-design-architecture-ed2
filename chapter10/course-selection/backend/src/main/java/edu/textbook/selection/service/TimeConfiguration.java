package edu.textbook.selection.service;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class TimeConfiguration {
    @org.springframework.context.annotation.Bean
    org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
            strictNumbers() {
        return builder ->
                builder.postConfigurer(
                        mapper ->
                                mapper.coercionConfigFor(
                                                com.fasterxml.jackson.databind.type.LogicalType
                                                        .Integer)
                                        .setCoercion(
                                                com.fasterxml.jackson.databind.cfg
                                                        .CoercionInputShape.String,
                                                com.fasterxml.jackson.databind.cfg.CoercionAction
                                                        .Fail));
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
