package com.freightflow.commons.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers factory methods and property registration helpers.
 *
 * <p>This utility centralizes container defaults used across services so tests
 * stay consistent and DRY (Testing Pyramid baseline, T15).</p>
 */
public final class ContainerizedDependencies {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.6.1");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.2-alpine");
    private static final DockerImageName KEYCLOAK_IMAGE = DockerImageName.parse("quay.io/keycloak/keycloak:25.0");

    private ContainerizedDependencies() {
    }

    public static PostgreSQLContainer<?> postgres(String database, String username, String password) {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(database)
                .withUsername(username)
                .withPassword(password);
    }

    public static KafkaContainer kafka() {
        return new KafkaContainer(KAFKA_IMAGE);
    }

    public static GenericContainer<?> redis() {
        return new GenericContainer<>(REDIS_IMAGE)
                .withExposedPorts(6379);
    }

    public static GenericContainer<?> keycloak() {
        return new GenericContainer<>(KEYCLOAK_IMAGE)
                .withCommand("start-dev")
                .withExposedPorts(8080)
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin");
    }

    public static void registerPostgres(DynamicPropertyRegistry registry, PostgreSQLContainer<?> postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    public static void registerKafka(DynamicPropertyRegistry registry, KafkaContainer kafka) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    public static void registerRedis(DynamicPropertyRegistry registry, GenericContainer<?> redis) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    public static void registerKeycloak(DynamicPropertyRegistry registry, GenericContainer<?> keycloak) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://%s:%d/realms/freightflow".formatted(
                        keycloak.getHost(), keycloak.getMappedPort(8080)));
    }
}
