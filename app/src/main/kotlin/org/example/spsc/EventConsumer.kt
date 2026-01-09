package org.example.spsc

import org.example.events.Event
import org.example.events.storage.Bookmark

/**
 * Consumes events from the SPSC queue.
 * Receives a batch of events and the current bookmark position.
 * Must complete successfully to advance the bookmark (on failure, bookmark is not advanced).
 */
fun interface EventConsumer {
    /**
     * Process a batch of events.
     * @param events the batch of events to consume
     * @param bookmark the current bookmark position (before consuming these events)
     * @throws Exception to indicate failure - bookmark will NOT be advanced
     */
    suspend fun consume(events: List<Event<Any>>, bookmark: Bookmark)
}
