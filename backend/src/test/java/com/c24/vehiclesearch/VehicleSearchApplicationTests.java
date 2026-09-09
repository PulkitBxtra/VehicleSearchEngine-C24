package com.c24.vehiclesearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full context against a throwaway Postgres, so `./mvnw test` passes
 * on a clean checkout with nothing running. Also exercises the Flyway
 * migrations and the seeder on every build.
 */
@SpringBootTest
@Testcontainers
class VehicleSearchApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void seedSmall(DynamicPropertyRegistry registry) {
        // Enough rows for the cohort medians behind deal_score to be meaningful,
        // few enough to keep the build quick.
        registry.add("search.seed.count", () -> 120);
    }

    @Test
    void contextLoads() {
    }
}
