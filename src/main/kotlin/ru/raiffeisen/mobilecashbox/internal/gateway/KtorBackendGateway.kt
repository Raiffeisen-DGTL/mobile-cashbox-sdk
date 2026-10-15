package ru.raiffeisen.mobilecashbox.internal.gateway

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import ru.raiffeisen.mobilecashbox.AuthorizationCredential
import ru.raiffeisen.mobilecashbox.MobileCashboxEvent
import ru.raiffeisen.mobilecashbox.ConnectionFailureReason
import ru.raiffeisen.mobilecashbox.internal.DEFAULT_JSON
import ru.raiffeisen.mobilecashbox.internal.MobileCashboxEnvironment
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.OperatorCredentialsRequest
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.ProblemDetails
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.MobileCashboxInitRequestDto
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.MobileCashboxLogsInitRequestDto
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.MobileCashboxPaymentInitRequestDto
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.MobileCashboxReceiptInitRequestDto
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.MobileCashboxRefundInitRequestDto
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.MobileCashboxRefundTransactionDto
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.MobileCashboxTransactionReferenceDto
import ru.raiffeisen.mobilecashbox.internal.gateway.outcome.AuthorizeOutcome
import ru.raiffeisen.mobilecashbox.internal.gateway.outcome.GatewayResult
import ru.raiffeisen.mobilecashbox.model.PaymentData
import ru.raiffeisen.mobilecashbox.model.ReceiptData
import ru.raiffeisen.mobilecashbox.model.RefundData
import java.net.SocketTimeoutException

/**
 * Talks to the backend over a real Ktor [io.ktor.client.HttpClient]. Swapping [client]'s engine (and [baseUrl])
 * for a live one is the only change needed to go from mock to production — nothing above this
 * class knows or cares which engine is behind it.
 *
 * Bodies are (de)serialized explicitly with [kotlinx.serialization.json.Json] rather than via Ktor's ContentNegotiation
 * plugin: that plugin looks up serializers through runtime reflection on class/method names,
 * which breaks once [ru.raiffeisen.mobilecashbox.internal.dto] classes are obfuscated. Explicit
 * (de)serialization has no such dependency.
 */
internal class KtorBackendGateway(
    private val client: HttpClient,
    private val baseUrl: String = MobileCashboxEnvironment.baseUrl,
) : BackendGateway {

    // Placeholder for a real-time channel (websocket/SSE) a real backend would push through.
    private val pushed = MutableSharedFlow<MobileCashboxEvent>(extraBufferCapacity = 16)
    override val pushedEvents: Flow<MobileCashboxEvent> = pushed.asSharedFlow()

    private var credentials: AuthorizationCredential? = null

    override fun setCredentials(credentials: AuthorizationCredential) {
        this.credentials = credentials
    }

    override suspend fun checkUser(): GatewayResult<AuthorizeOutcome> {
        val contacts = credentials?.toCredentialContacts()
            ?: return GatewayResult.Failure(ConnectionFailureReason.InvalidCredential)
        if (contacts.isEmpty) {
            return GatewayResult.Failure(ConnectionFailureReason.InvalidCredential)
        }
        val request = OperatorCredentialsRequest(
            email = contacts.email,
            phone = contacts.phone,
        )

        return try {
            client.post("$baseUrl/mobilecashbox/v1/check") {
                setBody(CHECK_REQUEST_JSON.encodeToString(request))
            }.toCheckUserResult()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpRequestTimeoutException) {
            GatewayResult.Failure(ConnectionFailureReason.Timeout)
        } catch (exception: ConnectTimeoutException) {
            GatewayResult.Failure(ConnectionFailureReason.Timeout)
        } catch (exception: SocketTimeoutException) {
            GatewayResult.Failure(ConnectionFailureReason.Timeout)
        } catch (exception: Exception) {
            GatewayResult.Failure(ConnectionFailureReason.NetworkUnavailable)
        }
    }

    override suspend fun initializeMobileCashboxPayment(
        paymentData: PaymentData,
    ): Map<String, String> {
        val response = client.post("$baseUrl/mobilecashbox/v1/payments/init") {
            setBody(
                DEFAULT_JSON.encodeToString(
                    MobileCashboxPaymentInitRequestDto(
                        amount = paymentData.amount,
                    ).applyCredentials()
                )
            )
        }
        return response.decodeApp2AppMap()
    }

    override suspend fun initializeMobileCashboxRefund(
        refundData: RefundData,
    ): Map<String, String> {
        val response = client.post("$baseUrl/mobilecashbox/v1/refunds/init") {
            setBody(
                DEFAULT_JSON.encodeToString(
                    MobileCashboxRefundInitRequestDto(
                        amount = refundData.amount,
                        transaction = MobileCashboxRefundTransactionDto(
                            id = refundData.transactionId,
                            date = refundData.transactionDate,
                        ),
                    ).applyCredentials()
                )
            )
        }
        return response.decodeApp2AppMap()
    }

    override suspend fun initializeMobileCashboxReceipt(
        receiptData: ReceiptData,
    ): Map<String, String> {
        val response = client.post("$baseUrl/mobilecashbox/v1/receipts/init") {
            setBody(
                DEFAULT_JSON.encodeToString(
                    MobileCashboxReceiptInitRequestDto(
                        receiptEmail = receiptData.receiptEmail,
                        transaction = MobileCashboxTransactionReferenceDto(
                            id = receiptData.transactionId,
                            date = receiptData.transactionDate,
                        ),
                    ).applyCredentials()
                )
            )
        }
        return response.decodeApp2AppMap()
    }

    override suspend fun initializeMobileCashboxLogs(): Map<String, String> {
        val response = client.post("$baseUrl/mobilecashbox/v1/logs/init") {
            setBody(
                DEFAULT_JSON.encodeToString(
                    MobileCashboxLogsInitRequestDto().applyCredentials()
                )
            )
        }
        return response.decodeApp2AppMap()
    }

    override fun close() {
        client.close()
    }

    private fun <T : MobileCashboxInitRequestDto> T.applyCredentials(): T = apply {
        setCredentials(requireNotNull(credentials) { "Credentials missing" })
    }

    /** A check succeeds solely by its 2xx status; its response body may be empty. */
    private suspend fun HttpResponse.toCheckUserResult(): GatewayResult<AuthorizeOutcome> {
        if (status.isSuccess()) return GatewayResult.Success(AuthorizeOutcome.Authorized)

        val problemCode = bodyAsText()
            .takeIf(String::isNotBlank)
            ?.let { body -> runCatching { DEFAULT_JSON.decodeFromString<ProblemDetails>(body) }.getOrNull() }
            ?.let { it.code ?: it.title }

        val reason = when (problemCode) {
            "CASHIER_NOT_REGISTERED" -> ConnectionFailureReason.CashierNotRegistered
            "NO_PERMISSION_FOR_MERCHANT" -> ConnectionFailureReason.NoPermissionForMerchant
            "CASHIER_IS_NOT_ACTIVATED" -> ConnectionFailureReason.CashierNotActivated
            else -> when {
                status == HttpStatusCode.Unauthorized -> ConnectionFailureReason.InvalidToken
                status == HttpStatusCode.BadRequest -> ConnectionFailureReason.BadRequest(problemCode)
                status == HttpStatusCode.Forbidden -> ConnectionFailureReason.Forbidden(problemCode)
                status.value >= 500 -> ConnectionFailureReason.ServerError(status.value)
                else -> ConnectionFailureReason.Unknown(problemCode ?: "HTTP ${status.value}")
            }
        }
        return GatewayResult.Failure(reason)
    }

    private suspend fun HttpResponse.decodeApp2AppMap(): Map<String, String> {
        val responseBody = bodyAsText()
        if (!status.isSuccess()) throw ResponseException(this, responseBody)
        return DEFAULT_JSON.decodeFromString(responseBody)
    }

    private companion object {
        // The check contract distinguishes nullable identifiers and documents both fields in the
        // wire model, so unlike older requests it emits explicit JSON nulls.
        val CHECK_REQUEST_JSON: Json = Json(DEFAULT_JSON) { explicitNulls = true }
    }
}
