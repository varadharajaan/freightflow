package com.freightflow.booking.infrastructure.adapter.in.rest.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.booking.application.BookingService;
import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.BookingStatus;
import com.freightflow.booking.domain.model.Cargo;
import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.booking.infrastructure.adapter.in.rest.BookingController;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.ConfirmBookingRequest;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.domain.VoyageId;
import com.freightflow.commons.domain.Weight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for the {@link BookingController}.
 *
 * <p>These tests verify that the RBAC rules enforced via {@code @PreAuthorize} annotations
 * work correctly with Spring Security's method-level security. The tests use
 * {@code @WithMockUser} to simulate different roles without requiring a running Keycloak
 * instance or real JWT tokens.</p>
 *
 * <h3>Test Strategy</h3>
 * <ul>
 *   <li><b>Authentication tests</b> — verify 401 Unauthorized for unauthenticated requests</li>
 *   <li><b>Authorization tests</b> — verify 403 Forbidden for insufficient roles</li>
 *   <li><b>Happy-path tests</b> — verify 200/202 for correctly authenticated and authorized requests</li>
 *   <li><b>Row-level security</b> — verify customers can only access their own bookings</li>
 * </ul>
 *
 * <h3>RBAC Matrix Under Test</h3>
 * <pre>
 * Endpoint                          | ADMIN | OPERATOR | CUSTOMER | FINANCE
 * ----------------------------------|-------|----------|----------|--------
 * POST   /api/v1/bookings           |  OK   |    OK    |    OK    |  403
 * GET    /api/v1/bookings/{id}      |  OK   |    OK    |    OK    |  403
 * POST   /api/v1/bookings/{id}/confirm | OK |    OK    |   403    |  403
 * DELETE /api/v1/bookings/{id}      |  OK   |    OK    |    OK    |  403
 * GET    /api/v1/bookings?customerId | OK   |    OK    | own-only |  403
 * </pre>
 *
 * @see BookingController
 */
@WebMvcTest(BookingController.class)
@Import(com.freightflow.booking.infrastructure.config.security.BookingSecurityConfig.class)
@DisplayName("BookingController Security Tests")
class BookingSecurityTest {

    private static final String BOOKINGS_URL = "/api/v1/bookings";
    private static final String BOOKING_ID = UUID.randomUUID().toString();
    private static final String CUSTOMER_ID = UUID.randomUUID().toString();
    private static final String OTHER_CUSTOMER_ID = UUID.randomUUID().toString();
    private static final String VOYAGE_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    // ==================== Authentication Tests ====================

    @Nested
    @DisplayName("Authentication — Unauthenticated Requests")
    class AuthenticationTests {

        @Test
        @DisplayName("should return 401 when no token is provided for GET /api/v1/bookings/{id}")
        void should_Return401_When_NoTokenProvided() throws Exception {
            mockMvc.perform(get(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 401 when no token is provided for POST /api/v1/bookings")
        void should_Return401_When_NoTokenOnCreate() throws Exception {
            mockMvc.perform(post(BOOKINGS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"test\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 401 when no token is provided for POST /api/v1/bookings/{id}/confirm")
        void should_Return401_When_NoTokenOnConfirm() throws Exception {
            mockMvc.perform(post(BOOKINGS_URL + "/{bookingId}/confirm", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"voyageId\":\"" + VOYAGE_ID + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 401 when no token is provided for DELETE /api/v1/bookings/{id}")
        void should_Return401_When_NoTokenOnCancel() throws Exception {
            mockMvc.perform(delete(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"test cancellation\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== Admin Role Tests ====================

    @Nested
    @DisplayName("Authorization — ADMIN Role")
    class AdminRoleTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("should return 200 when admin retrieves a booking")
        void should_Return200_When_ValidAdminToken() throws Exception {
            given(bookingService.getBooking(BOOKING_ID)).willReturn(stubBooking(BOOKING_ID, CUSTOMER_ID));

            mockMvc.perform(get(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("should return 200 when admin confirms a booking")
        void should_Return200_When_AdminConfirms() throws Exception {
            given(bookingService.confirmBooking(eq(BOOKING_ID), eq(VOYAGE_ID)))
                    .willReturn(stubConfirmedBooking(BOOKING_ID, CUSTOMER_ID));

            ConfirmBookingRequest request = new ConfirmBookingRequest(VOYAGE_ID);

            mockMvc.perform(post(BOOKINGS_URL + "/{bookingId}/confirm", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("should return 200 when admin lists any customer's bookings")
        void should_Return200_When_AdminListsAnyCustomerBookings() throws Exception {
            given(bookingService.getBookingsByCustomer(CUSTOMER_ID))
                    .willReturn(List.of(stubBooking(BOOKING_ID, CUSTOMER_ID)));

            mockMvc.perform(get(BOOKINGS_URL)
                            .param("customerId", CUSTOMER_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    // ==================== Operator Role Tests ====================

    @Nested
    @DisplayName("Authorization — OPERATOR Role")
    class OperatorRoleTests {

        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("should return 200 when operator confirms a booking")
        void should_Return200_When_OperatorConfirms() throws Exception {
            given(bookingService.confirmBooking(eq(BOOKING_ID), eq(VOYAGE_ID)))
                    .willReturn(stubConfirmedBooking(BOOKING_ID, CUSTOMER_ID));

            ConfirmBookingRequest request = new ConfirmBookingRequest(VOYAGE_ID);

            mockMvc.perform(post(BOOKINGS_URL + "/{bookingId}/confirm", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID));
        }

        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("should return 200 when operator retrieves a booking")
        void should_Return200_When_OperatorRetrievesBooking() throws Exception {
            given(bookingService.getBooking(BOOKING_ID)).willReturn(stubBooking(BOOKING_ID, CUSTOMER_ID));

            mockMvc.perform(get(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID));
        }
    }

    // ==================== Customer Role Tests ====================

    @Nested
    @DisplayName("Authorization — CUSTOMER Role")
    class CustomerRoleTests {

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("should return 403 when customer tries to confirm a booking")
        void should_Return403_When_CustomerTriesToConfirm() throws Exception {
            ConfirmBookingRequest request = new ConfirmBookingRequest(VOYAGE_ID);

            mockMvc.perform(post(BOOKINGS_URL + "/{bookingId}/confirm", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = CUSTOMER_ID, roles = "CUSTOMER")
        @DisplayName("should return 200 when customer retrieves a booking")
        void should_Return200_When_CustomerRetrievesBooking() throws Exception {
            given(bookingService.getBooking(BOOKING_ID)).willReturn(stubBooking(BOOKING_ID, CUSTOMER_ID));

            mockMvc.perform(get(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(BOOKING_ID));
        }

        @Test
        @WithMockUser(username = CUSTOMER_ID, roles = "CUSTOMER")
        @DisplayName("should return 200 when customer views own bookings")
        void should_Return200_When_CustomerViewsOwnBookings() throws Exception {
            given(bookingService.getBookingsByCustomer(CUSTOMER_ID))
                    .willReturn(List.of(stubBooking(BOOKING_ID, CUSTOMER_ID)));

            mockMvc.perform(get(BOOKINGS_URL)
                            .param("customerId", CUSTOMER_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @WithMockUser(username = CUSTOMER_ID, roles = "CUSTOMER")
        @DisplayName("should return 403 when customer views another customer's bookings")
        void should_Return403_When_CustomerViewsOtherCustomerBookings() throws Exception {
            mockMvc.perform(get(BOOKINGS_URL)
                            .param("customerId", OTHER_CUSTOMER_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = CUSTOMER_ID, roles = "CUSTOMER")
        @DisplayName("should return 200 when customer cancels a booking")
        void should_Return200_When_CustomerCancelsBooking() throws Exception {
            given(bookingService.getBooking(BOOKING_ID)).willReturn(stubBooking(BOOKING_ID, CUSTOMER_ID));
            given(bookingService.cancelBooking(eq(BOOKING_ID), eq("Changed plans")))
                    .willReturn(stubBooking(BOOKING_ID, CUSTOMER_ID));

            mockMvc.perform(delete(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Changed plans\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = OTHER_CUSTOMER_ID, roles = "CUSTOMER")
        @DisplayName("should return 403 when customer retrieves another customer's booking")
        void should_Return403_When_CustomerRetrievesOtherCustomerBooking() throws Exception {
            given(bookingService.getBooking(BOOKING_ID)).willReturn(stubBooking(BOOKING_ID, CUSTOMER_ID));

            mockMvc.perform(get(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = OTHER_CUSTOMER_ID, roles = "CUSTOMER")
        @DisplayName("should return 403 when customer cancels another customer's booking")
        void should_Return403_When_CustomerCancelsOtherCustomerBooking() throws Exception {
            given(bookingService.getBooking(BOOKING_ID)).willReturn(stubBooking(BOOKING_ID, CUSTOMER_ID));

            mockMvc.perform(delete(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Changed plans\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = CUSTOMER_ID, roles = "CUSTOMER")
        @DisplayName("should return 403 when customer creates booking for a different customerId")
        void should_Return403_When_CustomerCreatesBookingForDifferentCustomer() throws Exception {
            CreateBookingRequest request = new CreateBookingRequest(
                    OTHER_CUSTOMER_ID,
                    "CNSHA",
                    "USLAX",
                    "8471",
                    "Electronics",
                    new BigDecimal("5000"),
                    ContainerType.DRY_20,
                    2,
                    LocalDate.now().plusDays(10)
            );

            mockMvc.perform(post(BOOKINGS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== Finance Role Tests ====================

    @Nested
    @DisplayName("Authorization — FINANCE Role")
    class FinanceRoleTests {

        @Test
        @WithMockUser(roles = "FINANCE")
        @DisplayName("should return 403 when finance role tries to create a booking")
        void should_Return403_When_FinanceTriesToCreate() throws Exception {
            mockMvc.perform(post(BOOKINGS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"test\",\"origin\":\"CNSHA\",\"destination\":\"USLAX\"," +
                                    "\"commodityCode\":\"8471\",\"description\":\"Electronics\"," +
                                    "\"weightKg\":5000,\"containerType\":\"DRY_20\"," +
                                    "\"containerCount\":2,\"requestedDepartureDate\":\"2026-05-01\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "FINANCE")
        @DisplayName("should return 403 when finance role tries to confirm a booking")
        void should_Return403_When_FinanceTriesToConfirm() throws Exception {
            ConfirmBookingRequest request = new ConfirmBookingRequest(VOYAGE_ID);

            mockMvc.perform(post(BOOKINGS_URL + "/{bookingId}/confirm", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "FINANCE")
        @DisplayName("should return 403 when finance role tries to retrieve a booking")
        void should_Return403_When_FinanceTriesToRetrieve() throws Exception {
            mockMvc.perform(get(BOOKINGS_URL + "/{bookingId}", BOOKING_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== Test Helpers ====================

    /**
     * Creates a stub {@link Booking} for use in mock service responses.
     *
     * <p>Uses aggregate reconstitution to create deterministic IDs and customer ownership
     * in security-focused controller tests.</p>
     */
    private Booking stubBooking(String bookingId, String customerId) {
        Cargo cargo = new Cargo(
                "8471",
                "Electronics",
                Weight.ofKilograms(new BigDecimal("5000")),
                ContainerType.DRY_20,
                2,
                PortCode.of("CNSHA"),
                PortCode.of("USLAX")
        );

        return Booking.reconstitute(
                BookingId.fromString(bookingId),
                CustomerId.fromString(customerId),
                cargo,
                LocalDate.now().plusDays(30),
                BookingStatus.DRAFT,
                null,
                null,
                Instant.now().minusSeconds(60),
                Instant.now(),
                0L
        );
    }

    /**
     * Creates a stub confirmed {@link Booking} for mock confirm responses.
     */
    private Booking stubConfirmedBooking(String bookingId, String customerId) {
        Cargo cargo = new Cargo(
                "8471",
                "Electronics",
                Weight.ofKilograms(new BigDecimal("5000")),
                ContainerType.DRY_20,
                2,
                PortCode.of("CNSHA"),
                PortCode.of("USLAX")
        );
        return Booking.reconstitute(
                BookingId.fromString(bookingId),
                CustomerId.fromString(customerId),
                cargo,
                LocalDate.now().plusDays(30),
                BookingStatus.CONFIRMED,
                VoyageId.fromString(VOYAGE_ID),
                null,
                Instant.now().minusSeconds(120),
                Instant.now(),
                1L
        );
    }
}
