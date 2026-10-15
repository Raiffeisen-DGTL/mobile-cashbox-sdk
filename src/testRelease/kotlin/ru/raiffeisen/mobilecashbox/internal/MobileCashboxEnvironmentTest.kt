package ru.raiffeisen.mobilecashbox.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_ACTIVITY
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_PACKAGE

class MobileCashboxEnvironmentTest {
    @Test
    fun `release variant uses production environment`() {
        assertEquals(EXTERNAL_APP_PACKAGE, MobileCashboxEnvironment.externalAppPackage)
        assertEquals(EXTERNAL_APP_ACTIVITY, MobileCashboxEnvironment.externalAppActivity)
        assertEquals(
            "https://pay.raif.ru/api/mobile-cashbox-gateway",
            MobileCashboxEnvironment.baseUrl,
        )
    }
}
