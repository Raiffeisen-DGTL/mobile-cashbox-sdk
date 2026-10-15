package ru.raiffeisen.mobilecashbox.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import ru.raiffeisen.mobilecashbox.internal.gateway.dto.OperatorCredentialsRequest

/**
 * A [HttpClient] wired to a fake in-memory router instead of a live server. Used only from
 * tests — library consumers use the real OkHttp-backed client from [httpClient].
 */
internal fun mockHttpClient(token: String): HttpClient {
    val engine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/mobilecashbox/v1/check" -> handleCheck(request)
            else -> respond(
                content = "",
                status = HttpStatusCode.NotFound,
            )
        }
    }

    return HttpClient(engine) {
        defaultRequest {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleCheck(request: HttpRequestData) =
    if (DEFAULT_JSON.decodeFromString<OperatorCredentialsRequest>(request.bodyAsText()).let {
            it.email.isNullOrBlank() && it.phone.isNullOrBlank()
        }
    ) {
        respond(
            content = "{\"title\":\"INVALID_CREDENTIAL\"}",
            status = HttpStatusCode.BadRequest,
            headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
        )
    } else {
        respond(content = "", status = HttpStatusCode.NoContent)
    }

private fun HttpRequestData.bodyAsText(): String =
    (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
