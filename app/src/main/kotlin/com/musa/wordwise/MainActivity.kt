// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.musa.wordwise.data.Prefs
import com.musa.wordwise.server.WwServer
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Native WebView container hosting the htmx frontend served by the embedded
 * Ktor server. External links (Google AI Studio) open in the browser; the
 * frontend drives the system Accessibility settings through [WwServer].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status bar follows the user's accent color instead of the theme default.
        applyStatusBarColor(Prefs.getAccent(this))

        WwServer.accessibilitySettingsRequester = {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, R.string.toast_accessibility_hint, Toast.LENGTH_LONG).show()
            }
        }

        web = WebView(this)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Pinch and double-tap zoom would stretch the fixed pastel layout;
            // the viewport meta and touch-action CSS in Shell.kt back this up.
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        web.addJavascriptInterface(WwNativeBridge(), "WwNative")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Keep navigation inside the embedded server; anything else
                // (the AI Studio link) opens in the user's browser.
                if (request.url.host == "127.0.0.1") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // Serve static assets straight from the APK: no Ktor round trip
                // and, because intercepted responses bypass Chromium's network
                // stack, nothing lands in the WebView HTTP disk cache.
                val url = request.url
                if (url.host != "127.0.0.1" || url.path?.startsWith("/assets/") != true) return null
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

    /** Exposed to the WebView so the frontend can drive native chrome. */
    inner class WwNativeBridge {
        @JavascriptInterface
        fun setStatusBarColor(hex: String) {
            runOnUiThread { if (!isDestroyed) applyStatusBarColor(hex) }
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
        WwServer.accessibilitySettingsRequester = null
        if (::web.isInitialized) web.clearCache(false)
        super.onDestroy()
    }
}
