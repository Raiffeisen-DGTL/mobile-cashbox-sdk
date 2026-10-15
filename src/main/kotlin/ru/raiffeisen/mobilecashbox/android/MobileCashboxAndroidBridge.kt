package ru.raiffeisen.mobilecashbox.android

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import ru.raiffeisen.mobilecashbox.ExternalAppRequest
import ru.raiffeisen.mobilecashbox.MobileCashboxApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.raiffeisen.mobilecashbox.ExternalAppResultHandler

/**
 * Bridges [MobileCashboxApi]'s internal launch commands to Android's Activity Result API: this
 * launches the target app via an explicit-package [Intent] and, once it calls `setResult()` and
 * finishes, feeds the result back into the library's internal result handler.
 *
 * Register once per host, before it reaches `STARTED` — the same constraint
 * `registerForActivityResult` imposes on every launcher, e.g. as a property initializer of an
 * Activity/Fragment:
 * ```
 * private val lib = MockupLibApi()
 * private val bridge = MockupLibAndroidBridge.register(this, lib)
 * ```
 * Call [dispose] when the host is torn down (e.g. `onDestroy`).
 *
 * For Jetpack Compose UIs, where there is no equivalent "before STARTED" hook to call this from,
 * use [MobileCashboxExternalAppEffect] instead — it wraps the same underlying mechanism using
 * Compose's own `rememberLauncherForActivityResult`, which handles registration/disposal as part
 * of composition instead of the Activity lifecycle directly.
 */
public class MobileCashboxAndroidBridge private constructor(
    private val launcher: ActivityResultLauncher<ExternalAppRequest>,
    private val lib: MobileCashboxApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun observe() {
        val externalAppHandler = lib as ExternalAppResultHandler
        scope.launch {
            externalAppHandler.openExternalApp.collect { state ->
                try {
                    launcher.launch(state)
                } catch (e: ActivityNotFoundException) {
                    externalAppHandler.onExternalAppLaunchFailed()
                }
            }
        }
    }

    public fun dispose() {
        scope.cancel()
    }

    public companion object {
        public fun register(caller: ActivityResultCaller, lib: MobileCashboxApi): MobileCashboxAndroidBridge {
            val launcher = caller.registerForActivityResult(ExternalAppContract) { result ->
                (lib as ExternalAppResultHandler).onExternalAppResult(result)
            }
            return MobileCashboxAndroidBridge(launcher, lib).apply { observe() }
        }
    }
}
