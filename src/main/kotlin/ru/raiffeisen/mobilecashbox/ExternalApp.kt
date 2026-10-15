package ru.raiffeisen.mobilecashbox

internal const val EXTERNAL_APP_PACKAGE_DEBUG: String = "com.example.myapplication"
internal const val EXTERNAL_APP_ACTIVITY_DEBUG: String =
    "com.example.myapplication.MainActivity"
internal const val EXTERNAL_APP_PACKAGE: String = "ru.m4bank.softpos.raiffeisen"
internal const val EXTERNAL_APP_ACTIVITY: String =
    "ru.m4bank.feature.externalapplication.ExternalApplicationActivity"
internal const val EXTERNAL_APP_RESULT_DATA_KEY: String =
    "ru.m4bank.ExternalApplication.ResultDataKey"

internal data class ExternalAppRequest(
    val application: String,
    val targetScreen: String,
    val data: Map<String, String>?,
)

/** What the external app handed back, decoded into platform-agnostic data. */
internal data class ExternalAppResult(
    val resultCode: Int,
    val payload: Map<String, String> = emptyMap(),
)
