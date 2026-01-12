package org.example.spsc

import kotlinx.coroutines.flow.Flow
import org.example.events.Event
import org.example.events.storage.EventStream

/**
 * Produces events from an EventStream.
 *
 * The producer is responsible for streaming events from a given position.
 * The returned Flow respects the batch size parameter, emitting at most batchSize events.
 * Batch sizing is performed by the EventStream implementation.
 *
 * The default implementation simply delegates to EventStream.stream(), allowing
 * custom implementations to add filtering, transformation, or other logic.
 */
interface EventProducer {
    suspend fun produce(
        eventStream: EventStream,
        fromPosition: Long,
        batchSize: Int,
    ): Flow<Event<Any>> = eventStream.stream(fromPosition, batchSize)
}
