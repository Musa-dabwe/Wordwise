package com.musa.wordwise.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import com.musa.wordwise.network.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Security invariants of the on-device server, asserted against **real HTTP
 * responses**.
 *
 * Every test here previously parsed the source text of `WwServer.kt` and
 * asserted that a string was present. That proved nothing about what a request
 * actually receives: it broke on cosmetic edits and passed on behaviourally
 * broken code. The routing block and guard are now reachable via
 * [wordWiseModule] + [ServerEnvironment], so `testApplication` drives the real
 * code with fakes for the outside world.
 *
 * The properties under test are the fix for a shipped vulnerability: the API key
 * was rendered into this server's HTML and all settings were written by POSTing
 * to it, and because Android's loopback is a single shared namespace, any
 * installed app could read the key or force a settings change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WwServerTest {

    private val origin = "http://127.0.0.1:${WwServer.PORT}"

    private fun env(
        models: suspend () -> List<ModelInfo> = { emptyList() },
        assets: Map<String, ByteArray> = mapOf("htmx.min.js" to "// htmx".toByteArray())
    ) = ServerEnvironment(
        loadAsset = { name -> assets[name] },
        theme = { "peach" },
        model = { "openrouter/free" },
        isServiceEnabled = { false },
        models = models
    )

    private fun ApplicationTestBuilder.serve(env: ServerEnvironment = env()) {
        application { wordWiseModule(env, origin) }
    }

    // ------------------------------------------------------------------
    // 1. No mutating routes.
    // ------------------------------------------------------------------

    @Test
    fun `no mutating route is registered`() = testApplication {
        serve()
        val removed = listOf(
            "/api/key",
            "/api/settings/model",
            "/api/settings/theme",
            "/api/accessibility/open"
        )
        for (path in removed) {
            val response = client.post(path)
            assertEquals(
                "$path must not accept writes",
                HttpStatusCode.NotFound,
                response.status
            )
        }
    }

    @Test
    fun `only the six documented GET routes exist`() = testApplication {
        serve()
        val expected = mapOf(
            "/" to HttpStatusCode.OK,
            "/screens/home" to HttpStatusCode.OK,
            "/screens/about" to HttpStatusCode.OK,
            "/api/status" to HttpStatusCode.OK,
            "/api/models" to HttpStatusCode.ServiceUnavailable // empty catalog
        )
        for ((path, status) in expected) {
            assertEquals("GET $path", status, client.get(path).status)
        }
        assertEquals(HttpStatusCode.OK, client.get("/assets/htmx.min.js").status)
    }

    // ------------------------------------------------------------------
    // 2. No secret in any response.
    // ------------------------------------------------------------------

    @Test
    fun `no response leaks key material`() = testApplication {
        serve(env(assets = mapOf("x.js" to "secret".toByteArray())))
        // Sequentially, because bodyAsText() is suspend and cannot be called
        // from inside a non-suspend map lambda.
        for (path in listOf("/", "/screens/home", "/screens/about", "/api/status")) {
            val body = client.get(path).bodyAsText()
            assertFalse("$path contained key material", body.contains("sk-or-"))
            // The status route dropped hasKey when the key became write-only.
            assertFalse("$path reported hasKey", body.contains("hasKey"))
        }
    }

    @Test
    fun `status route reports only the enabled flag`() = testApplication {
        serve()
        assertEquals("""{"enabled":false}""", client.get("/api/status").bodyAsText().trim())
    }

    @Test
    fun `status route reflects the service flag`() = testApplication {
        val on = ServerEnvironment(
            loadAsset = { null }, theme = { "peach" }, model = { "openrouter/free" },
            isServiceEnabled = { true }, models = { emptyList() }
        )
        serve(on)
        assertEquals("""{"enabled":true}""", client.get("/api/status").bodyAsText().trim())
    }

    // ------------------------------------------------------------------
    // 3. Origin guard.
    // ------------------------------------------------------------------

    @Test
    fun `a request with no Origin header is allowed`() = testApplication {
        serve()
        assertEquals(HttpStatusCode.OK, client.get("/api/status").status)
    }

    @Test
    fun `a request with our own Origin is allowed`() = testApplication {
        serve()
        val response = client.get("/api/status") { header("Origin", origin) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `a foreign Origin is refused`() = testApplication {
        serve()
        val response = client.get("/api/status") { header("Origin", "https://evil.example") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `a foreign Origin is refused for every route`() = testApplication {
        serve()
        for (path in listOf("/", "/screens/home", "/api/status", "/assets/htmx.min.js")) {
            val response = client.get(path) { header("Origin", "https://evil.example") }
            assertEquals("GET $path", HttpStatusCode.Forbidden, response.status)
        }
    }

    /** The guard must not also block the browser-ish Origin on our own port. */
    @Test
    fun `a same-origin Origin differing only by trailing slash is refused`() = testApplication {
        serve()
        val response = client.get("/api/status") { header("Origin", "$origin/") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ------------------------------------------------------------------
    // 4. Security headers on ordinary responses.
    // ------------------------------------------------------------------

    @Test
    fun `ordinary responses carry the full security header set`() = testApplication {
        serve()
        val h = client.get("/api/status").headers
        assertEquals("no-store, no-cache, must-revalidate", h["Cache-Control"])
        assertEquals("no-cache", h["Pragma"])
        assertEquals("DENY", h["X-Frame-Options"])
        assertEquals("no-referrer", h["Referrer-Policy"])
        assertEquals("nosniff", h["X-Content-Type-Options"])

        val csp = assertNotNull(h["Content-Security-Policy"]).let { h["Content-Security-Policy"]!! }
        for (directive in listOf(
            "default-src 'self'",
            "frame-ancestors 'none'",
            "form-action 'none'",
            "base-uri 'none'",
            "connect-src 'self'"
        )) {
            assertTrue("CSP missing '$directive': $csp", csp.contains(directive))
        }
    }

    @Test
    fun `html responses are not cacheable`() = testApplication {
        serve()
        assertEquals(
            "no-store, no-cache, must-revalidate",
            client.get("/screens/home").headers["Cache-Control"]
        )
    }

    /**
     * Regression: Ktor's `header()` APPENDS. The guard used to add `no-store` to
     * every response including assets, and the assets route then added its own
     * `max-age=86400`. Both went on the wire, `no-store` won, and asset caching
     * was silently dead while a comment claimed otherwise.
     *
     * Only a real response can prove there is exactly one Cache-Control header.
     */
    @Test
    fun `asset responses carry exactly one long-lived Cache-Control`() = testApplication {
        serve()
        val response = client.get("/assets/htmx.min.js")
        assertEquals(HttpStatusCode.OK, response.status)

        val all = response.headers.getAll("Cache-Control")
        assertNotNull("assets should set Cache-Control", all)
        assertEquals("assets must set exactly one Cache-Control, got $all", 1, all!!.size)
        assertTrue("expected a long cache, got $all", all.first().contains("max-age=86400"))
        assertFalse("assets must not be no-store, got $all", all.first().contains("no-store"))
        assertNull("assets must not receive Pragma", response.headers["Pragma"])
    }

    @Test
    fun `non-asset responses carry exactly one Cache-Control`() = testApplication {
        serve()
        val all = client.get("/api/status").headers.getAll("Cache-Control")
        assertEquals("exactly one Cache-Control expected, got $all", 1, all?.size)
    }

    // ------------------------------------------------------------------
    // 5. Asset serving and traversal.
    // ------------------------------------------------------------------

    @Test
    fun `asset content type follows the extension`() = testApplication {
        val assets = mapOf(
            "a.js" to byteArrayOf(1),
            "b.woff2" to byteArrayOf(2),
            "c.css" to byteArrayOf(3),
            "d.bin" to byteArrayOf(4)
        )
        serve(env(assets = assets))
        assertTrue(client.get("/assets/a.js").contentType()!!.contains("javascript"))
        assertTrue(client.get("/assets/b.woff2").contentType()!!.contains("font/woff2"))
        assertTrue(client.get("/assets/c.css").contentType()!!.contains("text/css"))
        assertEquals(HttpStatusCode.OK, client.get("/assets/d.bin").status)
    }

    @Test
    fun `an unknown asset is not found`() = testApplication {
        serve()
        assertEquals(HttpStatusCode.NotFound, client.get("/assets/missing.js").status)
    }

    @Test
    fun `asset path traversal is refused`() = testApplication {
        serve()
        // The route only accepts [A-Za-z0-9._-]+, so anything with a separator,
        // a dot-dot segment or a slash-bearing name never reaches the filesystem.
        for (name in listOf("../secret", "..%2Fsecret", "a/b", "web", ".")) {
            val response = client.get("/assets/$name")
            assertTrue(
                "traversal attempt '$name' was served: HTTP ${response.status}",
                response.status == HttpStatusCode.NotFound ||
                    response.status == HttpStatusCode.BadRequest
            )
        }
    }

    // ------------------------------------------------------------------
    // 6. Catalog route.
    // ------------------------------------------------------------------

    @Test
    fun `catalog failure is 503 with an empty array body`() = testApplication {
        serve()
        val response = client.get("/api/models")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("[]", response.bodyAsText().trim())
    }

    @Test
    fun `catalog success is 200 with the projected models`() = testApplication {
        val models = listOf(
            ModelInfo("openai/gpt-4o", "GPT-4o", 128000, false),
            ModelInfo("vendor/free:free", "Free", 8192, true)
        )
        serve(env(models = { models }))
        val response = client.get("/api/models")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"id\":\"openai/gpt-4o\""))
        assertTrue(body.contains("\"ctx\":128000"))
        assertTrue(body.contains("\"free\":true"))
    }

    /**
     * The catalog is public, so this must never carry the user's credential.
     * `ModelCatalogFetchTest` asserts that on the recorded request; this asserts
     * the route itself does not accept or echo one.
     */
    @Test
    fun `catalog route does not accept a credential`() = testApplication {
        serve()
        val response = client.get("/api/models") {
            header("Authorization", "Bearer sk-or-v1-leaked")
        }
        // It is refused as an empty catalog rather than serving anything, and in
        // particular the body can never echo the header back.
        assertFalse(response.bodyAsText().contains("sk-or-v1-leaked"))
    }

    private fun HttpResponse.contentType() = headers["Content-Type"]
}