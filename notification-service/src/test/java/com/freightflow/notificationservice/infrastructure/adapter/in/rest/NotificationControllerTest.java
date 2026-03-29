package com.freightflow.notificationservice.infrastructure.adapter.in.rest;

import com.freightflow.notificationservice.application.command.NotificationCommandHandler;
import com.freightflow.notificationservice.application.query.NotificationQueryHandler;
import com.freightflow.notificationservice.domain.model.Notification;
import com.freightflow.notificationservice.domain.model.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationCommandHandler notificationCommandHandler;

    @MockBean
    private NotificationQueryHandler notificationQueryHandler;

    @Test
    void shouldReturnNotificationById() throws Exception {
        Notification notification = Notification.create(
                UUID.randomUUID(),
                new NotificationChannel.EmailChannel("ops@acme.test"),
                "Booking Update",
                "Container departed"
        );

        when(notificationQueryHandler.getNotification(notification.getNotificationId().toString()))
                .thenReturn(notification);

        mockMvc.perform(get("/api/v1/notifications/{id}", notification.getNotificationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notification.getNotificationId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
