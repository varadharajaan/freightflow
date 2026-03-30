package com.freightflow.booking.contract;

import com.freightflow.commons.testing.ContainerizedDependencies;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for generated Spring Cloud Contract tests in booking-service.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "freightflow.security.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
public abstract class ContractBaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            ContainerizedDependencies.postgres("freightflow_booking_contract", "test", "test");

    @Container
    static final KafkaContainer KAFKA = ContainerizedDependencies.kafka();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ContainerizedDependencies.registerPostgres(registry, POSTGRES);
        ContainerizedDependencies.registerKafka(registry, KAFKA);
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setupRestAssured() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }
}
