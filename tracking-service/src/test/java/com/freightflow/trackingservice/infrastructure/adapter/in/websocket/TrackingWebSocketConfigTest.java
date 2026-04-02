package com.freightflow.trackingservice.infrastructure.adapter.in.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that the WebSocket configuration is properly registered
 * and the STOMP messaging infrastructure is available in the application context.
 *
 * @see TrackingWebSocketConfig
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "spring.kafka.bootstrap-servers=localhost:9092",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.listener.auto-startup=false",
                "eureka.client.enabled=false",
                "spring.cloud.config.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
                "freightflow.security.enabled=false"
        }
)
@ActiveProfiles("test")
@DisplayName("Tracking WebSocket Configuration")
class TrackingWebSocketConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("should register TrackingWebSocketConfig as a WebSocketMessageBrokerConfigurer bean")
    void should_RegisterWebSocketConfig_When_ApplicationStarts() {
        // When
        TrackingWebSocketConfig config = applicationContext.getBean(TrackingWebSocketConfig.class);

        // Then
        assertThat(config).isNotNull();
        assertThat(config).isInstanceOf(WebSocketMessageBrokerConfigurer.class);
    }

    @Test
    @DisplayName("should register TrackingWebSocketHandler bean in application context")
    void should_RegisterWebSocketHandler_When_ApplicationStarts() {
        // When
        TrackingWebSocketHandler handler = applicationContext.getBean(TrackingWebSocketHandler.class);

        // Then
        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("should have SimpMessagingTemplate available for STOMP messaging")
    void should_HaveSimpMessagingTemplate_When_WebSocketEnabled() {
        // When
        boolean hasMessagingTemplate = applicationContext.containsBean("brokerMessagingTemplate");

        // Then
        assertThat(hasMessagingTemplate).isTrue();
    }
}
