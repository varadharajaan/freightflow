package com.freightflow.notificationservice.infrastructure.adapter.in.rest;

import com.freightflow.commons.domain.FreightFlowConstants;
import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.notificationservice.application.command.NotificationCommandHandler;
import com.freightflow.notificationservice.application.query.NotificationQueryHandler;
import com.freightflow.notificationservice.domain.model.Notification;
import com.freightflow.notificationservice.domain.model.NotificationChannel;
import com.freightflow.notificationservice.domain.model.NotificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for managing notifications.
 *
 * <p>This is the primary inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer commands and queries. Most notifications
 * are created via Kafka event consumers, but this controller provides manual
 * send capability and query access.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST  /api/v1/notifications/send}                — send a notification</li>
 *   <li>{@code GET   /api/v1/notifications/{notificationId}}     — retrieve a notification</li>
 *   <li>{@code GET   /api/v1/notifications?recipientId={id}}     — list by recipient</li>
 *   <li>{@code GET   /api/v1/notifications?status={status}}      — list by status</li>
 *   <li>{@code POST  /api/v1/notifications/{notificationId}/retry} — retry a failed notification</li>
 * </ul>
 *
 * @see NotificationCommandHandler
 * @see NotificationQueryHandler
 */
@RestController
@RequestMapping(FreightFlowConstants.API_V1 + "/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationCommandHandler commandHandler;
    private final NotificationQueryHandler queryHandler;

    /**
     * Creates a new {@code NotificationController} with the required handlers.
     *
     * @param commandHandler the notification command handler (must not be null)
     * @param queryHandler   the notification query handler (must not be null)
     */
    public NotificationController(NotificationCommandHandler commandHandler,
                                    NotificationQueryHandler queryHandler) {
        this.commandHandler = Objects.requireNonNull(commandHandler,
                "NotificationCommandHandler must not be null");
        this.queryHandler = Objects.requireNonNull(queryHandler,
                "NotificationQueryHandler must not be null");
    }

    /**
     * Sends a notification through the specified channel.
     *
     * @param request the send notification request (validated via Bean Validation)
     * @return 202 Accepted with the notification details
     */
    @PostMapping("/send")
    @Profiled(value = "sendNotificationEndpoint", slowThresholdMs = 3000)
    public ResponseEntity<NotificationResponse> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {
        log.debug("POST /api/v1/notifications/send — channel={}, subject='{}'",
                request.channelType(), request.subject());

        NotificationChannel channel = buildChannel(request);

        Notification notification = commandHandler.sendNotification(
                UUID.fromString(request.recipientId()),
                channel,
                request.subject(),
                request.body()
        );

        NotificationResponse response = NotificationResponse.from(notification);

        log.info("Notification sent: notificationId={}, channel={}, status={}",
                response.notificationId(), response.channelType(), response.status());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Retrieves a notification by its unique identifier.
     *
     * @param notificationId the notification UUID (path variable)
     * @return 200 OK with the notification details
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> getNotification(@PathVariable String notificationId) {
        log.debug("GET /api/v1/notifications/{} — fetching notification", notificationId);

        Notification notification = queryHandler.getNotification(notificationId);
        NotificationResponse response = NotificationResponse.from(notification);

        return ResponseEntity.ok(response);
    }

    /**
     * Lists notifications filtered by recipient or status.
     *
     * @param recipientId the recipient UUID (optional query parameter)
     * @param status      the notification status (optional query parameter)
     * @return 200 OK with a list of notifications
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @RequestParam(required = false) String recipientId,
            @RequestParam(required = false) NotificationStatus status) {
        log.debug("GET /api/v1/notifications — recipientId={}, status={}", recipientId, status);

        List<NotificationResponse> responses;
        if (recipientId != null) {
            responses = queryHandler.getNotificationsByRecipient(recipientId)
                    .stream()
                    .map(NotificationResponse::from)
                    .toList();
        } else if (status != null) {
            responses = queryHandler.getNotificationsByStatus(status)
                    .stream()
                    .map(NotificationResponse::from)
                    .toList();
        } else {
            responses = queryHandler.getNotificationsByStatus(NotificationStatus.PENDING)
                    .stream()
                    .map(NotificationResponse::from)
                    .toList();
        }

        log.info("Retrieved {} notification(s)", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Retries a failed or retrying notification.
     *
     * @param notificationId the notification UUID to retry
     * @return 200 OK with the updated notification
     */
    @PostMapping("/{notificationId}/retry")
    public ResponseEntity<NotificationResponse> retryNotification(
            @PathVariable String notificationId) {
        log.debug("POST /api/v1/notifications/{}/retry — retrying", notificationId);

        Notification notification = commandHandler.retryNotification(UUID.fromString(notificationId));
        NotificationResponse response = NotificationResponse.from(notification);

        log.info("Notification retried: notificationId={}, status={}", notificationId, response.status());

        return ResponseEntity.ok(response);
    }

    /**
     * Builds a {@link NotificationChannel} from the REST request using pattern matching.
     *
     * @param request the send notification request
     * @return the constructed channel
     */
    private NotificationChannel buildChannel(SendNotificationRequest request) {
        return switch (request.channelType()) {
            case "EMAIL" -> new NotificationChannel.EmailChannel(
                    request.to(),
                    request.cc() != null ? request.cc() : List.of(),
                    request.bcc() != null ? request.bcc() : List.of());
            case "SMS" -> new NotificationChannel.SmsChannel(request.to());
            case "WEBHOOK" -> new NotificationChannel.WebhookChannel(
                    request.to(),
                    request.headers() != null ? request.headers() : Map.of());
            default -> throw new IllegalArgumentException(
                    "Unknown channel type: " + request.channelType());
        };
    }

    // ==================== Inner DTOs ====================

    /**
     * Inbound REST DTO for sending a notification.
     *
     * @param recipientId the recipient UUID
     * @param channelType the channel type (EMAIL, SMS, WEBHOOK)
     * @param to          the delivery address (email, phone, or URL)
     * @param cc          CC recipients (email only)
     * @param bcc         BCC recipients (email only)
     * @param headers     custom headers (webhook only)
     * @param subject     the notification subject
     * @param body        the notification body
     */
    public record SendNotificationRequest(
            @NotBlank(message = "Recipient ID is required") String recipientId,
            @NotBlank(message = "Channel type is required") String channelType,
            @NotBlank(message = "To address is required") String to,
            List<String> cc,
            List<String> bcc,
            Map<String, String> headers,
            @NotBlank(message = "Subject is required") String subject,
            @NotBlank(message = "Body is required") String body
    ) {}

    /**
     * Outbound REST DTO representing a notification in API responses.
     *
     * @param notificationId the notification UUID
     * @param recipientId    the recipient UUID
     * @param channelType    the delivery channel type
     * @param subject        the notification subject
     * @param status         the current status
     * @param attempts       the number of delivery attempts
     * @param sentAt         when the notification was sent (null if not yet sent)
     * @param error          the last error message (null if no errors)
     * @param createdAt      when the notification was created
     * @param updatedAt      when the notification was last modified
     */
    public record NotificationResponse(
            String notificationId,
            String recipientId,
            String channelType,
            String subject,
            NotificationStatus status,
            int attempts,
            String sentAt,
            String error,
            String createdAt,
            String updatedAt
    ) {
        /**
         * Factory method that maps a domain {@link Notification} to a response DTO.
         *
         * @param n the domain notification aggregate
         * @return a new response DTO
         */
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(
                    n.getNotificationId().toString(),
                    n.getRecipientId().toString(),
                    n.getChannel().channelType(),
                    n.getSubject(),
                    n.getStatus(),
                    n.getAttempts(),
                    n.getSentAt() != null ? n.getSentAt().toString() : null,
                    n.getError(),
                    n.getCreatedAt().toString(),
                    n.getUpdatedAt().toString()
            );
        }
    }
}
