package org.example.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for retry behavior when processing events.
 *
 * @property maxAttempts Maximum number of attempts (including the initial attempt). Must be >= 1.
 * @property initialDelay Initial delay before the first retry.
 * @property maxDelay Maximum delay between retries.
 * @property backoffMultiplier Multiplier for exponential backoff (e.g., 2.0 for doubling).
 * @property retryableExceptions Set of exception types that should trigger a retry.
 *                                If empty, all exceptions except non-retriable ones are retried.
 */
data class RetryStrategy(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 100.milliseconds,
    val maxDelay: Duration = 10.seconds,
    val backoffMultiplier: Double = 2.0,
    val retryableExceptions: Set<Class<out Throwable>> = emptySet(),
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0" }
        require(initialDelay > Duration.ZERO) { "initialDelay must be positive" }
        require(maxDelay >= initialDelay) { "maxDelay must be >= initialDelay" }
    }

    companion object {
        /** Default retry strategy with 3 attempts and exponential backoff. */
        val DEFAULT = RetryStrategy()

        /** No retry - fail immediately. */
        val NO_RETRY = RetryStrategy(maxAttempts = 1)

        /** Aggressive retry with more attempts and faster initial retries. */
        val AGGRESSIVE = RetryStrategy(
            maxAttempts = 5,
            initialDelay = 50.milliseconds,
            maxDelay = 5.seconds,
            backoffMultiplier = 1.5,
        )
    }

    /**
     * Calculate the delay for a given retry attempt.
     * @param attempt The retry attempt number (0-based, so first retry is attempt 0)
     */
    fun calculateDelay(attempt: Int): Duration {
        var multiplier = 1.0
        repeat(attempt) {
            multiplier *= backoffMultiplier
        }
        val delay = initialDelay * multiplier
        return minOf(delay, maxDelay)
    }

    private fun Duration.times(multiplier: Double): Duration = (this.inWholeMilliseconds * multiplier).toLong().milliseconds
}
