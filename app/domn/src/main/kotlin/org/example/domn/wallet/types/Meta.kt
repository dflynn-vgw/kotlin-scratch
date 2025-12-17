package org.example.domn.wallet.types

import org.example.core.types.Epoch
import org.example.core.types.now

/** Data class representing metadata for wallet operations */
data class Meta (
    /** Identifier of the entity that created the operation */
    val createdBy: String,
    /** Timestamp when the operation was created */
    val createdAt: Epoch = now(),
    /** Additional context information as key-value pairs */
    val context: Map<String, Any> = emptyMap()
)