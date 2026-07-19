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
import android.view.accessibility.AccessibilityManager
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.data.Prefs
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext

/**
 * Embedded Ktor server bound to the app process on localhost.
 * Serves the htmx frontend and the settings API. Port 8977 (not 8080) so
 * WordWise can coexist with PoetMusic on the same device.
 */
object WwServer {

    const val PORT = 8977

    /** Set by MainActivity so the frontend can open the system Accessibility settings. */
    @Volatile var accessibilitySettingsRequester: (() -> Unit)? = null

    private var started = false

    /**
     * Determines whether an accessibility service from the application is enabled.
     *
     * @param context The context used to access accessibility services and identify the application.
     * @return `true` if an enabled accessibility service belongs to the application, `false` otherwise.
     */
    private fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    /**
     * Starts the embedded web server on the local device.
     *
     * @param context A context used to access application resources and preferences.
     */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val apiKeyRepository = ApiKeyRepository(app)

        embeddedServer(CIO, port = PORT, host = "127.0.0.1") {
            routing {

                get("/") {
                    call.respondText(
                        Shell.page(Prefs.getAccent(app), Prefs.getTint(app)),
                        ContentType.Text.Html
                    )
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
                    call.response.header("Cache-Control", "max-age=86400")
                    call.respondBytes(bytes, type)
                }

                // ---------- screens ----------

                get("/screens/home") {
                    call.respondText(
                        Views.homeScreen(app, isServiceEnabled(app), apiKeyRepository.getApiKey()),
                        ContentType.Text.Html
                    )
                }

                get("/screens/settings") {
                    call.respondText(Views.settingsScreen(app), ContentType.Text.Html)
                }

                // ---------- status ----------

                get("/api/status") {
                    val enabled = isServiceEnabled(app)
                    val hasKey = apiKeyRepository.getApiKey().isNotEmpty()
                    call.respondText(
                        """{"enabled":$enabled,"hasKey":$hasKey}""",
                        ContentType.Application.Json
                    )
                }

                // ---------- api key ----------

                post("/api/key") {
                    val key = call.receiveParameters()["key"]?.trim().orEmpty()
                    if (key.isEmpty()) {
                        toast("API key cannot be empty")
                    } else {
                        apiKeyRepository.saveApiKey(key)
                        toast("API key saved securely")
                    }
                }

                // ---------- settings ----------

                post("/api/accessibility/open") {
                    val requester = accessibilitySettingsRequester
                    if (requester == null) toast("Accessibility settings unavailable")
                    else { requester(); noContent() }
                }

                post("/api/settings/model") {
                    val m = call.request.queryParameters["m"]
                    if (m != null && m in Prefs.GEMINI_MODELS) {
                        Prefs.setSelectedModel(app, m)
                        call.response.header("HX-Trigger", """{"ww-toast":"Model set to $m"}""")
                    }
                    call.respondText(Views.modelCard(app), ContentType.Text.Html)
                }

                post("/api/settings/accent") {
                    val c = call.receiveParameters()["c"] ?: return@post noContent()
                    if (c.matches(Regex("#[0-9a-fA-F]{6}"))) Prefs.setAccent(app, c)
                    noContent()
                }

                post("/api/settings/theme") {
                    val name = call.receiveParameters()["name"] ?: return@post noContent()
                    if (name in Shell.CANVAS_TINTS) Prefs.setTint(app, name)
                    noContent()
                }
            }
        }.start(wait = false)
    }

    /**
     * Responds with an HTTP 204 No Content status.
     */
    private suspend fun PipelineContext<Unit, ApplicationCall>.noContent() {
        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * Responds with no content and triggers a client-side toast notification.
     *
     * @param message The text displayed in the toast notification.
     */
    private suspend fun PipelineContext<Unit, ApplicationCall>.toast(message: String) {
        call.response.header("HX-Trigger", """{"ww-toast":${jsonStr(message)}}""")
        call.respond(HttpStatusCode.NoContent)
    }
}
