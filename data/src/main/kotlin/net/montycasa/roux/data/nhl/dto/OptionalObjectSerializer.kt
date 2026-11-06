package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder

/** Bad optional details become absent; identity errors still reach the per-game boundary. */
internal abstract class OptionalObjectSerializer<T : Any>(
    private val delegate: KSerializer<T>,
) : KSerializer<T?> {
    override val descriptor = delegate.nullable.descriptor

    override fun deserialize(decoder: Decoder): T? {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return try {
            jsonDecoder.json.decodeFromJsonElement(delegate.nullable, element)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: T?,
    ) = encoder.encodeSerializableValue(delegate.nullable, value)
}

internal object GameOutcomeSerializer : OptionalObjectSerializer<GameOutcomeDto>(GameOutcomeDto.serializer())

internal object SeriesStatusSerializer : OptionalObjectSerializer<SeriesStatusDto>(SeriesStatusDto.serializer())

internal object PeriodDescriptorSerializer : OptionalObjectSerializer<PeriodDescriptorDto>(PeriodDescriptorDto.serializer())

internal object ClockSerializer : OptionalObjectSerializer<ClockDto>(ClockDto.serializer())
