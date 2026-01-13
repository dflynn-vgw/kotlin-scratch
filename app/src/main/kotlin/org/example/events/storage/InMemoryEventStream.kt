package org.example.events.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.example.events.Event
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** In-memory stream with bookmark support */
class InMemoryEventStream(
    seedEvents: List<Event<*>> = emptyList(),
    private val fixedTime: Long? = null,
) : EventStream {

    // Thread-safe bookmark storage
    private val bookmarks = ConcurrentHashMap<String, Bookmark>()

    // Global event log in order (built from seed + saves) with synchronization (thread-safe)
    private val eventLog = Collections.synchronizedList(mutableListOf<Event<*>>())

    init {
        eventLog.addAll(seedEvents)
    }

    /** Stream events from a given position onwards */
    override fun stream(fromPosition: Long, batchSize: Int): Flow<Event<*>> = flow {
        require(fromPosition >= 0) { "fromPosition must be positive" }
        require(batchSize > 0) { "batchSize must be positive" }

        // Ensure fromPosition fits in Int for indexing purposes (eventLog uses Int indices)
        require(fromPosition <= Int.MAX_VALUE) { "fromPosition $fromPosition exceeds Int.MAX_VALUE" }

        val events: List<Event<*>>

        /* Synchronized access to the event log to ensure thread safety
            - The synchronized block creates a snapshot (events list).
            - This prevents concurrent modification issues during iteration.
         */
        synchronized(eventLog) {
            val to = (fromPosition + batchSize).coerceAtMost(eventLog.size.toLong()).toInt()
            events = (fromPosition.toInt() until to).map { eventLog[it] }
        }

        // Iteration over the snapshot outside the block is safe and avoids holding the lock longer than needed.
        events.forEach { emit(it) }
    }

    /** Save a bookmark for a named consumer */
    override suspend fun saveBookmark(name: String, position: Long) {
        bookmarks[name] = Bookmark(name = name, position = position, updatedAt = fixedTime ?: System.currentTimeMillis())
    }

    /** Retrieve a bookmark by name */
    override suspend fun getBookmark(name: String): Bookmark? = bookmarks[name]
}
