package org.example.domn.wallet.events

import org.example.core.types.Id
import org.example.domn.common.Identifiable
import org.example.domn.common.Snapshotable
import org.example.domn.wallet.types.Meta

/** Generic interface representing an event associated with an aggregate snapshot of TSnapshot */
interface Event<TSnapshot: Identifiable> : Identifiable {
    /** Unique identifier for the event */
    override val id: Id
    /** Stream to which the event belongs */
    val stream: String
    /** Type of the event */
    val type: String
    /** Version of the event for concurrency control */
    val version : Int
    /** Snapshot of the aggregate at the time of the event */
    val snapshot: TSnapshot
    /** Metadata associated with the event */
    val metadata: Meta
}