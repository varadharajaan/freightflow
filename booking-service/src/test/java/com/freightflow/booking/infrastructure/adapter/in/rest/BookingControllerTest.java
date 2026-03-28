package com.freightflow.booking.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.booking.application.BookingService;
import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.BookingStatus;
import com.freightflow.booking.domain.model.Cargo;
import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.domain.VoyageId;
import com.freightflow.commons.domain.Weight;
import com.freightflow.commons.exception.GlobalExceptionHandler;
import com.freightflow.commons.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST API contract tests for the {@link BookingController}.
 *
 * <p>Uses {@code @WebMvcTest} to load ONLY the web layer (no full application context).
 * The {@link BookingService} is mocked to isolate the controller from business logic.
 * Tests verify HTTP status codes, content types, and JSON response structure.</p>
 *
 * <p>Since there is no Spring Security in this service, no {@code @WithMockUser}
 * or security disabling is needed.</p>
 *
 * @see BookingController
 * @see BookingService
 */
@WebMvcTest(BookingController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("BookingController REST API")
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    // ==================== Test Fixtures ====================

    private static final String BASE_URL = "/api/v1/bookings";
    private static final String BOOKING_ID = UUID.randomUUID().toString();
    private static final String CUSTOMER_ID = UUID.randomUUID().toString();
    private static final String VOYAGE_ID = UUID.randomUUID().toString();
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(30);

    /**
     * Creates a reconstituted Booking in DRAFT status for mocking service responses.
     */
    private static Booking draftBooking() {
        return Booking.reconstitute(
                BookingId.fromString(BOOKING_ID),
                CustomerId.fromString(CUSTOMER_ID),
                new Cargo(
                        "HS8471",
                        "Electronic components",
                        Weight.ofKilograms(new BigDecimal("25000")),
                        ContainerType.DRY_40,
                        2,
                        PortCode.of("DEHAM"),
                        PortCode.of("CNSHA")
                ),
                FUTURE_DATE,
                BookingStatus.DRAFT,
                null,
                null,
                Instant.now(),
                Instant.now(),
                0
        );
    }

    /**
     * Creates a reconstituted Booking in CANCELLED status for mocking service responses.
     */
    private static Booking cancelledBooking() {
        return Booking.reconstitute(
                BookingId.fromString(BOOKING_ID),
                CustomerId.fromString(CUSTOMER_ID),
                new Cargo(
                        "HS8471",
                        "Electronic components",
                        Weight.ofKilograms(new BigDecimal("25000")),
                        ContainerType.DRY_40,
                        2,
                        PortCode.of("DEHAM"),
                        PortCode.of("CNSHA")
                ),
                FUTURE_DATE,
                BookingStatus.CANCELLED,
                null,
                "Customer request",
                Instant.now(),
                Instant.now(),
                1
        );
    }

    // ==================== POST /api/v1/bookings ====================

    @Nested
    @DisplayName("POST /api/v1/bookings")
    class CreateBooking {

        @Test
        @DisplayName("should return 202 Accepted with booking response when valid body provided")
        void should_Return202_When_ValidBodyProvided() throws Exception {
            // Given
            when(bookingService.createBooking(any())).thenReturn(draftBooking());

            String requestBody = """
                    {
                        "customerId": "%s",
                        "origin": "DEHAM",
                        "destination": "CNSHA",
                        "commodityCode": "HS8471",
                        "description": "Electronic components",
                        "weightKg": 25000,
                        "containerType": "DRY_40",
                        "containerCount": 2,
                        "requestedDepartureDate": "%s"
                    }
                    """.formatted(CUSTOMER_ID, FUTURE_DATE);

            // When / Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID))
                    .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.origin").value("DEHAM"))
                    .andExpect(jsonPath("$.destination").value("CNSHA"))
                    .andExpect(jsonPath("$.containerType").value("DRY_40"))
                    .andExpect(jsonPath("$.containerCount").value(2))
                    .andExpect(jsonPath("$.requestedDepartureDate").exists())
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists());

            verify(bookingService).createBooking(any());
        }

        @Test
        @DisplayName("should return 422 with ProblemDetail when body is invalid (missing required fields)")
        void should_Return422_When_InvalidBodyProvided() throws Exception {
            // Given — empty JSON body missing all required fields
            String invalidBody = """
                    {
                        "customerId": "",
                        "origin": "",
                        "destination": ""
                    }
                    """;

            // When / Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.type").exists())
                    .andExpect(jsonPath("$.title").value("Validation Failed"))
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.detail").exists())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors").isArray());
        }

        @Test
        @DisplayName("should return 422 when container count is missing")
        void should_Return422_When_ContainerCountMissing() throws Exception {
            String bodyWithoutCount = """
                    {
                        "customerId": "%s",
                        "origin": "DEHAM",
                        "destination": "CNSHA",
                        "commodityCode": "HS8471",
                        "description": "Electronic components",
                        "weightKg": 25000,
                        "containerType": "DRY_40",
                        "requestedDepartureDate": "%s"
                    }
                    """.formatted(CUSTOMER_ID, FUTURE_DATE);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithoutCount))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Validation Failed"));
        }
    }

    // ==================== GET /api/v1/bookings/{bookingId} ====================

    @Nested
    @DisplayName("GET /api/v1/bookings/{bookingId}")
    class GetBooking {

        @Test
        @DisplayName("should return 200 OK with booking JSON when booking exists")
        void should_Return200_When_BookingExists() throws Exception {
            // Given
            when(bookingService.getBooking(BOOKING_ID)).thenReturn(draftBooking());

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{bookingId}", BOOKING_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID))
                    .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.origin").value("DEHAM"))
                    .andExpect(jsonPath("$.destination").value("CNSHA"))
                    .andExpect(jsonPath("$.containerType").value("DRY_40"))
                    .andExpect(jsonPath("$.containerCount").value(2))
                    .andExpect(jsonPath("$.voyageId").isEmpty())
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists());

            verify(bookingService).getBooking(BOOKING_ID);
        }

        @Test
        @DisplayName("should return 404 with ProblemDetail when booking does not exist")
        void should_Return404_When_BookingNotFound() throws Exception {
            // Given
            String nonExistentId = UUID.randomUUID().toString();
            when(bookingService.getBooking(nonExistentId))
                    .thenThrow(ResourceNotFoundException.forBooking(nonExistentId));

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{bookingId}", nonExistentId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").exists())
                    .andExpect(jsonPath("$.title").value("Resource Not Found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").exists())
                    .andExpect(jsonPath("$.errorCode").value("BOOKING_NOT_FOUND"))
                    .andExpect(jsonPath("$.resourceType").value("Booking"))
                    .andExpect(jsonPath("$.resourceId").value(nonExistentId));

            verify(bookingService).getBooking(nonExistentId);
        }
    }

    // ==================== DELETE /api/v1/bookings/{bookingId} ====================

    @Nested
    @DisplayName("DELETE /api/v1/bookings/{bookingId}")
    class CancelBooking {

        @Test
        @DisplayName("should return 200 OK with cancelled booking when valid cancellation")
        void should_Return200_When_ValidCancellation() throws Exception {
            // Given
            when(bookingService.cancelBooking(eq(BOOKING_ID), eq("Customer request")))
                    .thenReturn(cancelledBooking());

            String requestBody = """
                    {
                        "reason": "Customer request"
                    }
                    """;

            // When / Then
            mockMvc.perform(delete(BASE_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            verify(bookingService).cancelBooking(BOOKING_ID, "Customer request");
        }

        @Test
        @DisplayName("should return 422 when reason is missing")
        void should_Return422_When_ReasonMissing() throws Exception {
            String bodyWithoutReason = """
                    {
                        "reason": ""
                    }
                    """;

            mockMvc.perform(delete(BASE_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithoutReason))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Validation Failed"));
        }

        @Test
        @DisplayName("should return 404 when cancelling non-existent booking")
        void should_Return404_When_CancellingNonExistentBooking() throws Exception {
            // Given
            String nonExistentId = UUID.randomUUID().toString();
            when(bookingService.cancelBooking(eq(nonExistentId), any()))
                    .thenThrow(ResourceNotFoundException.forBooking(nonExistentId));

            String requestBody = """
                    {
                        "reason": "No longer needed"
                    }
                    """;

            // When / Then
            mockMvc.perform(delete(BASE_URL + "/{bookingId}", nonExistentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("BOOKING_NOT_FOUND"));
        }
    }

    // ==================== POST /api/v1/bookings/{bookingId}/confirm ====================

    @Nested
    @DisplayName("POST /api/v1/bookings/{bookingId}/confirm")
    class ConfirmBooking {

        @Test
        @DisplayName("should return 200 OK with confirmed booking when valid")
        void should_Return200_When_ValidConfirmation() throws Exception {
            // Given
            Booking confirmedBooking = Booking.reconstitute(
                    BookingId.fromString(BOOKING_ID),
                    CustomerId.fromString(CUSTOMER_ID),
                    new Cargo(
                            "HS8471",
                            "Electronic components",
                            Weight.ofKilograms(new BigDecimal("25000")),
                            ContainerType.DRY_40,
                            2,
                            PortCode.of("DEHAM"),
                            PortCode.of("CNSHA")
                    ),
                    FUTURE_DATE,
                    BookingStatus.CONFIRMED,
                    VoyageId.fromString(VOYAGE_ID),
                    null,
                    Instant.now(),
                    Instant.now(),
                    1
            );
            when(bookingService.confirmBooking(BOOKING_ID, VOYAGE_ID))
                    .thenReturn(confirmedBooking);

            String requestBody = """
                    {
                        "voyageId": "%s"
                    }
                    """.formatted(VOYAGE_ID);

            // When / Then
            mockMvc.perform(post(BASE_URL + "/{bookingId}/confirm", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.voyageId").value(VOYAGE_ID));

            verify(bookingService).confirmBooking(BOOKING_ID, VOYAGE_ID);
        }

        @Test
        @DisplayName("should return 422 when voyageId is blank")
        void should_Return422_When_VoyageIdBlank() throws Exception {
            String bodyWithBlankVoyage = """
                    {
                        "voyageId": ""
                    }
                    """;

            mockMvc.perform(post(BASE_URL + "/{bookingId}/confirm", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithBlankVoyage))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Validation Failed"));
        }
    }
}
