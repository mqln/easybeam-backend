package com.pollywog.prompts

import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

typealias PromptConfig = Map<String, ConfigValue>

@Serializable(with = ConfigValueSerializer::class)
sealed class ConfigValue {
    data class StrValue(val value: String) : ConfigValue()
    data class NumValue(val value: Double) : ConfigValue()
    data class StrListValue(val value: List<String>) : ConfigValue()
}

object ConfigValueSerializer : KSerializer<ConfigValue> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ConfigValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ConfigValue) {
        when (value) {
            is ConfigValue.StrValue -> encoder.encodeString(value.value)
            is ConfigValue.NumValue -> encoder.encodeDouble(value.value)
            is ConfigValue.StrListValue -> encoder.encodeSerializableValue(
                ListSerializer(String.serializer()), value.value
            )
        }
    }

    override fun deserialize(decoder: Decoder): ConfigValue {
        val input = decoder as? JsonDecoder ?: throw SerializationException("This class can be loaded only by JSON")
        val element = input.decodeJsonElement()

        return when {
            element is JsonPrimitive && element.isString -> ConfigValue.StrValue(element.content)
            element is JsonPrimitive && element.doubleOrNull != null -> ConfigValue.NumValue(element.double)
            element is JsonArray -> ConfigValue.StrListValue(element.map { (it as JsonPrimitive).content })
            else -> throw SerializationException("Unexpected value $element")
        }
    }
}

fun Map<String, ConfigValue>.getString(key: String): String {
    val value = this[key]
    require(value is ConfigValue.StrValue) { "Expected a string value for key: $key" }
    return value.value
}

fun Map<String, ConfigValue>.getDouble(key: String): Double {
    val value = this[key]
    require(value is ConfigValue.NumValue) { "Expected a number value for key: $key" }
    return value.value
}

fun Map<String, ConfigValue>.getStringList(key: String): List<String> {
    val value = this[key]
    require(value is ConfigValue.StrListValue) { "Expected a string list value for key: $key" }
    return value.value
}