package ru.raiffeisen.mobilecashbox.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Shared JSON instance used for every (de)serialization in the library.
 *
 * Bodies are encoded/decoded explicitly with [Json] rather than via Ktor's ContentNegotiation
 * plugin — that plugin resolves serializers through runtime reflection on class/method names,
 * which breaks once the internal DTO classes are obfuscated. Explicit (de)serialization has no
 * such dependency.
 */
internal val DEFAULT_JSON: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Builds the [HttpClient] used by default across the library.
 *
 * Android uses OkHttp so certificate validation follows the platform's hostname-aware Network
 * Security Config path. Tests construct `HttpClient(MockEngine { ... })` and inject it via
 * [ru.raiffeisen.mobilecashbox.MobileCashboxApi]'s gateway.
 */
internal fun httpClient(token: String): HttpClient =
    HttpClient(OkHttp) {
        configureMobileCashboxClient(token)
    }

internal fun HttpClientConfig<*>.configureMobileCashboxClient(token: String) {
    install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.ALL
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Bearer $token")
    }
}
