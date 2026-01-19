package org.example.common.extensions

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.example.common.serializers.InstantSerializer
import org.example.common.serializers.UUIDSerializer
import org.example.events.Event
import org.example.events.OrderEvents

/** Default JSON serializer with default encoding enabled and custom serializers. */
val json = Json {
    encodeDefaults = true
    classDiscriminator = "__type"
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(InstantSerializer)

        polymorphic(Event::class) {
            subclass(OrderEvents.OrderPlacedEvent::class)
            subclass(OrderEvents.OrderModifiedEvent::class)
            subclass(OrderEvents.OrderConfirmedEvent::class)
            subclass(OrderEvents.OrderCancelledEvent::class)
        }
    }
}

/** Decode JSON string to object */
inline fun <reified T : Any> String.fromJSON(): T = json.decodeFromString<T>(this)

/** Encode object to JSON string */
inline fun <reified T> T.toJSON(): String = json.encodeToString(this)
