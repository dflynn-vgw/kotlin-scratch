package org.example.spsc

import kotlinx.coroutines.flow.Flow
import org.example.events.Event
import org.example.events.storage.EventStream

/**
 * Produces events from a StreamingEventStore.
 * Fetches events in configurable batch sizes from a given position.
 */
interface EventProducer {
    suspend fun produce(
        eventStream: EventStream,
        fromPosition: Long,
        batchSize: Int,
    ): Flow<Event<Any>> = eventStream.stream(fromPosition, batchSize)
}
