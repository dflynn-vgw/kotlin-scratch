package org.example.retry

import org.example.events.storage.StreamedEvent

/** Executor that performs actions with resilience and retry logic. */
fun interface ResilientExecutor {
    /** Executes the given action with retry logic.
     *
     * @param action The action to execute.
     * @return The outcome of the execution, indicating success or failure.
     */
    fun execute(event: StreamedEvent, action: () -> Unit): Outcome

    /** Represents the outcome of a retry operation. */
    sealed class Outcome {
        /** Operation succeeded (possibly after retries). */
        data class Success(val attemptCount: Int) : Outcome()

        /** Operation failed after exhausting all retry attempts. */
        data class Failure(val attemptCount: Int, val lastException: Throwable) : Outcome()
    }
}
