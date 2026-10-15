package ru.raiffeisen.mobilecashbox.internal.gateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertIs
import ru.raiffeisen.mobilecashbox.AuthorizationCredential
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_RESULT_DATA_KEY
import ru.raiffeisen.mobilecashbox.ExternalAppResult
import ru.raiffeisen.mobilecashbox.MobileCashboxEvent
import ru.raiffeisen.mobilecashbox.MobileCashboxOperationResult
import ru.raiffeisen.mobilecashbox.MobileCashboxOperationType
import ru.raiffeisen.mobilecashbox.MobileCashboxTechnicalFailureReason
import ru.raiffeisen.mobilecashbox.MobileCashboxTransaction
import ru.raiffeisen.mobilecashbox.internal.DEFAULT_JSON
import ru.raiffeisen.mobilecashbox.internal.MobileCashboxEnvironment
import ru.raiffeisen.mobilecashbox.internal.MobileCashboxApiImpl
import ru.raiffeisen.mobilecashbox.model.PaymentData
import ru.raiffeisen.mobilecashbox.model.ReceiptData
import ru.raiffeisen.mobilecashbox.model.RefundData
import java.math.BigDecimal
import java.time.LocalDateTime
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicInteger

class MobileCashboxInitTest {

    @Test
    fun `default gateway uses variant environment base URL`() = runBlocking {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { request ->
            captured = request
            respondJson(DEFAULT_JSON.encodeToString(app2AppMap("PAYMENT")))
        }) {
            defaultRequest {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer test-secret-key")
            }
        }
        val gateway = KtorBackendGateway(client)
        try {
            gateway.setCredentials(AuthorizationCredential.Email("cashier@example.com"))
            gateway.initializeMobileCashboxPayment(PaymentData(BigDecimal("123.45")))

            val request = requireNotNull(captured)
            assertEquals(
                "${MobileCashboxEnvironment.baseUrl}/mobilecashbox/v1/payments/init",
                request.url.toString(),
            )
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `vendor result is parsed locally and never sent to a result endpoint`() = runBlocking {
        val requestCount = AtomicInteger()
        val gateway = gateway {
            requestCount.incrementAndGet()
            respondJson(DEFAULT_JSON.encodeToString(app2AppMap("PAYMENT")))
        }
        val api = MobileCashboxApiImpl("unused", gateway)
        try {
            api.setUser(AuthorizationCredential.Email("cashier@example.com"))
            val event = async(start = CoroutineStart.UNDISPATCHED) { api.events.first() }
            api.performPayment(PaymentData(BigDecimal("123.45")))
            withTimeout(5_000) { api.openExternalApp.first() }

            api.onExternalAppResult(
                ExternalAppResult(
                    resultCode = -1,
                    payload = mapOf(
                        EXTERNAL_APP_RESULT_DATA_KEY to
                            "{\"result\":{\"code\":\"0\"},\"transaction\":{\"status\":\"SUCCESS\"}}"
                    ),
                )
            )

            assertEquals(
                MobileCashboxEvent.OperationResult(
                    operationType = MobileCashboxOperationType.PAYMENT,
                    result = MobileCashboxOperationResult.Success(successTransaction()),
                ),
                withTimeout(5_000) { event.await() },
            )
            delay(100)
            assertEquals(1, requestCount.get())
        } finally {
            api.close()
        }
    }

    @Test
    fun `refund vendor cancellation is returned as failure for requested refund`() = runBlocking {
        val gateway = gateway { respondJson(DEFAULT_JSON.encodeToString(app2AppMap("REVERSAL"))) }
        val api = MobileCashboxApiImpl("unused", gateway)
        try {
            api.setUser(AuthorizationCredential.Phone("79991234567"))
            val event = async(start = CoroutineStart.UNDISPATCHED) { api.events.first() }
            api.performRefund(refundData())
            withTimeout(5_000) { api.openExternalApp.first() }

            api.onExternalAppResult(
                ExternalAppResult(
                    resultCode = 0,
                    payload = mapOf(
                        EXTERNAL_APP_RESULT_DATA_KEY to
                            "{\"result\":{\"code\":\"1\",\"errorData\":{\"code\":\"027\",\"description\":\"cancelled\"}}}"
                    ),
                )
            )

            assertEquals(
                MobileCashboxEvent.OperationResult(
                    operationType = MobileCashboxOperationType.REFUND,
                    result = MobileCashboxOperationResult.Failure("027", "cancelled"),
                ),
                withTimeout(5_000) { event.await() },
            )
        } finally {
            api.close()
        }
    }

    @Test
    fun `initialization HTTP failure becomes typed operation outcome`() = runBlocking {
        val gateway = gateway {
            respond(
                content = "{\"title\":\"safe-error\"}",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }
        val api = MobileCashboxApiImpl("unused", gateway)
        try {
            api.setUser(AuthorizationCredential.Email("cashier@example.com"))
            val event = async(start = CoroutineStart.UNDISPATCHED) { api.events.first() }

            api.performPayment(PaymentData(BigDecimal("123.45")))

            assertEquals(
                MobileCashboxEvent.OperationResult(
                    operationType = MobileCashboxOperationType.PAYMENT,
                    result = MobileCashboxOperationResult.TechnicalFailure(
                        MobileCashboxTechnicalFailureReason.INITIALIZATION_FAILED
                    ),
                ),
                withTimeout(5_000) { event.await() },
            )
        } finally {
            api.close()
        }
    }

    @Test
    fun `platform failures produce typed technical outcomes`() = runBlocking {
        suspend fun assertScenario(
            action: (MobileCashboxApiImpl) -> Unit,
            expected: MobileCashboxTechnicalFailureReason,
        ) {
            val gateway = gateway { respondJson(DEFAULT_JSON.encodeToString(app2AppMap("PAYMENT"))) }
            val api = MobileCashboxApiImpl("unused", gateway)
            api.setUser(AuthorizationCredential.Email("cashier@example.com"))
            api.performPayment(PaymentData(BigDecimal("123.45")))
            withTimeout(5_000) { api.openExternalApp.first() }
            val event = async(start = CoroutineStart.UNDISPATCHED) { api.events.first() }
            action(api)

            val operationEvent = assertIs<MobileCashboxEvent.OperationResult>(
                withTimeout(5_000) { event.await() }
            )
            val failure = assertIs<MobileCashboxOperationResult.TechnicalFailure>(operationEvent.result)
            assertEquals(expected, failure.reason)
            api.close()
        }

        assertScenario(
            action = { it.onExternalAppResult(ExternalAppResult(resultCode = 0)) },
            expected = MobileCashboxTechnicalFailureReason.PLATFORM_CANCELED,
        )
        assertScenario(
            action = { it.onExternalAppLaunchFailed() },
            expected = MobileCashboxTechnicalFailureReason.APPLICATION_NOT_INSTALLED,
        )
    }

    @Test
    fun `public payment API initializes and opens configured external application`() = runBlocking {
        val expected = app2AppMap("PAYMENT")
        val gateway = gateway { respondJson(DEFAULT_JSON.encodeToString(expected)) }
        val api = MobileCashboxApiImpl("unused", gateway)
        try {
            api.setUser(AuthorizationCredential.Email("cashier@example.com"))
            api.performPayment(PaymentData(BigDecimal("123.45")))

            val request = withTimeout(5_000) { api.openExternalApp.first() }
            assertEquals(MobileCashboxEnvironment.externalAppPackage, request.application)
            assertEquals(MobileCashboxEnvironment.externalAppActivity, request.targetScreen)
            assertEquals(expected, request.data)
        } finally {
            api.close()
        }
    }

    @Test
    fun `public refund API initializes and opens configured external application`() = runBlocking {
        val expected = app2AppMap("REVERSAL")
        val gateway = gateway { respondJson(DEFAULT_JSON.encodeToString(expected)) }
        val api = MobileCashboxApiImpl("unused", gateway)
        try {
            api.setUser(AuthorizationCredential.Phone("79991234567"))
            api.performRefund(
                RefundData(
                    amount = BigDecimal("123.45"),
                    transactionId = "source-transaction-id",
                    transactionDate = LocalDateTime.of(2026, 9, 22, 12, 0),
                )
            )

            val request = withTimeout(5_000) { api.openExternalApp.first() }
            assertEquals(MobileCashboxEnvironment.externalAppPackage, request.application)
            assertEquals(MobileCashboxEnvironment.externalAppActivity, request.targetScreen)
            assertEquals(expected, request.data)
        } finally {
            api.close()
        }
    }

    @Test
    fun `public receipt API initializes and opens configured external application`() = runBlocking {
        val expected = app2AppMap("SEND_RECEIPT")
        val gateway = gateway { respondJson(DEFAULT_JSON.encodeToString(expected)) }
        val api = MobileCashboxApiImpl("unused", gateway)
        try {
            api.setUser(AuthorizationCredential.Email("cashier@example.com"))
            api.sendReceipt(receiptData())

            val request = withTimeout(5_000) { api.openExternalApp.first() }
            assertEquals(MobileCashboxEnvironment.externalAppPackage, request.application)
            assertEquals(MobileCashboxEnvironment.externalAppActivity, request.targetScreen)
            assertEquals(expected, request.data)
        } finally {
            api.close()
        }
    }

    @Test
    fun `public logs API initializes and opens configured external application`() = runBlocking {
        val expected = app2AppMap("SEND_LOGS")
        val gateway = gateway { respondJson(DEFAULT_JSON.encodeToString(expected)) }
        val api = MobileCashboxApiImpl("unused", gateway)
        try {
            api.setUser(AuthorizationCredential.Phone("79991234567"))
            api.sendLogs()

            val request = withTimeout(5_000) { api.openExternalApp.first() }
            assertEquals(MobileCashboxEnvironment.externalAppPackage, request.application)
            assertEquals(MobileCashboxEnvironment.externalAppActivity, request.targetScreen)
            assertEquals(expected, request.data)
        } finally {
            api.close()
        }
    }

    @Test
    fun `receipt and logs results keep requested public operation type`() = runBlocking {
        val scenarios = listOf<Pair<MobileCashboxOperationType, (MobileCashboxApiImpl) -> Unit>>(
            MobileCashboxOperationType.RECEIPT to { it.sendReceipt(receiptData()) },
            MobileCashboxOperationType.LOGS to { it.sendLogs() },
        )

        for ((expectedType, startOperation) in scenarios) {
            val gateway = gateway { respondJson(DEFAULT_JSON.encodeToString(app2AppMap(expectedType.name))) }
            val api = MobileCashboxApiImpl("unused", gateway)
            try {
                api.setUser(AuthorizationCredential.Email("cashier@example.com"))
                val event = async(start = CoroutineStart.UNDISPATCHED) { api.events.first() }
                startOperation(api)
                withTimeout(5_000) { api.openExternalApp.first() }

                api.onExternalAppResult(
                    ExternalAppResult(
                        resultCode = -1,
                        payload = mapOf(
                            EXTERNAL_APP_RESULT_DATA_KEY to "{\"result\":{\"code\":\"0\"}}",
                        ),
                    )
                )

                assertEquals(
                    MobileCashboxEvent.OperationResult(
                        operationType = expectedType,
                        result = MobileCashboxOperationResult.Success(null),
                    ),
                    withTimeout(5_000) { event.await() },
                )
            } finally {
                api.close()
            }
        }
    }

    @Test
    fun `payment init sends gateway contract and returns app2app map unchanged`() = runBlocking {
        var captured: HttpRequestData? = null
        val expected = app2AppMap("PAYMENT")
        val gateway = gateway { request ->
            captured = request
            respondJson(DEFAULT_JSON.encodeToString(expected))
        }
        try {
            gateway.setCredentials(
                AuthorizationCredential.EmailAndPhone(
                    email = "cashier@example.com",
                    phone = "79991234567",
                )
            )
            val actual = gateway.initializeMobileCashboxPayment(
                PaymentData(
                    amount = BigDecimal("12345678901234567890.12"),
                )
            )

            assertEquals(expected, actual)
            val request = requireNotNull(captured)
            assertEquals("/mobilecashbox/v1/payments/init", request.url.encodedPath)
            assertCommonHeaders(request)

            val body = DEFAULT_JSON.parseToJsonElement(request.bodyAsText()).jsonObject
            assertEquals(JsonPrimitive(BigDecimal("12345678901234567890.12")), body["amount"])
            assertEquals(JsonPrimitive("cashier@example.com"), body["cashier"]?.jsonObject?.get("email"))
            assertEquals(JsonPrimitive("79991234567"), body["cashier"]?.jsonObject?.get("phone"))
            assertFalse("publicId" in body)
            assertFalse("currency" in body)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `init normalizes blank credential field to null`() = runBlocking {
        var body = ""
        val gateway = gateway { request ->
            body = request.bodyAsText()
            respondJson(DEFAULT_JSON.encodeToString(app2AppMap("PAYMENT")))
        }
        try {
            gateway.setCredentials(AuthorizationCredential.EmailAndPhone("", "79991234567"))
            gateway.initializeMobileCashboxPayment(PaymentData(BigDecimal("123.45")))

            val cashier = DEFAULT_JSON.parseToJsonElement(body).jsonObject["cashier"]!!.jsonObject
            assertFalse("email" in cashier)
            assertEquals(JsonPrimitive("79991234567"), cashier["phone"])
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `init rejects credentials when all contact fields are blank`() = runBlocking {
        val requestCount = AtomicInteger()
        val gateway = gateway {
            requestCount.incrementAndGet()
            respondJson(DEFAULT_JSON.encodeToString(app2AppMap("PAYMENT")))
        }
        try {
            gateway.setCredentials(AuthorizationCredential.EmailAndPhone(" ", "\t"))

            assertFailsWith<IllegalArgumentException> {
                gateway.initializeMobileCashboxPayment(PaymentData(BigDecimal("123.45")))
            }
            assertEquals(0, requestCount.get())
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `refund init sends local date and preserves refund operation type`() = runBlocking {
        for (operationType in listOf("REFUND", "REVERSAL")) {
            var captured: HttpRequestData? = null
            val expected = app2AppMap(operationType)
            val gateway = gateway { request ->
                captured = request
                respondJson(DEFAULT_JSON.encodeToString(expected))
            }
            try {
                gateway.setCredentials(AuthorizationCredential.Phone("79991234567"))
                val actual = gateway.initializeMobileCashboxRefund(
                    RefundData(
                        amount = BigDecimal("123.45"),
                        transactionId = "source-transaction-id",
                        transactionDate = LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                    )
                )

                assertEquals(expected, actual)
                val request = requireNotNull(captured)
                assertEquals("/mobilecashbox/v1/refunds/init", request.url.encodedPath)
                assertCommonHeaders(request)

                val body = DEFAULT_JSON.parseToJsonElement(request.bodyAsText()).jsonObject
                assertEquals(JsonPrimitive(BigDecimal("123.45")), body["amount"])
                assertEquals(
                    JsonPrimitive("2026-09-22T12:00:00"),
                    body["transaction"]?.jsonObject?.get("date"),
                )
                assertEquals(
                    JsonPrimitive("source-transaction-id"),
                    body["transaction"]?.jsonObject?.get("id"),
                )
                assertFalse("publicId" in body)
                assertFalse("currency" in body)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun `receipt init sends recipient transaction and returns nested app2app JSON unchanged`() = runBlocking {
        var captured: HttpRequestData? = null
        val expected = mapOf(
            "ru.m4bank.ExternalApplication.InputDataKey" to
                "{\"credentials\":{\"authorizationToken\":\"token\\\\value\"}," +
                "\"operationData\":{\"email\":\"customer@example.com\"}}",
            "ru.m4bank.ExternalApplication.OperationTypeKey" to "SEND_RECEIPT",
        )
        val gateway = gateway { request ->
            captured = request
            respondJson(DEFAULT_JSON.encodeToString(expected))
        }
        try {
            gateway.setCredentials(AuthorizationCredential.Email("cashier@example.com"))

            val actual = gateway.initializeMobileCashboxReceipt(receiptData())

            assertEquals(expected, actual)
            val request = requireNotNull(captured)
            assertEquals("/mobilecashbox/v1/receipts/init", request.url.encodedPath)
            assertCommonHeaders(request)
            val body = DEFAULT_JSON.parseToJsonElement(request.bodyAsText()).jsonObject
            assertEquals(JsonPrimitive("customer@example.com"), body["receiptEmail"])
            assertEquals(JsonPrimitive("cashier@example.com"), body["cashier"]?.jsonObject?.get("email"))
            assertEquals(
                JsonPrimitive("source-transaction-id"),
                body["transaction"]?.jsonObject?.get("id"),
            )
            assertEquals(
                JsonPrimitive("2026-09-29T14:30:00"),
                body["transaction"]?.jsonObject?.get("date"),
            )
            assertFalse("publicId" in body)
            assertFalse("merchantId" in body)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `logs init sends only cashier and returns send logs operation type`() = runBlocking {
        var captured: HttpRequestData? = null
        val expected = app2AppMap("SEND_LOGS")
        val gateway = gateway { request ->
            captured = request
            respondJson(DEFAULT_JSON.encodeToString(expected))
        }
        try {
            gateway.setCredentials(AuthorizationCredential.Phone("79991234567"))

            val actual = gateway.initializeMobileCashboxLogs()

            assertEquals(expected, actual)
            val request = requireNotNull(captured)
            assertEquals("/mobilecashbox/v1/logs/init", request.url.encodedPath)
            assertCommonHeaders(request)
            val body = DEFAULT_JSON.parseToJsonElement(request.bodyAsText()).jsonObject
            assertEquals(JsonPrimitive("79991234567"), body["cashier"]?.jsonObject?.get("phone"))
            assertEquals(setOf("cashier"), body.keys)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `init HTTP errors are never converted to an empty map`() = runBlocking {
        for (status in listOf(
            HttpStatusCode.BadRequest,
            HttpStatusCode.Unauthorized,
            HttpStatusCode.BadGateway,
            HttpStatusCode.InternalServerError,
        )) {
            val gateway = gateway {
                respond(
                    content = "{\"title\":\"safe-error\"}",
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
                )
            }
            try {
                gateway.setCredentials(AuthorizationCredential.Email("cashier@example.com"))
                val exception = assertFailsWith<ResponseException> {
                    gateway.initializeMobileCashboxPayment(
                        PaymentData(
                            amount = BigDecimal("123.45"),
                        )
                    )
                }
                assertEquals(status, exception.response.status)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun `init calls do not write secrets personal data or app2app payload to logs`() = runBlocking {
        val expected = app2AppMap("PAYMENT")
        val gateway = gateway { respondJson(DEFAULT_JSON.encodeToString(expected)) }
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        try {
            gateway.setCredentials(
                AuthorizationCredential.EmailAndPhone(
                    email = "private-cashier@example.com",
                    phone = "79991234567",
                )
            )
            PrintStream(output, true, Charsets.UTF_8).use { sink ->
                System.setOut(sink)
                System.setErr(sink)
                gateway.initializeMobileCashboxPayment(
                    PaymentData(
                        amount = BigDecimal("123.45"),
                    )
                )
            }
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            gateway.close()
        }

        val logs = output.toString(Charsets.UTF_8)
        assertFalse("test-secret-key" in logs)
        assertFalse("private-cashier@example.com" in logs)
        assertFalse("79991234567" in logs)
        assertFalse("authorizationToken" in logs)
        assertFalse("InputDataKey" in logs)
    }

    private fun gateway(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): KtorBackendGateway {
        val client = HttpClient(MockEngine(handler)) {
            defaultRequest {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer test-secret-key")
            }
        }
        return KtorBackendGateway(client, "https://gateway.example")
    }

    private fun assertCommonHeaders(request: HttpRequestData) {
        assertEquals("Bearer test-secret-key", request.headers[HttpHeaders.Authorization])
        assertEquals(ContentType.Application.Json, request.body.contentType)
        assertNull(request.headers["publicId"])
        assertNull(request.headers["X-Public-Id"])
    }

    private fun MockRequestHandleScope.respondJson(body: String): io.ktor.client.request.HttpResponseData =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun HttpRequestData.bodyAsText(): String =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private fun app2AppMap(operationType: String): Map<String, String> = mapOf(
        "ru.m4bank.ExternalApplication.InputDataKey" to
            "{\"credentials\":{\"authorizationToken\":\"token\"},\"operationData\":{\"amount\":123.45}}",
        "ru.m4bank.ExternalApplication.OperationTypeKey" to operationType,
    )

    private fun refundData(): RefundData = RefundData(
        amount = BigDecimal("123.45"),
        transactionId = "source-transaction-id",
        transactionDate = LocalDateTime.of(2026, 9, 22, 12, 0),
    )

    private fun receiptData(): ReceiptData = ReceiptData(
        receiptEmail = "customer@example.com",
        transactionId = "source-transaction-id",
        transactionDate = LocalDateTime.of(2026, 9, 29, 14, 30),
    )

    private fun successTransaction(): MobileCashboxTransaction = MobileCashboxTransaction(
        terminalId = null,
        operationDay = null,
        transactionNumber = null,
        reversible = null,
        externalTerminalId = null,
        dateTime = null,
        operationExternalId = null,
        status = "SUCCESS",
        statusDescription = null,
        instrument = null,
        type = null,
        paymentSystemType = null,
        amount = null,
        currencyCode = null,
        companyName = null,
        merchant = null,
    )
}
