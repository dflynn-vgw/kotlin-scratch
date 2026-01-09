package org.example.events.storage

import org.example.events.Event
import java.util.concurrent.ConcurrentHashMap

/** In-memory implementation of the EventStore for testing or lightweight scenarios */
class InMemoryEventStore(
    seedEvents: List<Event<Any>> = emptyList(),
) : EventStore {
    // In-memory store mapping stream IDs to their respective events (thread-safe)
    private val store = ConcurrentHashMap<String, MutableList<Event<Any>>>()

    init {
        // Seed the store with initial events if provided
        seedEvents.forEach { event ->
            store.computeIfAbsent(event.streamId) { mutableListOf() }.add(event)
        }
    }

    /** Save a single event to the store */
    override suspend fun save(event: Event<Any>) {
        // Add the event to the list for its stream ID, creating the list if it doesn't exist
        store.computeIfAbsent(event.streamId) { mutableListOf() }.add(event)
    }

    /** Save multiple events to the store */
    override suspend fun save(events: List<Event<Any>>) {
        events.forEach { save(it) }
    }

    /** Read events from a specific stream starting from a given version */
    override suspend fun read(streamId: String, fromVersion: Int): List<Event<Any>> = store[streamId]
        ?.filter { it.version >= fromVersion }
        ?.sortedBy { it.version }
        ?: emptyList()
}
