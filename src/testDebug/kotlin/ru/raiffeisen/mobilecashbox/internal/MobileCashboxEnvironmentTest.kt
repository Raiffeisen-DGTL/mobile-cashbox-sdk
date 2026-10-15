package ru.raiffeisen.mobilecashbox.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_ACTIVITY_DEBUG
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_PACKAGE_DEBUG

class MobileCashboxEnvironmentTest {
    @Test
    fun `debug variant uses test environment`() {
        assertEquals(EXTERNAL_APP_PACKAGE_DEBUG, MobileCashboxEnvironment.externalAppPackage)
        assertEquals(EXTERNAL_APP_ACTIVITY_DEBUG, MobileCashboxEnvironment.externalAppActivity)
        assertEquals(
            "https://mcp-test.raiffeisen.ru/api/mobile-cashbox-gateway",
            MobileCashboxEnvironment.baseUrl,
        )
    }
}
