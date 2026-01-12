package org.example.spsc

import org.example.events.storage.StreamedEvent

/**
 * Consumes positioned events from the SPSC queue.
 * Receives a batch of StreamedEvent items, each with its position in the stream.
 * Must complete successfully to advance the bookmark (on failure, bookmark is not advanced).
 */
fun interface EventConsumer {
    /**
     * Process a batch of positioned events.
     * @param streamedEvents the batch of events with their stream positions
     * @throws Exception to indicate failure - bookmark will NOT be advanced
     *
     * The consumer can calculate the next bookmark position as:
     * ```kotlin
     * val nextPosition = streamedEvents.maxOf { it.offset.position } + 1L
     * ```
     */
    suspend fun consume(streamedEvents: List<StreamedEvent>)
}
