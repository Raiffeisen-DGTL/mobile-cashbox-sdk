package ru.raiffeisen.mobilecashbox.internal.gateway.dto

import kotlinx.serialization.Serializable
import ru.raiffeisen.mobilecashbox.internal.gateway.BigDecimalSerializer
import ru.raiffeisen.mobilecashbox.internal.gateway.LocalDateTimeSerializer
import ru.raiffeisen.mobilecashbox.AuthorizationCredential
import ru.raiffeisen.mobilecashbox.internal.gateway.toCredentialContacts
import java.math.BigDecimal
import java.time.LocalDateTime

@Serializable
internal data class MobileCashboxCashierDto(
    val email: String?,
    val phone: String?,
)

@Serializable
internal abstract class MobileCashboxInitRequestDto(
    var cashier: MobileCashboxCashierDto? = null,
) {
    fun setCredentials(credentials: AuthorizationCredential) {
        val contacts = credentials.toCredentialContacts()
        require(!contacts.isEmpty) { "Credentials missing" }
        cashier = MobileCashboxCashierDto(
            email = contacts.email,
            phone = contacts.phone,
        )
    }
}

@Serializable
internal class MobileCashboxPaymentInitRequestDto(
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
) : MobileCashboxInitRequestDto()

@Serializable
internal class MobileCashboxRefundInitRequestDto(
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val transaction: MobileCashboxRefundTransactionDto,
) : MobileCashboxInitRequestDto()

@Serializable
internal data class MobileCashboxRefundTransactionDto(
    val id: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val date: LocalDateTime,
)

@Serializable
internal class MobileCashboxReceiptInitRequestDto(
    val receiptEmail: String,
    val transaction: MobileCashboxTransactionReferenceDto,
) : MobileCashboxInitRequestDto()

@Serializable
internal class MobileCashboxLogsInitRequestDto : MobileCashboxInitRequestDto()

@Serializable
internal data class MobileCashboxTransactionReferenceDto(
    val id: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val date: LocalDateTime,
)
