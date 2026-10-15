package ru.raiffeisen.mobilecashbox.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import ru.raiffeisen.mobilecashbox.EXTERNAL_APP_RESULT_DATA_KEY
import ru.raiffeisen.mobilecashbox.ExternalAppRequest
import ru.raiffeisen.mobilecashbox.ExternalAppResult

/**
 * Shared by [MobileCashboxAndroidBridge] (Views) and the Compose entry point — one place that knows
 * how an [ExternalAppRequest] becomes an [Intent] and how its result gets decoded back.
 *
 * A singleton, not a class: it is fully stateless, and giving it stable identity matters for
 * [ru.raiffeisen.mobilecashbox.android.MobileCashboxExternalAppEffect] — Compose's
 * `rememberLauncherForActivityResult` keys its internal `remember` off this instance, so a fresh
 * instance on every recomposition would otherwise look like a changed contract each time.
 */
internal object ExternalAppContract : ActivityResultContract<ExternalAppRequest, ExternalAppResult>() {
    override fun createIntent(context: Context, input: ExternalAppRequest): Intent {
        val intent = Intent().apply {
            component = ComponentName(
                input.application,
                input.targetScreen
            )

            input.data?.forEach { (key, value) ->
                putExtra(key, value)
            }
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ExternalAppResult {
        val payload = intent
            ?.getStringExtra(EXTERNAL_APP_RESULT_DATA_KEY)
            ?.let { mapOf(EXTERNAL_APP_RESULT_DATA_KEY to it) }
            .orEmpty()
        return ExternalAppResult(resultCode = resultCode, payload = payload)
    }
}
