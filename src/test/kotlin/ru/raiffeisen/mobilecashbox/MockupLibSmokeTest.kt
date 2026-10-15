package ru.raiffeisen.mobilecashbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import ru.raiffeisen.mobilecashbox.internal.MobileCashboxApiImpl
import ru.raiffeisen.mobilecashbox.internal.gateway.KtorBackendGateway
import ru.raiffeisen.mobilecashbox.internal.mockHttpClient

// These are real (wall-clock) blocking tests, not virtual-time `runTest` ones: MobileCashboxApiImpl
// does its work on Dispatchers.Default, a genuinely separate thread pool that a virtual-time
// test dispatcher cannot fast-forward. Blocking here is fine — it's JVM-only test harness code,
// not library code, so it doesn't conflict with the "no runBlocking in the library" rule.
class MockupLibSmokeTest {

    @Test
    fun emailAuthReachesAuthenticatedState() = runBlocking {
        val lib = mobileCashboxApiWithMock()
        try {
            val states = mutableListOf<ConnectionState>()
            val collector = launchStateCollector(lib, states)

            lib.setUser(AuthorizationCredential.Email("user@example.com"))
            lib.auth()
            waitUntil { states.lastOrNull() == ConnectionState.Authenticated }

            assertEquals(ConnectionState.Authenticated, states.last())
            collector.cancel()
        } finally {
            lib.close()
        }
    }

    @Test
    fun phoneAuthReachesAuthenticatedState() = runBlocking {
        val lib = mobileCashboxApiWithMock()
        try {
            val states = mutableListOf<ConnectionState>()
            val collector = launchStateCollector(lib, states)

            lib.setUser(AuthorizationCredential.Phone("+1234567890"))
            lib.auth()
            waitUntil { states.lastOrNull() == ConnectionState.Authenticated }

            assertEquals(ConnectionState.Authenticated, states.last())
            collector.cancel()
        } finally {
            lib.close()
        }
    }

    @Test
    fun blankCredentialIsRejected() = runBlocking {
        val lib = mobileCashboxApiWithMock()
        try {
            val states = mutableListOf<ConnectionState>()
            val collector = launchStateCollector(lib, states)

            lib.setUser(AuthorizationCredential.Phone(""))
            lib.auth()
            waitUntil { states.lastOrNull() is ConnectionState.AuthenticationError }

            assertEquals(
                ConnectionState.AuthenticationError(ConnectionFailureReason.InvalidCredential),
                states.last(),
            )
            collector.cancel()
        } finally {
            lib.close()
        }
    }
}

// Tests use an injected MockEngine-backed client so no real network/CIO engine is exercised.
private fun mobileCashboxApiWithMock(token: String = "valid-token"): MobileCashboxApi =
    MobileCashboxApiImpl(
        token,
        KtorBackendGateway(mockHttpClient(token), "https://mock.local"),
    )

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
private fun launchStateCollector(lib: MobileCashboxApi, into: MutableList<ConnectionState>) =
    GlobalScope.launch(Dispatchers.Unconfined) {
        lib.connectionState.collect { into += it }
    }

private suspend fun waitUntil(maxAttempts: Int = 500, condition: () -> Boolean) {
    var attempts = 0
    while (!condition() && attempts < maxAttempts) {
        delay(10)
        attempts++
    }
}
