package com.freightflow.trackingservice.infrastructure.adapter.in.rest;

import com.freightflow.trackingservice.application.command.TrackingCommandHandler;
import com.freightflow.trackingservice.application.query.TrackingQueryHandler;
import com.freightflow.trackingservice.domain.model.Container;
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

@WebMvcTest(controllers = TrackingController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrackingQueryHandler trackingQueryHandler;

    @MockBean
    private TrackingCommandHandler trackingCommandHandler;

    @Test
    void shouldReturnContainerById() throws Exception {
        Container container = Container.create("MSCU1234567", UUID.randomUUID());
        when(trackingQueryHandler.getContainerPosition("MSCU1234567")).thenReturn(container);

        mockMvc.perform(get("/api/v1/tracking/containers/{id}", "MSCU1234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerId").value("MSCU1234567"))
                .andExpect(jsonPath("$.status").value("EMPTY"));
    }
}
