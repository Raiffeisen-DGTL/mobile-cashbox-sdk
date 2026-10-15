package ru.raiffeisen.mobilecashbox.android

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ru.raiffeisen.mobilecashbox.MobileCashboxApi
import ru.raiffeisen.mobilecashbox.ExternalAppResultHandler

/**
 * The Compose equivalent of [MobileCashboxAndroidBridge] — same underlying mechanism
 * (`ActivityResultContract`), but registered through Compose's own
 * `rememberLauncherForActivityResult` instead of an `ActivityResultCaller` reference, so it
 * fits a Compose-only UI with no separate `ActivityResultCaller` to hand it.
 *
 * That said, Compose does not remove the platform constraint that a launcher must be registered
 * before its host reaches `STARTED` — `rememberLauncherForActivityResult` still registers
 * synchronously on the first composition of whatever call site invokes it. It just gives you a
 * clean way to comply: **call this unconditionally, at the root of your composable tree** —
 * directly inside `setContent { }`, not behind an `if` or nested in a screen that might first
 * enter composition after the Activity is already resumed:
 * ```
 * setContent {
 *     MockupLibExternalAppEffect(lib) // always composed first, regardless of navigation state
 *     MyAppNavHost(lib)               // free to be as conditional/dynamic as you like
 * }
 * ```
 * Disposal is automatic — both the launcher and the state collection stop when this leaves
 * composition, so there is no `dispose()` to call, unlike [MobileCashboxAndroidBridge].
 */
@Composable
public fun MobileCashboxExternalAppEffect(lib: MobileCashboxApi) {
    val externalAppHandler = lib as ExternalAppResultHandler
    val launcher = rememberLauncherForActivityResult(ExternalAppContract) { result ->
        externalAppHandler.onExternalAppResult(result)
    }

    LaunchedEffect(lib) {
        externalAppHandler.openExternalApp.collect { state ->
            try {
                launcher.launch(state)
            } catch (e: ActivityNotFoundException) {
                externalAppHandler.onExternalAppLaunchFailed()
            }
        }
    }
}
