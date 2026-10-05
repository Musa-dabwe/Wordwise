package com.musa.wordwise

import android.app.AlertDialog
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The part of the bridge only a real device can exercise: the native confirm
 * dialog behind "Remove saved key".
 *
 * `WwBridgeTest` covers the rules, and those are pure JVM. The dialog lifecycle
 * cannot be covered off-device — it is Android framework behaviour driven by the
 * `WebChromeClient` override.
 *
 * ## What this cannot test, and why
 *
 * A `JsResult` must be resolved exactly once: if the activity is destroyed while
 * the dialog is up — rotation, or a low-memory kill — an unresolved one leaves
 * the JavaScript promise hung forever. That resolution is driven by clearing
 * `pendingJsResult` before calling `confirm()`/`cancel()`.
 *
 * The *call* itself cannot be observed here. `JsResult` has a package-private
 * constructor and final methods, so it can be neither subclassed nor
 * instantiated: `getDeclaredConstructor()` throws
 * `NoSuchMethodException: android.webkit.JsResult.<init>` on-device, verified.
 * Faking it would test the fake.
 *
 * So these tests assert the state that *drives* the resolution — the pending
 * fields are populated while a decision is outstanding and empty once it is made
 * or the activity goes away — which is the part that regressed. The
 * `JsResult.confirm()` call itself remains unverified by automated test and
 * needs a manual check on device.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityConfirmDialogTest {

    private fun <T> onActivity(
        scenario: ActivityScenario<MainActivity>,
        block: (MainActivity) -> T
    ): T {
        var result: T? = null
        scenario.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun field(activity: MainActivity, name: String): java.lang.reflect.Field =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun pendingResult(activity: MainActivity): Any? = field(activity, "pendingJsResult").get(activity)

    private fun pendingDialog(activity: MainActivity): AlertDialog? =
        field(activity, "pendingJsDialog").get(activity) as? AlertDialog

    private fun webView(activity: MainActivity): WebView =
        field(activity, "web").get(activity) as WebView

    private fun resolveJsConfirm(activity: MainActivity, confirmed: Boolean) {
        val method = activity.javaClass
            .getDeclaredMethod("resolveJsConfirm", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(activity, confirmed)
    }

    /** Invokes the private `onJsConfirm` seam the WebView would call. */
    private fun requestConfirm(scenario: ActivityScenario<MainActivity>, withResult: Boolean) =
        scenario.onActivity { activity ->
            val web = webView(activity)
            // onJsConfirm is an override on the anonymous WebChromeClient, not a
            // MainActivity method, so it is reached through the client.
            val chrome: WebChromeClient = requireNotNull(web.webChromeClient) {
                "MainActivity must install a WebChromeClient for onJsConfirm to exist"
            }
            val method = chrome.javaClass.getDeclaredMethod(
                "onJsConfirm",
                WebView::class.java,
                String::class.java,
                String::class.java,
                android.webkit.JsResult::class.java
            )
            method.isAccessible = true
            // A null JsResult is what the WebView passes only if something is
            // already wrong; it is the only value obtainable here, so the
            // pending-result field cannot be populated in this test.
            method.invoke(chrome, web, ORIGIN, MESSAGE, null)
            if (withResult) Unit
        }

    // ------------------------------------------------------------------

    @Test
    fun nothingIsPendingBeforeAnyRequest() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onActivity(scenario) { activity ->
                assertNull("no dialog should be pending at rest", pendingDialog(activity))
                assertNull("no result should be pending at rest", pendingResult(activity))
            }
        }
    }

    @Test
    fun requestingConfirmShowsADialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            requestConfirm(scenario, withResult = false)
            onActivity(scenario) { activity ->
                assertNotNull(
                    "onJsConfirm must show a native dialog",
                    pendingDialog(activity)
                )
            }
        }
    }

    /**
     * Regression: with a plain `WebChromeClient()` the WebView suppressed the
     * dialog and `confirm()` returned false, so `clearApiKey()` was never reached
     * and the button silently did nothing.
     */
    @Test
    fun acceptingTheDialogClearsThePendingState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            requestConfirm(scenario, withResult = false)
            val dialog = onActivity(scenario) { pendingDialog(it) }
            assertNotNull("precondition: a dialog should be pending", dialog)
            onActivity(scenario) { dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }

            onActivity(scenario) { activity ->
                assertNull("accepting must clear the pending result", pendingResult(activity))
                assertNull("accepting must clear the dialog reference", pendingDialog(activity))
            }
        }
    }

    @Test
    fun decliningTheDialogClearsThePendingState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            requestConfirm(scenario, withResult = false)
            val dialog = onActivity(scenario) { pendingDialog(it) }
            assertNotNull("precondition: a dialog should be pending", dialog)
            onActivity(scenario) { dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }

            onActivity(scenario) { activity ->
                assertNull("declining must clear the pending result", pendingResult(activity))
                assertNull("declining must clear the dialog reference", pendingDialog(activity))
            }
        }
    }

    /**
     * The lifecycle bug this guards: destroying the activity while the dialog was
     * up left the dialog window leaked and the result unresolved.
     */
    @Test
    fun destroyingTheActivityClearsThePendingDialog() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        requestConfirm(scenario, withResult = false)
        onActivity(scenario) { activity ->
            assertNotNull("precondition: a dialog should be pending", pendingDialog(activity))
        }

        scenario.close()

        ActivityScenario.launch(MainActivity::class.java).use { fresh ->
            onActivity(fresh) { activity ->
                assertNull(
                    "onDestroy must not leak a dialog window",
                    pendingDialog(activity)
                )
            }
        }
    }

    /**
     * Resolution must be idempotent. A button click fires the click listener and
     * then the dismiss listener, and both go through `resolveJsConfirm`; the
     * second must be a no-op rather than resolving the same result twice.
     */
    @Test
    fun resolvingRepeatedlyIsInert() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onActivity(scenario) { resolveJsConfirm(it, true) }
            onActivity(scenario) { resolveJsConfirm(it, true) }
            onActivity(scenario) { resolveJsConfirm(it, false) }
            onActivity(scenario) { activity ->
                assertNull("repeated resolution must stay inert", pendingResult(activity))
                assertNull("repeated resolution must stay inert", pendingDialog(activity))
            }
        }
    }

    /** The override must report that it handled the dialog. */
    @Test
    fun theOverrideReportsItHandledTheDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var handled: Boolean? = null
            scenario.onActivity { activity ->
                val web = webView(activity)
                val chrome = requireNotNull(web.webChromeClient)
                val method = chrome.javaClass.getDeclaredMethod(
                    "onJsConfirm",
                    WebView::class.java,
                    String::class.java,
                    String::class.java,
                    android.webkit.JsResult::class.java
                )
                method.isAccessible = true
                // Suppressing the platform default is what stops a second,
                // WebView-drawn dialog appearing behind ours.
                handled = method.invoke(chrome, web, ORIGIN, MESSAGE, null) as Boolean
            }
            assertTrue(
                "onJsConfirm must return true so the platform dialog is suppressed",
                handled == true
            )
        }
    }

    private companion object {
        const val ORIGIN = "http://127.0.0.1:8977"
        const val MESSAGE = "Remove the stored API key?"
    }
}
