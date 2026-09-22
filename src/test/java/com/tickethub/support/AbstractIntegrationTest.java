package com.tickethub.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Every integration test runs against a real MySQL 8 container.
 * H2 is deliberately avoided: row locking, SKIP LOCKED and generated columns
 * behave differently there, which would make the concurrency tests meaningless.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("tickethub")
            .withUsername("tickethub")
            .withPassword("tickethub")
            .withCommand("--innodb-lock-wait-timeout=5", "--transaction-isolation=READ-COMMITTED")
            .withReuse(true);

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?rewriteBatchedStatements=true&serverTimezone=UTC");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
