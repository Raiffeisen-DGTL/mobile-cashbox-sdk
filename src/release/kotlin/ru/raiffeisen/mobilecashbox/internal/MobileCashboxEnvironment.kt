package ru.raiffeisen.mobilecashbox.internal

import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_ACTIVITY
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_PACKAGE

internal object MobileCashboxEnvironment {
    const val externalAppPackage: String = EXTERNAL_APP_PACKAGE
    const val externalAppActivity: String = EXTERNAL_APP_ACTIVITY
    const val baseUrl: String =
        "https://pay.raif.ru/api/mobile-cashbox-gateway"
}
