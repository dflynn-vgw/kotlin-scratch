package org.example.domn.common

/** Interface representing an entity that can create a snapshot of itself */
interface Snapshotable {
    fun snapshot(): Identifiable
}