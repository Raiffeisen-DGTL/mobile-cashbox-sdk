package ru.raiffeisen.mobilecashbox.internal.gateway

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

internal object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ru.raiffeisen.mobilecashbox.internal.gateway.BigDecimalSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) {
            @OptIn(ExperimentalSerializationApi::class)
            val literal = JsonUnquotedLiteral(value.toPlainString())
            encoder.encodeJsonElement(literal)
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val data = if (decoder is JsonDecoder) {
            // The number may be represented directly, but we can't read it as Double as we'll lose precision
            (decoder.decodeJsonElement() as JsonPrimitive).content
        } else {
            decoder.decodeString()
        }
        return BigDecimal(data)
    }
}
