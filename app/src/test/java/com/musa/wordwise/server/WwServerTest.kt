// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for the security guard of the embedded server in [WwServer].
 *
 * ## Why most of these tests read the source instead of driving the socket
 *
 * [WwServer.start] binds a real port (127.0.0.1:8977) and its `started` flag is
 * one-shot, so it cannot be started per-test; and the routing block and the
 * `WwLocalGuard` plugin are inline lambdas inside it with no seam that
 * `testApplication` could drive. The invariants that matter here are therefore
 * asserted at the source level: the guard's predicates, the route table, the
 * header values and the cache constants are parsed out of `WwServer.kt` and
 * checked exactly. Every assertion is written to fail if the corresponding
 * line is removed, weakened or re-ordered.
 *
 * The two pure render functions the routes call — [Views.homeScreen] and
 * [Shell.page] — have no Android dependencies, so the "no secret in any
 * response" invariant is additionally tested behaviourally by calling them
 * directly. They run under the Robolectric runner per project convention.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WwServerTest {

    private val source: String = readServerSource()

    /** Whitespace-collapsed view of the source, for multi-line expressions. */
    private val normalized: String = source.replace(Regex("\\s+"), " ")

    companion object {
        private const val SERVER_KOTLIN = "src/main/kotlin/com/musa/wordwise/server/WwServer.kt"
        private const val WEB_ASSETS_DIR = "src/main/assets/web"

        private fun readServerSource(): String = locate(SERVER_KOTLIN).readText()

        /**
         * Locates a project file whether the test JVM's working directory is the
         * module dir (Gradle default), the repo root, or a subdirectory.
         */
        private fun locate(relative: String): File {
            val candidates = mutableListOf(File(relative), File("app/$relative"))
            var dir = File(".").absoluteFile
            while (true) {
                candidates += File(dir, relative)
                candidates += File(dir, "app/$relative")
                dir = dir.parentFile ?: break
            }
            return candidates.firstOrNull { it.exists() }
                ?: error("Cannot locate $relative from working directory ${File(".").absolutePath}")
        }
    }

    /** The `routing { ... }` block: the last block in the file. */
    private fun routingBlock(): String = source.substringAfter("routing {")

    /** The `WwLocalGuard` plugin's `onCall` block, which ends where routing begins. */
    private fun onCallBlock(): String =
        source.substringAfter("onCall { call ->").substringBefore("routing {")

    private fun assetsRouteBlock(): String =
        routingBlock().substringAfter("get(\"/assets/{name}\")").substringBefore("get(\"/screens/home\")")

    /** Drops `//` line comments so prose about a header cannot satisfy a search for it. */
    private fun stripComments(src: String): String =
        src.lines().joinToString("\n") { it.substringBefore("//") }

    private fun statusRouteBlock(): String =
        routingBlock().substringAfter("get(\"/api/status\")").substringBefore("get(\"/api/models\")")

    /** The `/api/models` route is the last one in the file. */
    private fun modelsRouteBlock(): String = routingBlock().substringAfter("get(\"/api/models\")")

    // ------------------------------------------------------------------
    // Invariant 1: no mutating routes exist.
    // ------------------------------------------------------------------

    /**
     * Invariant 1 — the removed write routes must stay removed.
     *
     * Android's loopback is a shared namespace: any installed app, or any web
     * page via a no-preflight form POST, could rewrite settings while these
     * routes existed — including silently switching the user to a paid model.
     * Ktor answers 404 for a path with no registered route, so the absence of
     * a `post(...)` registration IS the "POST returns 404" guarantee.
     */
    @Test
    fun `no mutating routes are registered`() {
        assertFalse("a POST route was re-added", source.contains("post("))
        assertFalse("a PUT route was re-added", source.contains("put("))
        assertFalse("a DELETE route was re-added", source.contains("delete("))
        assertFalse("a PATCH route was re-added", source.contains("patch("))

        // The four routes removed for the loopback-write vulnerability.
        assertFalse(source.contains("post(\"/api/key\")"))
        assertFalse(source.contains("post(\"/api/settings/model\")"))
        assertFalse(source.contains("post(\"/api/settings/theme\")"))
        assertFalse(source.contains("post(\"/api/accessibility/open\")"))
    }

    // ------------------------------------------------------------------
    // Invariant 2: only the six public GET routes exist.
    // ------------------------------------------------------------------

    /**
     * Invariant 2 — the route table is exactly the six public, read-only
     * routes. An extra `get(` (or any `route(`/`resource(`) fails the count.
     */
    @Test
    fun `only the six public GET routes exist`() {
        val routing = routingBlock()
        listOf(
            "get(\"/\")",
            "get(\"/assets/{name}\")",
            "get(\"/screens/home\")",
            "get(\"/screens/about\")",
            "get(\"/api/status\")",
            "get(\"/api/models\")"
        ).forEach { route ->
            assertTrue("missing route $route", routing.contains(route))
        }
        assertEquals("unexpected extra routes", 6, Regex("get\\(").findAll(routing).count())
        assertFalse(routing.contains("post("))
        assertFalse(routing.contains("put("))
        assertFalse(routing.contains("delete("))
        assertFalse(routing.contains("patch("))
        assertFalse(routing.contains("route("))
        assertFalse(routing.contains("resource("))
    }

    // ------------------------------------------------------------------
    // Invariant 3: no secret in any response.
    // ------------------------------------------------------------------

    /**
     * Invariant 3 (behavioural half) — the settings screen must never render
     * the OpenRouter key. The key is write-only and lives behind the WebView
     * bridge; the local port is readable by any app on the device.
     *
     * The positive controls ("SERVICE PAUSED", the model id, the API KEY
     * label) fail if the renderer breaks, so the absence checks cannot pass
     * vacuously.
     */
    @Test
    fun `home screen renders no api key`() {
        val html = Views.homeScreen(
            serviceEnabled = false,
            currentModel = "vendor/model",
            currentTheme = "peach"
        )

        // Positive controls: the screen really rendered.
        assertTrue(html.contains("SERVICE PAUSED"))
        assertTrue(html.contains("vendor/model"))
        assertTrue(html.contains("API KEY"))

        // OpenRouter keys all start with sk-or-; the old status payload's
        // hasKey flag must not come back either.
        assertFalse("API key rendered into the settings screen", html.contains("sk-or-"))
        assertFalse(html.contains("hasKey"))

        // The key field must be an empty password input — no value attribute.
        val keyInput = Regex("<input id=\"key-input\"[^>]*>").find(html)
        assertTrue("key input missing", keyInput != null)
        assertFalse("key input carries a value", keyInput!!.value.contains("value="))
    }

    /**
     * Invariant 3 (behavioural half) — the app shell must not render a key.
     */
    @Test
    fun `shell page renders no api key`() {
        val html = Shell.page("peach")

        // Positive controls.
        assertTrue(html.contains("WordWise"))
        assertTrue(html.contains("/assets/htmx.min.js"))

        assertFalse("API key rendered into the shell page", html.contains("sk-or-"))
        assertFalse(html.contains("hasKey"))
    }

    /**
     * Invariant 3 (source half) — /api/status returns only {"enabled":bool}.
     * The old payload also carried hasKey, which told any co-resident app
     * whether the user had a key stored.
     */
    @Test
    fun `status route returns only the enabled flag`() {
        val status = statusRouteBlock()
        assertTrue(status.contains("\"\"\"{\"enabled\":\$enabled}\"\"\""))
        assertFalse("hasKey leaked into a response", source.contains("hasKey"))
    }

    // ------------------------------------------------------------------
    // Invariant 4: foreign Origin is refused with 403.
    // ------------------------------------------------------------------

    /**
     * Invariant 4 — the Origin guard's truth table, asserted on the exact
     * predicate: no Origin header -> allowed; Origin exactly
     * http://127.0.0.1:8977 -> allowed; anything else -> 403.
     *
     * This is what stops a web page the user is browsing from driving the
     * server: a form POST needs no preflight, so without this check the
     * loopback surface would be reachable from any tab.
     */
    @Test
    fun `guard allows same-origin and refuses foreign origins`() {
        val guard = onCallBlock()

        assertTrue(guard.contains("val origin = call.request.headers[\"Origin\"]"))
        assertTrue(guard.contains("if (origin != null && origin != ORIGIN) {"))
        assertTrue(guard.contains("call.respond(HttpStatusCode.Forbidden)"))
        assertTrue(guard.contains("return@onCall"))

        // The refusal must come after the check, not before it.
        assertTrue(
            "Forbidden respond must follow the origin check",
            guard.indexOf("origin != null && origin != ORIGIN") <
                guard.indexOf("call.respond(HttpStatusCode.Forbidden)")
        )

        // The allowed origin is exactly the server's own origin.
        assertTrue(source.contains("private val ORIGIN = \"http://127.0.0.1:\$PORT\""))
        assertTrue(source.contains("const val PORT = 8977"))
    }

    /**
     * Invariant 4 (ordering) — the guard is installed before routing, so it
     * stays defence in depth even if a route is added carelessly later.
     */
    @Test
    fun `guard is installed ahead of routing`() {
        assertTrue(
            source.indexOf("createApplicationPlugin(\"WwLocalGuard\")") <
                source.indexOf("routing {")
        )
    }

    // ------------------------------------------------------------------
    // Invariant 5: security headers on normal responses.
    // ------------------------------------------------------------------

    /**
     * Invariant 5 — the security headers on normal responses.
     *
     * The settings document carries account state, so it must not be cached
     * or framed; the CSP's frame-ancestors/form-action 'none' make the
     * X-Frame-Options header redundant rather than load-bearing.
     */
    @Test
    fun `security headers are emitted on normal responses`() {
        val guard = onCallBlock()
        assertTrue(
            guard.contains(
                "call.response.header(\"Cache-Control\", \"no-store, no-cache, must-revalidate\")"
            )
        )
        assertTrue(guard.contains("call.response.header(\"Pragma\", \"no-cache\")"))
        assertTrue(guard.contains("call.response.header(\"X-Frame-Options\", \"DENY\")"))
        assertTrue(guard.contains("call.response.header(\"Referrer-Policy\", \"no-referrer\")"))
        assertTrue(guard.contains("call.response.header(\"X-Content-Type-Options\", \"nosniff\")"))

        // The CSP is concatenated across lines, so match it normalised.
        assertTrue(
            "CSP must be set via response.header",
            normalized.contains("call.response.header( \"Content-Security-Policy\",")
        )
        assertTrue(normalized.contains("default-src 'self'"))
        assertTrue(normalized.contains("frame-ancestors 'none'"))
        assertTrue(normalized.contains("form-action 'none'"))
        assertTrue(normalized.contains("base-uri 'none'"))
    }

    // ------------------------------------------------------------------
    // Invariant 6: the assets route must NOT get no-store.
    // ------------------------------------------------------------------

    /**
     * Invariant 6 - the assets route must NOT get no-store.
     *
     * Past bug: Ktor's response.header() APPENDS rather than replaces, so the
     * guard's no-store combined with the route's max-age=86400 produced two
     * conflicting Cache-Control headers and no-store won, silently defeating
     * asset caching. The guard now skips Cache-Control/Pragma for /assets/.
     */
    @Test
    fun `asset responses keep max-age and never receive no-store`() {
        val guard = onCallBlock()
        assertTrue(
            "Cache-Control/Pragma must be inside the /assets/ exemption",
            guard.contains("if (!call.request.path().startsWith(\"/assets/\")) {")
        )
        // ...and the headers really are inside that if: the order is
        // if(assets-exempt) -> no-store -> Pragma -> X-Frame-Options (outside).
        assertTrue(
            guard.indexOf("startsWith(\"/assets/\")") <
                guard.indexOf("\"Cache-Control\", \"no-store")
        )
        assertTrue(
            guard.indexOf("\"Cache-Control\", \"no-store") <
            guard.indexOf("\"Pragma\", \"no-cache\"")
        )
        assertTrue(
            guard.indexOf("\"Pragma\", \"no-cache\"") <
            guard.indexOf("\"X-Frame-Options\"")
        )

        val assets = stripComments(assetsRouteBlock())
        assertTrue(
            "asset route must set exactly one long-lived Cache-Control",
            assets.contains("call.response.header(\"Cache-Control\", \"max-age=86400\")")
        )
        // Comment-stripped: the route's own comment explains that no-store must
        // NOT be added here, and a bare substring match would trip over it.
        assertFalse("asset route must not emit no-store", assets.contains("no-store"))
    }

    /**
     * Invariants 6/8 (tie-in) — the assets the pages reference exist on disk
     * under the web/ prefix the route opens, so the long cache and the
     * traversal guard are protecting real files.
     */
    @Test
    fun `served asset names exist on disk`() {
        val dir = locate(WEB_ASSETS_DIR)
        assertTrue("$WEB_ASSETS_DIR missing", dir.isDirectory)
        listOf("htmx.min.js", "outfit-latin.woff2", "outfit-latin-ext.woff2").forEach {
            assertTrue("asset $it missing", File(dir, it).exists())
        }
    }

    // ------------------------------------------------------------------
    // Invariant 7: /api/models failure is 503 with a [] body.
    // ------------------------------------------------------------------

    /**
     * Invariant 7 — /api/models answers 503 with a [] body on catalog
     * failure, not 204.
     *
     * Past bug: this route is consumed by fetch(), not htmx, so a 204 with no
     * body looked like success and then threw at r.json() — the picker died
     * with a confusing network error instead of its own "could not load" note.
     */
    @Test
    fun `models route answers 503 with an empty json array on failure`() {
        val models = modelsRouteBlock()
        assertTrue(
            models.contains("call.respond(HttpStatusCode.ServiceUnavailable, \"[]\")")
        )
        assertTrue(
            "success path must return the serialised catalog",
            models.contains("call.respondText(ModelCatalog.toJson(models), ContentType.Application.Json)")
        )
        assertFalse("204 must not come back", source.contains("NoContent"))
    }

    // ------------------------------------------------------------------
    // Invariant 8: /assets/{name} refuses path traversal.
    // ------------------------------------------------------------------

    /**
     * Invariant 8 — /assets/{name} refuses path traversal.
     *
     * The name is matched against [A-Za-z0-9._-]+ before the asset is opened,
     * so ../ and a/b cannot escape web/. The regex literal is asserted exactly
     * so a future "relaxation" (e.g. adding /) fails here first.
     */
    @Test
    fun `asset names are restricted to a safe character set before the file is opened`() {
        val assets = assetsRouteBlock()
        assertTrue(
            assets.contains(
                "if (!name.matches(Regex(\"[A-Za-z0-9._-]+\"))) return@get call.respond(HttpStatusCode.NotFound)"
            )
        )
        assertTrue(assets.contains("app.assets.open(\"web/\$name\")"))
        // The gate must run before the open.
        assertTrue(
            "regex gate must precede the asset open",
            assets.indexOf("name.matches(Regex(") < assets.indexOf("app.assets.open(")
        )
    }

    // ------------------------------------------------------------------
    // Invariant 9: the catalog cache is single-flight and bounded.
    // ------------------------------------------------------------------

    /**
     * Invariant 9 — the catalog cache is single-flight and bounded.
     *
     * modelsOrFetch() cannot be driven from a unit test (it is private and
     * fetches over the network), so its structure is asserted at the source
     * level: a Mutex makes the check-then-act single-flight, the positive
     * TTL is 1 hour, the negative TTL is 60 seconds, and an empty result is
     * deliberately NOT written into the positive cache — only into the short
     * negative one — so one transient failure cannot suppress the picker for
     * an hour.
     */
    @Test
    fun `catalog cache is single flight and bounded`() {
        assertTrue(source.contains("private val catalogLock = Mutex()"))
        assertTrue(source.contains("return catalogLock.withLock {"))

        // 1-hour positive TTL, 60-second negative TTL.
        assertTrue(source.contains("private const val CATALOG_TTL_MS = 60L * 60L * 1000L"))
        assertTrue(source.contains("private const val CATALOG_NEGATIVE_TTL_MS = 60_000L"))

        // Double-checked locking: the freshness check runs again inside the lock.
        assertTrue(source.contains("freshCatalog()?.let { return it }"))
        assertTrue(source.contains("freshCatalog()?.let { return@withLock it }"))

        // The positive cache only accepts a non-empty result...
        assertTrue(source.contains("if (fetched.isNotEmpty()) {"))
        assertTrue(source.contains("cachedModels = fetched"))
        // ...and freshness requires non-emptiness too.
        assertTrue(
            source.contains(
                "it.isNotEmpty() && SystemClock.elapsedRealtime() - cachedAt < CATALOG_TTL_MS"
            )
        )

        // A failure is remembered only for the short negative TTL.
        assertTrue(source.contains("if (now < negativeUntil) return@withLock emptyList()"))
        assertTrue(source.contains("negativeUntil = now + CATALOG_NEGATIVE_TTL_MS"))
    }
}
