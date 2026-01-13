package org.example.common.extensions

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.example.common.serializers.InstantSerializer
import org.example.common.serializers.UUIDSerializer

/** Default JSON serializer with default encoding enabled and custom serializers. */
val json = Json {
    encodeDefaults = true
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(InstantSerializer)
    }
}

/** Decode JSON string to object */
inline fun <reified T : Any> String.fromJSON(): T = json.decodeFromString<T>(this)

/** Encode object to JSON string */
inline fun <reified T> T.toJSON(): String = json.encodeToString(this)
