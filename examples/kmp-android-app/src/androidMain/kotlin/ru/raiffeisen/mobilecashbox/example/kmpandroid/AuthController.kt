package ru.raiffeisen.mobilecashbox.example.kmpandroid

import ru.raiffeisen.mobilecashbox.AuthorizationCredential
import ru.raiffeisen.mobilecashbox.mobileCashboxApi
import ru.raiffeisen.mobilecashbox.MobileCashboxApi
import ru.raiffeisen.mobilecashbox.model.PaymentData
import ru.raiffeisen.mobilecashbox.model.ReceiptData
import ru.raiffeisen.mobilecashbox.model.RefundData
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Android-side glue around [MobileCashboxApi]. The SDK is an Android AAR, so a KMP consumer adds
 * it to androidMain rather than exposing it from commonMain.
 */
class AuthController {
    val lib: MobileCashboxApi = mobileCashboxApi("replace-with-secret-key")

    fun authorizeByEmail(email: String) {
        lib.setUser(AuthorizationCredential.Email(email))
        lib.auth()
    }
    fun authorizeByPhone(phone: String) {
        lib.setUser(AuthorizationCredential.Phone(phone))
        lib.auth()
    }
    fun authorizeByEmailAndPhone(email: String, phone: String) {
        lib.setUser(AuthorizationCredential.EmailAndPhone(email, phone))
        lib.auth()
    }

    fun performPayment(amount: BigDecimal) {
        lib.performPayment(PaymentData(amount))
    }

    fun performRefund(
        amount: BigDecimal,
        transactionId: String,
        transactionDate: LocalDateTime,
    ) {
        lib.performRefund(
            RefundData(
                amount = amount,
                transactionId = transactionId,
                transactionDate = transactionDate,
            ),
        )
    }

    fun sendReceipt(
        receiptEmail: String,
        transactionId: String,
        transactionDate: LocalDateTime,
    ) {
        lib.sendReceipt(
            ReceiptData(
                receiptEmail = receiptEmail,
                transactionId = transactionId,
                transactionDate = transactionDate,
            ),
        )
    }

    fun sendLogs() {
        lib.sendLogs()
    }

    fun close() = lib.close()
}
