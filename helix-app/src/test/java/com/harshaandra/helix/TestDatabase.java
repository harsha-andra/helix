package com.harshaandra.helix;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration tests run against a real PostgreSQL, never H2.
 *
 * H2 in PostgreSQL-compatibility mode does not reproduce Postgres' planner, its locking, its
 * type coercion, or its behaviour under concurrent updates — which is precisely what the
 * optimistic-locking and N+1 tests are about. A test that passes on H2 and fails on Postgres is
 * worse than no test.
 *
 * By default a container is started (this is what CI does). If HELIX_TEST_JDBC_URL is set, that
 * database is used instead — useful on a machine where the container runtime is unavailable.
 */
public final class TestDatabase {

    private static final String EXTERNAL_URL_ENV = "HELIX_TEST_JDBC_URL";

    private static PostgreSQLContainer<?> container;

    private TestDatabase() {
    }

    public static synchronized void configure(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv(EXTERNAL_URL_ENV);

        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username",
                    () -> envOrDefault("HELIX_TEST_DB_USER", "helix"));
            registry.add("spring.datasource.password",
                    () -> envOrDefault("HELIX_TEST_DB_PASSWORD", "helix"));
            return;
        }

        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("helix")
                    .withUsername("helix")
                    .withPassword("helix");
            container.start();
            // One container for the whole suite; the JVM tears it down on exit. Starting a
            // container per test class turns a 40-second suite into a five-minute one.
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
        }

        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
