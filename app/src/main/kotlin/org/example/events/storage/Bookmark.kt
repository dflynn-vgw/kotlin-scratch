package org.example.events.storage

/** Represents a consumer's progress checkpoint */
data class Bookmark(
    val name: String,
    val position: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
