package org.example.events.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.example.events.Event
import java.util.concurrent.ConcurrentHashMap

/** In-memory streaming event store with bookmark support */
class InMemoryStreamingEventStore(
    seedEvents: List<Event<Any>> = emptyList(),
) : InMemoryEventStore(seedEvents),
    StreamingEventStore {

    // Thread-safe bookmark storage
    private val bookmarks = ConcurrentHashMap<String, Bookmark>()

    // Global event log in order (built from seed + saves)
    private val eventLog = mutableListOf<Event<Any>>()
    private val eventLogLock = Any()

    init {
        synchronized(eventLogLock) {
            eventLog.addAll(seedEvents)
        }
    }

    /** Stream events from a given position onwards */
    override fun stream(fromPosition: Long): Flow<Event<Any>> = flow {
        val events = synchronized(eventLogLock) {
            (fromPosition.toInt() until eventLog.size).map { eventLog[it] }
        }
        events.forEach { emit(it) }
    }

    /** Save a bookmark for a named consumer */
    override suspend fun saveBookmark(name: String, position: Long) {
        bookmarks[name] = Bookmark(name = name, position = position)
    }

    /** Retrieve a bookmark by name */
    override suspend fun getBookmark(name: String): Bookmark? = bookmarks[name]

    /** Override parent's save to maintain event log */
    override suspend fun save(event: Event<Any>) {
        super.save(event)
        synchronized(eventLogLock) {
            eventLog.add(event)
        }
    }

    /** Override parent's save to maintain event log */
    override suspend fun save(events: List<Event<Any>>) {
        super.save(events)
        synchronized(eventLogLock) {
            eventLog.addAll(events)
        }
    }
}
