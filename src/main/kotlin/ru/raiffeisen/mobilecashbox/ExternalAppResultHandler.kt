package ru.raiffeisen.mobilecashbox

import kotlinx.coroutines.flow.Flow

internal interface ExternalAppResultHandler {
    val openExternalApp: Flow<ExternalAppRequest>

    fun onExternalAppResult(result: ExternalAppResult)
    fun onExternalAppLaunchFailed()
}
