package org.example.spsc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.example.events.storage.EventStream
import org.example.events.storage.StreamOffset
import org.example.events.storage.StreamedEvent

/**
 * Produces positioned events from an EventStream.
 *
 * The producer is responsible for streaming events from a given position and
 * wrapping each event with its position in the stream (StreamedEvent).
 * The returned Flow respects the batch size parameter, emitting at most batchSize events.
 * Batch sizing is performed by the EventStream implementation.
 *
 * Each emitted StreamedEvent includes the event and its StreamOffset, enabling consumers
 * to be position-aware and implement exactly-once semantics.
 */
fun interface EventProducer {
    suspend fun produce(
        eventStream: EventStream,
        fromPosition: Long,
        batchSize: Int,
    ): Flow<StreamedEvent>
}

/**
 * Default EventProducer implementation.
 * Streams events from the EventStream and wraps each with its StreamOffset.
 */
val DefaultEventProducer: EventProducer = EventProducer { eventStream, fromPosition, batchSize ->
    flow {
        var currentPosition = fromPosition
        eventStream.stream(fromPosition, batchSize).collect { event ->
            emit(StreamedEvent(event, StreamOffset(currentPosition)))
            currentPosition++
        }
    }
}
