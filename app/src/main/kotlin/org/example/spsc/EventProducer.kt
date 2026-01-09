package org.example.spsc

import kotlinx.coroutines.flow.Flow
import org.example.events.Event
import org.example.events.storage.StreamingEventStore

/**
 * Produces events from a StreamingEventStore.
 * Fetches events in configurable batch sizes from a given position.
 */
interface EventProducer {
    suspend fun produce(
        eventStore: StreamingEventStore,
        fromPosition: Long,
        batchSize: Int,
    ): Flow<Event<Any>>
}

/**
 * Default implementation of EventProducer.
 * Streams events from a starting position in batches.
 */
class DefaultEventProducer : EventProducer {
    override suspend fun produce(
        eventStore: StreamingEventStore,
        fromPosition: Long,
        batchSize: Int,
    ): Flow<Event<Any>> = eventStore.stream(fromPosition)
}
