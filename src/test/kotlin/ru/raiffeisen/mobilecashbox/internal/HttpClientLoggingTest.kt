package ru.raiffeisen.mobilecashbox.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains

class HttpClientLoggingTest {

    @Test
    fun `debug HTTP logs contain full exchange including bearer header`() = runBlocking {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = "{\"responseData\":\"full-response\"}",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        ) {
            configureMobileCashboxClient("secret-key-for-test")
        }
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        try {
            PrintStream(output, true, Charsets.UTF_8).use { sink ->
                System.setOut(sink)
                client.post("https://gateway.example/debug") {
                    setBody("{\"requestData\":\"full-request\"}")
                }.bodyAsText()
            }
        } finally {
            System.setOut(originalOut)
            client.close()
        }

        val logs = output.toString(Charsets.UTF_8)
        assertContains(logs, "POST")
        assertContains(logs, "https://gateway.example/debug")
        assertContains(logs, "full-request")
        assertContains(logs, "full-response")
        assertContains(logs, "secret-key-for-test")
    }
}
