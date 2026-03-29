package com.freightflow.booking.infrastructure.scheduler;

import com.freightflow.booking.application.saga.SagaExecution;
import com.freightflow.booking.application.saga.SagaExecutionRepository;
import com.freightflow.booking.application.saga.SagaStatus;
import com.freightflow.booking.application.saga.SagaStep;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Recovery watchdog for stale/in-flight saga executions.
 */
@Component
public class SagaRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryScheduler.class);

    private static final List<SagaStatus> RECOVERABLE_STATES = List.of(
            SagaStatus.STARTED,
            SagaStatus.CONFIRMING_BOOKING,
            SagaStatus.RESERVING_CAPACITY,
            SagaStatus.GENERATING_INVOICE,
            SagaStatus.SENDING_NOTIFICATION,
            SagaStatus.COMPENSATING
    );

    private final SagaExecutionRepository sagaRepository;
    private final long staleThresholdMs;

    public SagaRecoveryScheduler(
            SagaExecutionRepository sagaRepository,
            @Value("${freightflow.saga.recovery.stale-threshold-ms:300000}") long staleThresholdMs) {
        this.sagaRepository = sagaRepository;
        this.staleThresholdMs = staleThresholdMs;
    }

    /**
     * Marks stale sagas as failed so they can be surfaced for manual/automated replay.
     */
    @Scheduled(fixedDelayString = "${freightflow.saga.recovery.interval-ms:60000}")
    @Profiled(value = "sagaRecoverySweep", slowThresholdMs = 2000)
    public void recoverStaleSagas() {
        Instant now = Instant.now();
        Duration staleThreshold = Duration.ofMillis(staleThresholdMs);

        for (SagaStatus status : RECOVERABLE_STATES) {
            List<SagaExecution> sagas = sagaRepository.findByStatus(status);
            for (SagaExecution saga : sagas) {
                if (saga.getStartedAt().isAfter(now.minus(staleThreshold))) {
                    continue;
                }
                try {
                    SagaStep failedStep = saga.getCurrentStep() != null
                            ? saga.getCurrentStep()
                            : SagaStep.CONFIRM_BOOKING;
                    saga.markFailed(
                            failedStep,
                            "Recovery watchdog marked stale in-flight saga as FAILED for replay/intervention");
                    sagaRepository.save(saga);
                    log.warn("Saga marked failed by recovery watchdog: sagaId={}, previousStatus={}",
                            saga.getSagaId(), status);
                } catch (Exception ex) {
                    log.error("Failed to mark stale saga as failed: sagaId={}, status={}, error={}",
                            saga.getSagaId(), status, ex.getMessage(), ex);
                }
            }
        }
    }
}
