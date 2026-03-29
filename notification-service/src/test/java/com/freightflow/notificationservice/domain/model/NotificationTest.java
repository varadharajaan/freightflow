package com.freightflow.notificationservice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    @Test
    void shouldMoveToRetryingOnFirstFailureAndCountAttempts() {
        Notification notification = Notification.create(
                UUID.randomUUID(),
                new NotificationChannel.EmailChannel("ops@acme.test"),
                "Booking Update",
                "Container loaded"
        );

        notification.markRetryOrFail("smtp timeout");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getError()).contains("timeout");
    }
}
