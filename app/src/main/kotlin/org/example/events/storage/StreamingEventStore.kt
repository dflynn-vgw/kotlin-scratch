package org.example.events.storage

import kotlinx.coroutines.flow.Flow
import org.example.events.Event

/** Event store with streaming and position tracking capabilities for continuous event consumption */
interface StreamingEventStore : EventStore {
    /** Stream events from a given position onwards */
    fun stream(fromPosition: Long = 0): Flow<Event<Any>>

    /** Save a named bookmark (consumer progress checkpoint) */
    suspend fun saveBookmark(name: String, position: Long)

    /** Retrieve a named bookmark, returns null if not found */
    suspend fun getBookmark(name: String): Bookmark?
}

/** Represents a consumer's progress checkpoint */
data class Bookmark(
    val name: String,
    val position: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
