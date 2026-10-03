// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import com.musa.wordwise.data.Prefs
import com.musa.wordwise.network.ModelCatalog
import com.musa.wordwise.network.ModelInfo
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Embedded Ktor server bound to the app process on localhost.
 * Serves the htmx frontend and the public model catalog. Port 8977 (not 8080)
 * so WordWise can coexist with PoetMusic on the same device.
 *
 * ## This server is display-only
 *
 * There are deliberately **no mutating routes and no secrets**. Reading the API
 * key and writing the key, model or theme all happen through
 * `MainActivity.WwNativeBridge`, which only the app's own WebView can reach.
 *
 * That is a security decision, not an architectural preference. Android's
 * loopback interface is a single shared namespace, so any app installed on the
 * device can request this port directly — no permission, no prompt. Every route
 * here is therefore either public data or already visible to the user, and the
 * absence of writes means a hostile page cannot force a state change (which for
 * the model route meant silently moving the user onto a paid model).
 *
 * Do not add a mutating route or render a secret into a response here. Add a
 * bridge method in `MainActivity` instead.
 */
object WwServer {

    const val PORT = 8977

    private val ORIGIN = "http://127.0.0.1:$PORT"

    private var started = false

    /**
     * In-memory catalog cache.
     *
     * Guarded by [catalogLock] because this is a check-then-act: without it,
     * N concurrent requests on a cold cache each fire their own fetch of the
     * 762 KB catalog. The negative TTL is deliberately short so a single
     * transient network failure does not suppress the picker for an hour.
     *
     * Not keyed by API key: the upstream fetch is unauthenticated because the
     * endpoint is public, so the response is not account-specific and caching it
     * cannot leak one user's entitlements to another.
     */
    private val catalogLock = Mutex()
    @Volatile private var cachedModels: List<ModelInfo> = emptyList()
    @Volatile private var cachedAt = 0L
    @Volatile private var negativeUntil = 0L
    private const val CATALOG_TTL_MS = 60L * 60L * 1000L
    private const val CATALOG_NEGATIVE_TTL_MS = 60_000L

    private suspend fun modelsOrFetch(): List<ModelInfo> {
        freshCatalog()?.let { return it }

        return catalogLock.withLock {
            // Re-check inside the lock: another coroutine may have just filled it.
            freshCatalog()?.let { return@withLock it }

            val now = SystemClock.elapsedRealtime()
            if (now < negativeUntil) return@withLock emptyList()

            val fetched = ModelCatalog.fetch().orEmpty()
            if (fetched.isNotEmpty()) {
                cachedModels = fetched
                cachedAt = now
                negativeUntil = 0L
            } else {
                negativeUntil = now + CATALOG_NEGATIVE_TTL_MS
            }
            fetched
        }
    }

    /** The cached catalog if still fresh, else null. */
    private fun freshCatalog(): List<ModelInfo>? =
        cachedModels.takeIf {
            it.isNotEmpty() && SystemClock.elapsedRealtime() - cachedAt < CATALOG_TTL_MS
        }

    private fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext

        embeddedServer(CIO, port = PORT, host = "127.0.0.1") {

            // Defence in depth. Nothing secret is served, so this cannot stop a
            // co-resident app on its own — it stops a *web page* the user is
            // visiting from driving the server, and from framing it.
            install(createApplicationPlugin("WwLocalGuard") {
                onCall { call ->
                    // A same-origin request from our own WebView sends no
                    // Origin (or sends ours). Anything else came from a page the
                    // user is browsing and has no business here.
                    val origin = call.request.headers["Origin"]
                    if (origin != null && origin != ORIGIN) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@onCall
                    }

                    // The settings document carries account state. Keep it out of
                    // the WebView disk cache and out of any forensic image.
                    // /assets is exempt: see the note on that route.
                    if (!call.request.path().startsWith("/assets/")) {
                        call.response.header("Cache-Control", "no-store, no-cache, must-revalidate")
                        call.response.header("Pragma", "no-cache")
                    }
                    // Never let a third party frame this origin.
                    call.response.header("X-Frame-Options", "DENY")
                    call.response.header(
                        "Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'none'; " +
                            "script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
                            "connect-src 'self'; img-src 'self' data:; " +
                            "font-src 'self'; base-uri 'none'; form-action 'none'"
                    )
                    call.response.header("Referrer-Policy", "no-referrer")
                    call.response.header("X-Content-Type-Options", "nosniff")
                }
            })

            routing {

                get("/") {
                    call.respondText(Shell.page(Prefs.getTheme(app)), ContentType.Text.Html)
                }

                get("/assets/{name}") {
                    val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.NotFound)
                    if (!name.matches(Regex("[A-Za-z0-9._-]+"))) return@get call.respond(HttpStatusCode.NotFound)
                    val type = when {
                        name.endsWith(".js") -> ContentType.parse("application/javascript")
                        name.endsWith(".woff2") -> ContentType.parse("font/woff2")
                        name.endsWith(".css") -> ContentType.Text.CSS
                        else -> ContentType.Application.OctetStream
                    }
                    val bytes = try {
                        app.assets.open("web/$name").use { it.readBytes() }
                    } catch (e: Exception) {
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                    // Fonts and the htmx runtime are content-stable, unlike the
                    // settings document, so they keep a long cache. The guard
                    // deliberately skips Cache-Control on this path, because
                    // Ktor's header() appends rather than replaces and a second
                    // no-store here would win.
                    call.response.header("Cache-Control", "max-age=86400")
                    call.respondBytes(bytes, type)
                }

                // ---------- screens ----------

                get("/screens/home") {
                    call.respondText(
                        Views.homeScreen(
                            isServiceEnabled(app),
                            Prefs.getModel(app),
                            Prefs.getTheme(app)
                        ),
                        ContentType.Text.Html
                    )
                }

                get("/screens/about") {
                    call.respondText(Views.aboutScreen(), ContentType.Text.Html)
                }

                // ---------- status ----------

                get("/api/status") {
                    val enabled = isServiceEnabled(app)
                    call.respondText(
                        """{"enabled":$enabled}""",
                        ContentType.Application.Json
                    )
                }

                // ---------- public model catalog ----------

                get("/api/models") {
                    val models = modelsOrFetch()
                    if (models.isEmpty()) {
                        // 503 rather than 204: this route is consumed by fetch(),
                        // not htmx, so a 204 with no body would look like success
                        // and fail later at r.json(). The client renders its own
                        // note, and the paste field still works.
                        call.respond(HttpStatusCode.ServiceUnavailable, "[]")
                    } else {
                        call.respondText(ModelCatalog.toJson(models), ContentType.Application.Json)
                    }
                }
            }
        }.start(wait = false)
    }
}
