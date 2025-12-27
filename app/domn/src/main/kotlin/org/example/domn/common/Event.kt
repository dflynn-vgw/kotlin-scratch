package org.example.domn.common

import org.example.core.types.Id
import org.example.domn.wallet.types.Meta
import org.example.domn.wallet.types.Version

/** Generic interface representing an event associated with an aggregate snapshot of TSnapshot */
interface Event<TSnapshot : Identifiable> : Identifiable {
  /** Unique identifier for the event */
  override val id: Id

  /** Version of the event for concurrency control */
  val version: Version

  /** Stream to which the event belongs */
  val stream: String

  /** Type of the event */
  val type: String

  /** Snapshot of the aggregate at the time of the event */
  val snapshot: TSnapshot

  /** Metadata associated with the event */
  val metadata: Meta
}