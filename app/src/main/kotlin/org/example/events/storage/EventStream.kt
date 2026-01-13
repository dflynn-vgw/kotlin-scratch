package org.example.events.storage

import kotlinx.coroutines.flow.Flow
import org.example.events.Event

/** Event store with streaming and position tracking capabilities for continuous event consumption */
interface EventStream {
    /** Stream events from a given position onwards */
    fun stream(fromPosition: Long = 0, batchSize: Int = 1): Flow<Event<*>>

    /** Save a named bookmark (consumer progress checkpoint) */
    suspend fun saveBookmark(name: String, position: Long)

    /** Retrieve a named bookmark, returns null if not found */
    suspend fun getBookmark(name: String): Bookmark?
}
