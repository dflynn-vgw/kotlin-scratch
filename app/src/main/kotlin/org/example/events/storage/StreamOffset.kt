package org.example.events.storage

/**
 * Represents a position in an event stream.
 * Provides type safety and makes position semantics explicit.
 */
data class StreamOffset(
    val position: Long,
) {
    init {
        require(position >= 0) { "position must be non-negative, got $position" }
    }
}
