package org.example.common.extensions

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Default JSON serializer with default encoding enabled (see https://www.baeldung.com/kotlin/data-class-json-serialize-default-values)*/
val json = Json { encodeDefaults = true }

/** Decode JSON string to object */
inline fun <reified T : Any> String.fromJSON(): T = json.decodeFromString<T>(this)

/** Encode object to JSON string */
inline fun <reified T> T.toJSON(): String = json.encodeToString(this)
