package ru.raiffeisen.mobilecashbox.internal.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_RESULT_DATA_KEY
import ru.raiffeisen.mobilecashbox.MobileCashboxMerchant
import ru.raiffeisen.mobilecashbox.MobileCashboxOperationResult
import ru.raiffeisen.mobilecashbox.MobileCashboxTechnicalFailureReason
import ru.raiffeisen.mobilecashbox.MobileCashboxTransaction
import ru.raiffeisen.mobilecashbox.internal.DEFAULT_JSON
import java.math.BigDecimal

internal object SoftPosOperationResultParser {
    fun parse(resultData: Map<String, String>): MobileCashboxOperationResult {
        val payload = resultData[EXTERNAL_APP_RESULT_DATA_KEY]
            ?: return technicalFailure(MobileCashboxTechnicalFailureReason.RESULT_DATA_KEY_MISSING)

        if (payload.isBlank()) {
            return technicalFailure(MobileCashboxTechnicalFailureReason.RESULT_DATA_BLANK)
        }

        val response = try {
            DEFAULT_JSON.decodeFromString<SoftPosVendorAppResponseDto>(payload)
        } catch (exception: Exception) {
            return technicalFailure(MobileCashboxTechnicalFailureReason.INVALID_RESULT_JSON)
        }

        if (response.result?.code?.content != SUCCESS_CODE) {
            return MobileCashboxOperationResult.Failure(
                code = response.result?.errorData?.code?.content,
                description = response.result?.errorData?.description,
            )
        }

        return MobileCashboxOperationResult.Success(response.transaction?.toDomain())
    }

    private fun technicalFailure(
        reason: MobileCashboxTechnicalFailureReason,
    ): MobileCashboxOperationResult = MobileCashboxOperationResult.TechnicalFailure(reason)

    private const val SUCCESS_CODE: String = "0"
}

private fun SoftPosTransactionDto.toDomain(): MobileCashboxTransaction =
    MobileCashboxTransaction(
        terminalId = terminalId,
        operationDay = operationDay,
        transactionNumber = transactionNumber,
        reversible = reversible,
        externalTerminalId = externalTerminalId,
        dateTime = dateTime ?: dateAndTime,
        operationExternalId = operationExternalId,
        status = status?.takeIf(String::isNotBlank),
        statusDescription = statusDescription,
        instrument = instrument,
        type = type,
        paymentSystemType = paymentSystemType,
        amount = amountData?.decimalAmountOrNull(),
        currencyCode = amountData?.currencyCode?.content,
        companyName = company?.name,
        merchant = merchant?.let {
            MobileCashboxMerchant(
                id = it.merchantId,
                name = it.name,
                address = it.address,
                phoneNumber = it.phoneNumber,
            )
        },
    )

@Serializable
internal data class SoftPosVendorAppResponseDto(
    val result: SoftPosResultDto? = null,
    val transaction: SoftPosTransactionDto? = null,
)

@Serializable
internal data class SoftPosResultDto(
    val code: JsonPrimitive? = null,
    val description: String? = null,
    val hostResponse: SoftPosHostResponseDto? = null,
    val errorData: SoftPosErrorDataDto? = null,
)

@Serializable
internal data class SoftPosHostResponseDto(
    val code: JsonPrimitive? = null,
    val description: String? = null,
)

@Serializable
internal data class SoftPosErrorDataDto(
    val code: JsonPrimitive? = null,
    val description: String? = null,
)

@Serializable
internal data class SoftPosTransactionDto(
    val terminalId: Int? = null,
    val operationDay: Int? = null,
    val transactionNumber: Int? = null,
    val reversible: Boolean? = null,
    val externalTerminalId: String? = null,
    val dateTime: String? = null,
    val operationExternalId: String? = null,
    val status: String? = null,
    val statusDescription: String? = null,
    val instrument: String? = null,
    val type: String? = null,
    val dateAndTime: String? = null,
    val paymentSystemType: String? = null,
    val instrumentSpecificData: JsonObject? = null,
    val amountData: SoftPosAmountDataDto? = null,
    val company: SoftPosCompanyDto? = null,
    val merchant: SoftPosMerchantDto? = null,
)

@Serializable
internal data class SoftPosAmountDataDto(
    val currencyCode: JsonPrimitive = JsonPrimitive("643"),
    val amount: JsonPrimitive,
    val amountExponent: JsonPrimitive = JsonPrimitive("2"),
) {
    fun decimalAmount(): BigDecimal =
        BigDecimal(amount.content).movePointLeft(amountExponent.content.toInt())

    fun decimalAmountOrNull(): BigDecimal? = runCatching(::decimalAmount).getOrNull()
}

@Serializable
internal data class SoftPosCompanyDto(
    val name: String? = null,
)

@Serializable
internal data class SoftPosMerchantDto(
    val merchantId: String? = null,
    val name: String? = null,
    val address: String? = null,
    val phoneNumber: String? = null,
)
