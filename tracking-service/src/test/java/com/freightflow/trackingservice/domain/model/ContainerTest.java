package com.freightflow.trackingservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerTest {

    @Test
    void shouldUpdatePositionAndKeepContainerMutableBeforeDelivery() {
        Container container = Container.create("MSCU1234567", UUID.randomUUID());
        Position position = new Position(1.3000, 103.8000, Instant.now(), Position.PositionSource.GPS);

        container.updatePosition(position);

        assertThat(container.getCurrentPosition()).isPresent();
        assertThat(container.getCurrentPosition().orElseThrow().latitude()).isEqualTo(1.3000);
        assertThat(container.getStatus()).isEqualTo(ContainerStatus.EMPTY);
    }
}
