package com.freightflow.notificationservice.domain.model;

/**
 * Represents the lifecycle states of a notification.
 *
 * <p>State transitions follow a strict flow with retry support:</p>
 * <pre>
 *   PENDING ──→ SENT
 *     │
 *     ▼
 *   RETRYING ──→ SENT
 *     │
 *     ▼
 *   FAILED
 * </pre>
 *
 * <p>Retry logic is encapsulated within this enum to keep it close to
 * the domain model.</p>
 *
 * @see Notification
 */
public enum NotificationStatus {

    /** Notification has been created but not yet sent. */
    PENDING,

    /** Notification was successfully delivered. Terminal state. */
    SENT,

    /** Notification delivery failed and is being retried. */
    RETRYING,

    /** Notification delivery permanently failed after all retries. Terminal state. */
    FAILED;

    /** Maximum number of retry attempts before marking as FAILED. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Checks whether a transition from this state to the target state is allowed.
     *
     * @param target the desired target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(NotificationStatus target) {
        return switch (this) {
            case PENDING -> target == SENT || target == RETRYING || target == FAILED;
            case RETRYING -> target == SENT || target == FAILED || target == RETRYING;
            case SENT, FAILED -> false;
        };
    }

    /**
     * Determines whether a retry should be attempted based on the current attempt count.
     *
     * @param currentAttempts the number of attempts made so far
     * @return true if another retry is allowed
     */
    public static boolean shouldRetry(int currentAttempts) {
        return currentAttempts < MAX_RETRY_ATTEMPTS;
    }

    /**
     * Returns the maximum number of retry attempts.
     *
     * @return the max retry count
     */
    public static int maxRetryAttempts() {
        return MAX_RETRY_ATTEMPTS;
    }
}
