package org.example.core.types

import java.util.UUID

/** Type alias for unique identifiers used across the application. */
typealias Id = UUID

/** Type alias for epoch time in seconds (seconds from 1970-01-01) */
typealias Epoch = Long

/** Extension function to get the current epoch time in seconds */
fun now(): Epoch = System.currentTimeMillis() / 1000