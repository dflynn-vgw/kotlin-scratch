package org.example.events.storage

import org.example.events.Event

/** Essential event store operations: saving single or multiple events and reading events from a stream */
interface EventStore {
    /** Save a single event to the store */
    suspend fun save(event: Event<*>)

    /** Save multiple events to the store */
    suspend fun save(events: List<Event<*>>)

    /** Read events from a specific stream starting from a given version */
    suspend fun read(streamId: String, fromVersion: Int = 0): List<Event<*>>
}
