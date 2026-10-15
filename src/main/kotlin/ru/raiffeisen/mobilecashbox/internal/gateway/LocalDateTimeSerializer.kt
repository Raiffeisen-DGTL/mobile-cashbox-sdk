package ru.raiffeisen.mobilecashbox.internal.gateway

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter

public object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ru.raiffeisen.mobilecashbox.internal.gateway.LocalDateTimeSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime): Unit =
        encoder.encodeString(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value))

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val date = decoder.decodeString()
        return try {
            LocalDateTime.parse(date)
        } catch (e: DateTimeParseException) {
            OffsetDateTime.parse(date).toLocalDateTime()
        }
    }
}
