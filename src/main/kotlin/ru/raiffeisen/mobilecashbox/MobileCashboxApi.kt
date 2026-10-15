package ru.raiffeisen.mobilecashbox

import ru.raiffeisen.mobilecashbox.internal.MobileCashboxApiImpl
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import ru.raiffeisen.mobilecashbox.model.PaymentData
import ru.raiffeisen.mobilecashbox.model.ReceiptData
import ru.raiffeisen.mobilecashbox.model.RefundData

public interface MobileCashboxApi : AutoCloseable {
    public val connectionState: StateFlow<ConnectionState>
    public val events: SharedFlow<MobileCashboxEvent>

    public fun setUser(credential: AuthorizationCredential)

    public fun auth()

    /**
     * Starts a payment. Call only when no payment or refund is currently in progress; wait for
     * [MobileCashboxEvent.OperationResult] before starting the next operation.
     */
    public fun performPayment(paymentData: PaymentData)

    /**
     * Starts a refund. Call only when no payment or refund is currently in progress; wait for
     * [MobileCashboxEvent.OperationResult] before starting the next operation.
     */
    public fun performRefund(refundData: RefundData)

    /**
     * Starts sending a receipt for an existing payment. Wait for
     * [MobileCashboxEvent.OperationResult] before starting another operation.
     */
    public fun sendReceipt(receiptData: ReceiptData)

    /**
     * Starts sending Mobile Cashbox logs. Wait for [MobileCashboxEvent.OperationResult] before
     * starting another operation.
     */
    public fun sendLogs()

    public fun addListener(listener: MobileCashboxListener)
    public fun removeListener(listener: MobileCashboxListener)

    override fun close()
}

public fun mobileCashboxApi(token: String): MobileCashboxApi = MobileCashboxApiImpl(token)
