package org.example.retry

import org.example.events.storage.StreamedEvent

/** Represents the outcome of a retry operation. */
sealed class RetryOutcome {
    /**
     * Operation succeeded (possibly after retries).
     * @property attemptCount Total number of attempts (1 if succeeded on first try)
     */
    data class Success(val attemptCount: Int) : RetryOutcome()

    /**
     * Operation failed after exhausting all retry attempts.
     * @property streamedEvent The event that failed
     * @property lastException The final exception that caused failure
     * @property attemptCount Total number of attempts made
     * @property retriable Whether the failure was considered retriable
     */
    data class Failure(
        val streamedEvent: StreamedEvent,
        val lastException: Throwable,
        val attemptCount: Int,
        val retriable: Boolean,
    ) : RetryOutcome()
}
