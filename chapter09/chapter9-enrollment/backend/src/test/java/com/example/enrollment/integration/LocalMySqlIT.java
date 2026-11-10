package com.example.enrollment.integration;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.*;

/** Uses a disposable local MySQL database when Docker is unavailable; never point at production data. */
@EnabledIfEnvironmentVariable(named = "CH9_TEST_DB_URL", matches = ".+")
class LocalMySqlIT extends MySqlContract {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CH9_TEST_DB_URL"));
        registry.add(
                "spring.datasource.username",
                () -> System.getenv().getOrDefault("CH9_TEST_DB_USER", "ch9"));
        registry.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("CH9_TEST_DB_PASSWORD", "Chapter9Db!Demo"));
    }
}
