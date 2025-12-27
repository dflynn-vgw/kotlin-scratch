package org.example.domn.common

import org.example.domn.common.Event
import org.example.domn.common.Version

/** Abstract base class for an aggregate root in an event-sourced system */
abstract class Aggregate<T : Identifiable> {
  private val _events = mutableListOf<EventState<T>>()
  private var _version: Version = Version.Companion.ZERO
  private var _snapshot: T? = null

  /** Applies an event to the aggregate, updating its state (overridden by subclass) */
  abstract fun apply(event: Event<T>, isNew: Boolean = false): T

  /** Saves a list of events to the event store (overridden by subclass) */
  abstract fun save(events: List<Event<T>>): Result<T>

  /** Loads a list of events to reconstruct the aggregate's state */
  fun load(events: List<Event<T>>): Result<T?> {
    try {
      events.forEach { event ->
        apply(event, isNew = false)
        _version = event.version
        _snapshot = event.snapshot
      }
    } catch (e: Exception) {
      return Result.failure(e)
    }

    return Result.success(this._snapshot)
  }

  /** Stages a new event for the aggregate, updating its state */
  fun stage(event: Event<T>): Result<T?> {
    try {
      _events.add(EventState(event = event, isCommited = false))
      _version = event.version
      _snapshot = event.snapshot
    } catch (e: Exception) {
      return Result.failure(e)
    }

    return Result.success(_snapshot)
  }

  /** Commits uncommitted events (calls save(), for persistence) */
  fun commit(): Result<T?> {
    val uncommitedEvents = _events.filter { !it.isCommited }.map { it.event }
    save(uncommitedEvents).fold(
      onFailure = { return Result.failure(it) },
      onSuccess = {
        _events.filter { !it.isCommited }.forEach {
          _events.remove(it)
          _events.add(it.copy(isCommited = true))
        }
      }
    )

    return Result.success(this._snapshot)
  }

  /** Represents the state of an event along with its commit status */
  data class EventState<T : Identifiable>(
      val event: Event<T>,
      val isCommited: Boolean = false,
  )
}