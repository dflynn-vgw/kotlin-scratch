package org.example.events.storage

import kotlinx.serialization.Serializable

/**
 * Represents a position in an event stream.
 * Used for tracking stream position for bookmarking and replay.
 */
@Serializable
data class StreamOffset(
    val position: Long,
) {
    init {
        require(position >= 0) { "position must be non-negative, got $position" }
    }
}
