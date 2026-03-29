package com.freightflow.booking.infrastructure.adapter.in.rest.dto;

import com.freightflow.booking.application.saga.SagaExecution;
import com.freightflow.booking.application.saga.SagaStep;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound REST DTO representing a saga execution in API responses.
 *
 * <p>This record maps from the {@link SagaExecution} domain entity to a flat
 * JSON-serializable structure. It hides internal domain details from API consumers,
 * following the Interface Segregation Principle.</p>
 *
 * @param sagaId         unique saga execution identifier
 * @param bookingId      the booking being confirmed
 * @param status         current saga lifecycle status
 * @param currentStep    the current or last-attempted step (nullable)
 * @param completedSteps list of completed step names
 * @param failedStep     the step that caused failure (nullable)
 * @param failureReason  human-readable failure reason (nullable)
 * @param startedAt      when the saga started
 * @param completedAt    when the saga completed (nullable if still in progress)
 */
public record SagaExecutionResponse(
        UUID sagaId,
        String bookingId,
        String status,
        String currentStep,
        List<String> completedSteps,
        String failedStep,
        String failureReason,
        Instant startedAt,
        Instant completedAt
) {

    /**
     * Factory method that maps a domain {@link SagaExecution} to an API response DTO.
     *
     * <p>Converts enum values to their string names for JSON serialization and
     * maps the list of completed steps to a list of step name strings.</p>
     *
     * @param saga the domain saga execution
     * @return a new {@code SagaExecutionResponse} DTO
     */
    public static SagaExecutionResponse from(SagaExecution saga) {
        return new SagaExecutionResponse(
                saga.getSagaId(),
                saga.getBookingId(),
                saga.getStatus().name(),
                saga.getCurrentStep() != null ? saga.getCurrentStep().name() : null,
                saga.getCompletedSteps().stream()
                        .map(SagaStep::name)
                        .toList(),
                saga.getFailedStep() != null ? saga.getFailedStep().name() : null,
                saga.getFailureReason(),
                saga.getStartedAt(),
                saga.getCompletedAt()
        );
    }
}
