package org.example.spsc

import org.example.events.storage.StreamedEvent

/**
 * Default EventConsumer implementation that wraps a user-provided consume function.
 *
 * The SpscCoordinator handles bookmark advancement automatically:
 * - If consume completes successfully, the coordinator advances the bookmark to the position after the last event
 * - If consume throws an exception, the bookmark is NOT advanced
 *
 * This allows you to focus on your business logic without worrying about bookmark management.
 *
 * Example usage:
 * ```kotlin
 * val consumer = DefaultEventConsumer { events ->
 *     events.forEach { event ->
 *         println("Processing: ${event.data}")
 *     }
 * }
 *
 * val coordinator = SpscCoordinator(
 *     DefaultEventProducer,
 *     consumer,
 *     eventStream,
 *     config
 * )
 * ```
 */
class DefaultEventConsumer(
    private val consume: suspend (List<StreamedEvent>) -> Unit,
) : EventConsumer {
    override suspend fun consume(streamedEvents: List<StreamedEvent>) {
        // Call the user's consume function
        consume(streamedEvents)
    }

    companion object {
        /**
         * Create a DefaultEventConsumer from a lambda.
         * This is just a convenience function - you can also construct directly.
         */
        operator fun invoke(consume: suspend (List<StreamedEvent>) -> Unit): DefaultEventConsumer = DefaultEventConsumer(consume)
    }
}
