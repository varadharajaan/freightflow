package com.freightflow.vesselschedule.infrastructure.adapter.in.rest;

import com.freightflow.vesselschedule.application.command.VesselCommandHandler;
import com.freightflow.vesselschedule.application.query.VesselQueryHandler;
import com.freightflow.commons.exception.FreightFlowExceptionAutoConfiguration;
import com.freightflow.commons.exception.ConflictException;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VesselController.class)
@ImportAutoConfiguration(FreightFlowExceptionAutoConfiguration.class)
class VesselControllerExceptionSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VesselQueryHandler vesselQueryHandler;

    @MockBean
    private VesselCommandHandler vesselCommandHandler;

    @Test
    void shouldReturnProblemDetailForResourceNotFoundFromCentralizedHandler() throws Exception {
        UUID voyageId = UUID.randomUUID();
        when(vesselQueryHandler.getVoyage(voyageId))
                .thenThrow(new ResourceNotFoundException("Voyage", voyageId.toString()));

        mockMvc.perform(get("/api/v1/voyages/{voyageId}", voyageId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnProblemDetailForConflictFromCentralizedHandler() throws Exception {
        UUID voyageId = UUID.randomUUID();
        when(vesselQueryHandler.getVoyage(voyageId))
                .thenThrow(ConflictException.invalidStateTransition("Voyage", voyageId.toString(), "SCHEDULED", "CANCELLED"));

        mockMvc.perform(get("/api/v1/voyages/{voyageId}", voyageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void shouldReturnProblemDetailForValidationFromCentralizedHandler() throws Exception {
        UUID voyageId = UUID.randomUUID();
        when(vesselQueryHandler.getVoyage(voyageId))
                .thenThrow(new ValidationException(
                        "Invalid request",
                        java.util.List.of(new ValidationException.FieldError("voyageId", "must not be blank", null))
                ));

        mockMvc.perform(get("/api/v1/voyages/{voyageId}", voyageId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnProblemDetailForUnexpectedErrorsFromCentralizedHandler() throws Exception {
        UUID voyageId = UUID.randomUUID();
        when(vesselQueryHandler.getVoyage(voyageId))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/v1/voyages/{voyageId}", voyageId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }
}
