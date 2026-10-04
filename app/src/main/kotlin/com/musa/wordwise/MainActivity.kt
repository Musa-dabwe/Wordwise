// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.data.Prefs
import com.musa.wordwise.network.ModelId
import com.musa.wordwise.server.Themes
import com.musa.wordwise.server.WwServer
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Native WebView container hosting the htmx frontend served by the embedded
 * Ktor server. External links (OpenRouter) open in the system browser; the
 * frontend drives native settings through [WwNativeBridge].
 *
 * ## Trust boundary
 *
 * The embedded server on 127.0.0.1:8977 is **display-only**. Every secret read
 * and every settings write goes through [WwNativeBridge], which is only reachable
 * from the WebView this activity creates.
 *
 * This matters because Android's loopback is a single shared namespace: any
 * installed app holding INTERNET can request that port directly. When the API key
 * was rendered into the settings HTML and settings were saved by POSTing to the
 * local server, any co-resident app could read the key and silently rewrite the
 * user's settings, including switching to a paid model. Keeping the socket
 * free of secrets and free of mutating routes removes that class of attack
 * rather than trying to authenticate it.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    // Shared with GrammarFixService so a key saved or removed in settings takes
    // effect immediately instead of on next service start.
    private val apiKeyRepository by lazy { ApiKeyRepository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status bar follows the selected pastel theme's canvas color.
        applyStatusBarColor(Themes.byKey(Prefs.getTheme(this)).statusBar)

        web = WebView(this)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Pinch and double-tap zoom would stretch the fixed pastel layout;
            // the viewport meta and touch-action CSS in Shell.kt back this up.
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // Belt and braces with the server's no-store headers: never let the
            // settings document reach Chromium's on-disk cache.
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        web.addJavascriptInterface(WwNativeBridge(), "WwNative")
        web.webChromeClient = object : WebChromeClient() {
            /**
             * Without this override the WebView suppresses JS dialogs and
             * `confirm()` returns false, so the Remove saved key button would
             * silently do nothing.
             *
             * Non-cancelable because the callback must be invoked exactly once;
             * letting the dialog be dismissed any other way would leave the
             * WebView waiting on a result that never arrives.
             */
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    if (isDestroyed) { result?.cancel(); return@runOnUiThread }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Remove API key")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("Remove") { _, _ -> result?.confirm() }
                        .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                        .show()
                }
                // true = the app handled it, so suppress the WebView's default.
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Keep navigation inside the embedded server; anything else
                // (the OpenRouter links) opens in the user's browser.
                if (request.url.host == "127.0.0.1") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url

                // addJavascriptInterface injects the bridge into EVERY frame in
                // this WebView, and shouldOverrideUrlLoading is not guaranteed
                // to fire for subframes. So block any non-loopback subresource
                // outright rather than relying on the top-level navigation check:
                // that keeps a cross-origin iframe, image or font from ever
                // executing with WwNative in scope.
                if (url.host != "127.0.0.1") {
                    return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden",
                        emptyMap(), ByteArray(0).inputStream())
                }

                // Serve static assets straight from the APK: no Ktor round trip
                // and, because intercepted responses bypass Chromium's network
                // stack, nothing lands in the WebView HTTP disk cache.
                if (url.path?.startsWith("/assets/") != true) return null
                val name = url.lastPathSegment ?: return null
                if (!name.matches(Regex("[A-Za-z0-9._-]+"))) return null
                val mime = when {
                    name.endsWith(".js") -> "application/javascript"
                    name.endsWith(".woff2") -> "font/woff2"
                    name.endsWith(".css") -> "text/css"
                    else -> "application/octet-stream"
                }
                return try {
                    WebResourceResponse(mime, null, assets.open("web/$name"))
                } catch (e: Exception) {
                    null // Fall through to the Ktor /assets route.
                }
            }
        }
        setContentView(web)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("wwBack()") { result ->
                    if (result?.contains("exit") == true) moveTaskToBack(true)
                }
            }
        })

        loadWhenServerReady()
    }

    private fun applyStatusBarColor(hex: String) {
        val color = runCatching { Color.parseColor(hex) }.getOrNull() ?: return
        window.statusBarColor = color
        // Pastel accents are light, so keep the status bar icons dark.
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = luminance > 0.5
    }

    /**
     * The one JS-to-native trust boundary.
     *
     * Mutating methods return `""` on success and a user-facing message on
     * failure, so the frontend can surface the reason without a second channel.
     * They never return secret material: [hasApiKey] reports a boolean, and the
     * key itself can only ever be written, never read back out.
     *
     * Called on a WebView background thread, so anything touching the UI hops
     * to the main thread explicitly. Preference and keystore access is
     * thread-safe.
     */
    inner class WwNativeBridge {

        // ---------- API key ----------

        /**
         * Whether a key is stored. Deliberately a boolean: the key is never
         * readable from JS, so it cannot leak back out through the bridge,
         * through the local server, or into a screenshot of the DOM.
         */
        @JavascriptInterface
        fun hasApiKey(): Boolean = apiKeyRepository.hasApiKey()

        /** Saves [key], replacing any existing one. `""` on success. */
        @JavascriptInterface
        fun saveApiKey(key: String): String {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) return "API key cannot be empty"
            if (trimmed.length > 200) return "That does not look like an OpenRouter key"
            apiKeyRepository.saveApiKey(trimmed)
            return ""
        }

        /**
         * Deletes the stored key.
         *
         * Needed because the key is write-only: making it unreadable also removed
         * the user's only in-app way to get rid of it, which would otherwise be
         * clearing app data.
         */
        @JavascriptInterface
        fun clearApiKey(): String {
            apiKeyRepository.clearApiKey()
            return ""
        }

        // ---------- model ----------

        @JavascriptInterface
        fun getModel(): String = Prefs.getModel(this@MainActivity)

        /** Validates and stores the model. Returns [ModelId]'s reason on reject. */
        @JavascriptInterface
        fun setModel(raw: String): String =
            when (val result = ModelId.validate(raw)) {
                is ModelId.Result.Valid -> {
                    Prefs.setModel(this@MainActivity, result)
                    ""
                }
                is ModelId.Result.Invalid -> result.reason
            }

        // ---------- theme ----------

        @JavascriptInterface
        fun getTheme(): String = Prefs.getTheme(this@MainActivity)

        @JavascriptInterface
        fun setTheme(key: String): String {
            if (key !in Themes.KEYS) return "Unknown theme"
            Prefs.setTheme(this@MainActivity, key)
            runOnUiThread { applyStatusBarColor(Themes.byKey(key).statusBar) }
            return ""
        }

        // ---------- native chrome ----------

        @JavascriptInterface
        fun setStatusBarColor(hex: String) {
            runOnUiThread { if (!isDestroyed) applyStatusBarColor(hex) }
        }

        @JavascriptInterface
        fun openAccessibilitySettings() {
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this@MainActivity, R.string.toast_accessibility_hint, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadWhenServerReady() {
        thread {
            for (attempt in 0 until 40) {
                try {
                    Socket().use { it.connect(InetSocketAddress("127.0.0.1", WwServer.PORT), 250) }
                    break
                } catch (_: Exception) {
                    Thread.sleep(150)
                }
            }
            runOnUiThread {
                if (!isDestroyed) web.loadUrl("http://127.0.0.1:${WwServer.PORT}/")
            }
        }
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            // Drop the injected bridge before the WebView goes away.
            web.removeJavascriptInterface("WwNative")
            web.clearCache(false)
        }
        super.onDestroy()
    }
}
