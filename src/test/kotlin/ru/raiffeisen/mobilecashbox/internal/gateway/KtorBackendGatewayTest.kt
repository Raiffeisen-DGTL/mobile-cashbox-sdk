package ru.raiffeisen.mobilecashbox.internal.gateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.raiffeisen.mobilecashbox.AuthorizationCredential
import ru.raiffeisen.mobilecashbox.ConnectionFailureReason
import ru.raiffeisen.mobilecashbox.ConnectionState
import ru.raiffeisen.mobilecashbox.internal.DEFAULT_JSON
import ru.raiffeisen.mobilecashbox.internal.MobileCashboxApiImpl
import ru.raiffeisen.mobilecashbox.internal.gateway.outcome.GatewayResult
import java.util.concurrent.atomic.AtomicInteger

class KtorBackendGatewayTest {

    @Test
    fun `email-only request preserves nullable phone and uses standard bearer header`() = runBlocking {
        var captured: HttpRequestData? = null
        val gateway = gateway { request ->
            captured = request
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        try {
            gateway.setCredentials(AuthorizationCredential.Email("cashier@example.com"))

            assertIs<GatewayResult.Success<*>>(gateway.checkUser())
            val request = requireNotNull(captured)
            assertEquals("/mobilecashbox/v1/check", request.url.encodedPath)
            assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
            assertNull(request.headers["publicId"])
            assertNull(request.headers["X-Public-Id"])
            assertEquals(ContentType.Application.Json, request.body.contentType)

            val body = DEFAULT_JSON.parseToJsonElement(request.bodyAsText()).jsonObject
            assertEquals(JsonPrimitive("cashier@example.com"), body["email"])
            assertEquals(JsonNull, body["phone"])
            assertFalse("publicId" in body)
            assertFalse("merchantId" in body)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `phone-only request does not substitute fields`() = runBlocking {
        var body = ""
        val gateway = gateway { request ->
            body = request.bodyAsText()
            respond(content = "", status = HttpStatusCode.OK)
        }
        try {
            gateway.setCredentials(AuthorizationCredential.Phone("79991234567"))
            assertIs<GatewayResult.Success<*>>(gateway.checkUser())

            val json = DEFAULT_JSON.parseToJsonElement(body).jsonObject
            assertEquals(JsonNull, json["email"])
            assertEquals(JsonPrimitive("79991234567"), json["phone"])
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `email and phone request sends both identifiers`() = runBlocking {
        var body = ""
        val gateway = gateway { request ->
            body = request.bodyAsText()
            respond(content = "", status = HttpStatusCode.Accepted)
        }
        try {
            gateway.setCredentials(AuthorizationCredential.EmailAndPhone("cashier@example.com", "79991234567"))
            assertIs<GatewayResult.Success<*>>(gateway.checkUser())

            val json = DEFAULT_JSON.parseToJsonElement(body).jsonObject
            assertEquals(JsonPrimitive("cashier@example.com"), json["email"])
            assertEquals(JsonPrimitive("79991234567"), json["phone"])
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `blank credential field is normalized to null`() = runBlocking {
        var body = ""
        val gateway = gateway { request ->
            body = request.bodyAsText()
            respond(content = "", status = HttpStatusCode.OK)
        }
        try {
            gateway.setCredentials(AuthorizationCredential.EmailAndPhone("   ", "79991234567"))
            assertIs<GatewayResult.Success<*>>(gateway.checkUser())

            val json = DEFAULT_JSON.parseToJsonElement(body).jsonObject
            assertEquals(JsonNull, json["email"])
            assertEquals(JsonPrimitive("79991234567"), json["phone"])
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `missing identifiers fail validation without an HTTP request`() = runBlocking {
        val requestCount = AtomicInteger()
        val gateway = gateway {
            requestCount.incrementAndGet()
            respond(content = "", status = HttpStatusCode.OK)
        }
        try {
            val noCredentials = gateway.checkUser()
            gateway.setCredentials(AuthorizationCredential.EmailAndPhone("", ""))
            val blankCredentials = gateway.checkUser()

            assertEquals(GatewayResult.Failure(ConnectionFailureReason.InvalidCredential), noCredentials)
            assertEquals(GatewayResult.Failure(ConnectionFailureReason.InvalidCredential), blankCredentials)
            assertEquals(0, requestCount.get())
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `problem codes distinguish business errors sharing HTTP statuses`() = runBlocking {
        val cases = listOf(
            Triple(HttpStatusCode.Forbidden, "CASHIER_NOT_REGISTERED", ConnectionFailureReason.CashierNotRegistered),
            Triple(HttpStatusCode.Forbidden, "NO_PERMISSION_FOR_MERCHANT", ConnectionFailureReason.NoPermissionForMerchant),
            Triple(HttpStatusCode.BadRequest, "CASHIER_IS_NOT_ACTIVATED", ConnectionFailureReason.CashierNotActivated),
        )

        for ((status, code, expected) in cases) {
            val gateway = gateway {
                respondProblem(status, "{\"title\":\"$code\"}")
            }
            try {
                gateway.setCredentials(AuthorizationCredential.Phone("79991234567"))
                assertEquals(GatewayResult.Failure(expected), gateway.checkUser())
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun `problem code field is accepted and business failures are not retried`() = runBlocking {
        val requestCount = AtomicInteger()
        val gateway = gateway {
            requestCount.incrementAndGet()
            respondProblem(HttpStatusCode.Forbidden, "{\"code\":\"NO_PERMISSION_FOR_MERCHANT\"}")
        }
        try {
            gateway.setCredentials(AuthorizationCredential.Email("cashier@example.com"))

            assertEquals(
                GatewayResult.Failure(ConnectionFailureReason.NoPermissionForMerchant),
                gateway.checkUser(),
            )
            assertEquals(1, requestCount.get())
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `generic HTTP failures retain useful categories without exposing response bodies`() = runBlocking {
        val cases = listOf(
            Triple(HttpStatusCode.Unauthorized, "", ConnectionFailureReason.InvalidToken),
            Triple(HttpStatusCode.BadRequest, "{\"title\":\"OTHER_BAD_REQUEST\"}", ConnectionFailureReason.BadRequest("OTHER_BAD_REQUEST")),
            Triple(HttpStatusCode.Forbidden, "{\"title\":\"OTHER_FORBIDDEN\"}", ConnectionFailureReason.Forbidden("OTHER_FORBIDDEN")),
            Triple(HttpStatusCode.ServiceUnavailable, "sensitive response body", ConnectionFailureReason.ServerError(503)),
        )

        for ((status, body, expected) in cases) {
            val gateway = gateway { respondProblem(status, body) }
            try {
                gateway.setCredentials(AuthorizationCredential.Phone("79991234567"))
                assertEquals(GatewayResult.Failure(expected), gateway.checkUser())
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun `request timeout has a distinct failure reason`() = runBlocking {
        val gateway = gateway { request -> throw HttpRequestTimeoutException(request) }
        try {
            gateway.setCredentials(AuthorizationCredential.Phone("79991234567"))
            assertEquals(
                GatewayResult.Failure(ConnectionFailureReason.Timeout),
                gateway.checkUser(),
            )
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `second auth call is ignored while check is active`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val requestCount = AtomicInteger()
        val gateway = gateway {
            requestCount.incrementAndGet()
            started.complete(Unit)
            release.await()
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val api = MobileCashboxApiImpl("unused", gateway)
        try {
            api.setUser(AuthorizationCredential.Phone("79991234567"))
            api.auth()
            started.await()

            api.auth()
            delay(100)
            assertEquals(1, requestCount.get())
            assertEquals(ConnectionState.Authenticating, api.connectionState.value)

            release.complete(Unit)
            waitUntil { api.connectionState.value == ConnectionState.Authenticated }
            assertEquals(1, requestCount.get())
        } finally {
            api.close()
        }
    }

    private fun gateway(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): KtorBackendGateway {
        val client = HttpClient(MockEngine(handler)) {
            defaultRequest {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer access-token")
            }
        }
        return KtorBackendGateway(client, "https://gateway.example")
    }

    private fun MockRequestHandleScope.respondProblem(
        status: HttpStatusCode,
        body: String,
    ): io.ktor.client.request.HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
    )

    private fun HttpRequestData.bodyAsText(): String =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            if (condition()) return
            delay(10)
        }
        assertTrue(condition(), "Condition was not met in time")
    }
}
