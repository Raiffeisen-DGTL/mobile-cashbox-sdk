package ru.raiffeisen.mobilecashbox.internal

import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_ACTIVITY_DEBUG
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_PACKAGE_DEBUG

internal object MobileCashboxEnvironment {
    const val externalAppPackage: String = EXTERNAL_APP_PACKAGE_DEBUG
    const val externalAppActivity: String = EXTERNAL_APP_ACTIVITY_DEBUG
    const val baseUrl: String =
        "https://mcp-test.raiffeisen.ru/api/mobile-cashbox-gateway"
}
