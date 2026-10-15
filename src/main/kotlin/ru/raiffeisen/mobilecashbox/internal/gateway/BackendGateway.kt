package ru.raiffeisen.mobilecashbox.internal.gateway

import kotlinx.coroutines.flow.Flow
import ru.raiffeisen.mobilecashbox.AuthorizationCredential
import ru.raiffeisen.mobilecashbox.MobileCashboxEvent
import ru.raiffeisen.mobilecashbox.internal.gateway.outcome.AuthorizeOutcome
import ru.raiffeisen.mobilecashbox.internal.gateway.outcome.GatewayResult
import ru.raiffeisen.mobilecashbox.model.PaymentData
import ru.raiffeisen.mobilecashbox.model.ReceiptData
import ru.raiffeisen.mobilecashbox.model.RefundData

internal interface BackendGateway : AutoCloseable {
    val pushedEvents: Flow<MobileCashboxEvent>

    fun setCredentials(credentials: AuthorizationCredential)

    suspend fun checkUser(): GatewayResult<AuthorizeOutcome>

    suspend fun initializeMobileCashboxPayment(
        paymentData: PaymentData,
    ): Map<String, String>

    suspend fun initializeMobileCashboxRefund(
        refundData: RefundData,
    ): Map<String, String>

    suspend fun initializeMobileCashboxReceipt(
        receiptData: ReceiptData,
    ): Map<String, String>

    suspend fun initializeMobileCashboxLogs(): Map<String, String>
}
