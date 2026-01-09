package org.example.common

import org.example.events.Event
import org.junit.jupiter.params.converter.TypedArgumentConverter

/** Converts a string representation (e.g. [ORDER_PLACED, ORDER_CONFIRMED, ORDER_CANCELLED, ORDER_MODIFIED]) of a list of Event.EventType to an actual List<Event.EventType> */
@Suppress("UNCHECKED_CAST")
class EventTypeListConverter :
    TypedArgumentConverter<String, List<Event.EventType>>(
        String::class.java,
        List::class.java as Class<List<Event.EventType>>,
    ) {
    override fun convert(source: String): List<Event.EventType> = source
        .removeSurrounding("[", "]")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { Event.EventType.valueOf(it) }
}
