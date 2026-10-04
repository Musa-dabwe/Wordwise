package com.musa.wordwise.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Network-level guards for [ModelCatalog.fetch].
 *
 * Security regression this protects: `fetch` used to send
 * `Authorization: Bearer <user key>` with the catalog request. The catalog
 * response is not account-specific, so every picker refresh spent the user's
 * credential on the wire for nothing — and any proxy or log on that path would
 * see it. The request is now deliberately unauthenticated, and that is asserted
 * here against a **real recorded request**, not by reading the source.
 *
 * `fetch` is redirected to a local [MockWebServer] through
 * [ModelCatalog.endpoint], the single testability seam on the singleton. The
 * production default is restored in [tearDown].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelCatalogFetchTest {

    private lateinit var server: MockWebServer
    private lateinit var originalEndpoint: String

    @Before
    fun setUp() {
        originalEndpoint = ModelCatalog.endpoint
        server = MockWebServer()
        server.start()
        ModelCatalog.endpoint = server.url("/api/v1/models").toString()
    }

    @After
    fun tearDown() {
        ModelCatalog.endpoint = originalEndpoint
        server.shutdown()
    }

    // ------------------------------------------------------------------
    // 1. Security regression: no credential on the catalog request.
    // ------------------------------------------------------------------

    @Test
    fun `fetch sends no authorization header`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validCatalog()))

        val result = runBlocking { ModelCatalog.fetch() }

        assertNotNull("a 200 with a valid catalog must parse", result)
        val request = server.takeRequest()
        assertNull(
            "the catalog request must not spend the user's credential",
            request.getHeader("Authorization")
        )
    }

    /** Guards against the header being reintroduced under a different casing. */
    @Test
    fun `fetch sends no credential under any header casing`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validCatalog()))

        runBlocking { ModelCatalog.fetch() }

        val request = server.takeRequest()
        val credentialish = request.headers.names().filter {
            it.equals("Authorization", ignoreCase = true) ||
                it.equals("Proxy-Authorization", ignoreCase = true) ||
                it.equals("X-Api-Key", ignoreCase = true)
        }
        assertTrue("no credential-bearing header may be present, found $credentialish", credentialish.isEmpty())
    }

    /**
     * The request must still identify the app to OpenRouter. Those headers are
     * not credentials, and dropping them would degrade OpenRouter's routing and
     * attribution.
     */
    @Test
    fun `fetch sends the app-identifying headers`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validCatalog()))

        runBlocking { ModelCatalog.fetch() }

        val request = server.takeRequest()
        assertEquals("WordWise", request.getHeader("X-Title"))
        assertNotNull("HTTP-Referer identifies the app", request.getHeader("HTTP-Referer"))
    }

    @Test
    fun `fetch requests the models path`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validCatalog()))

        runBlocking { ModelCatalog.fetch() }

        assertEquals("/api/v1/models", server.takeRequest().path)
    }

    // ------------------------------------------------------------------
    // 2. Failure modes must degrade, never throw.
    // ------------------------------------------------------------------

    @Test
    fun `non-2xx response yields null rather than throwing`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("upstream exploded"))
        assertNull(runBlocking { ModelCatalog.fetch() })

        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        assertNull(runBlocking { ModelCatalog.fetch() })
    }

    @Test
    fun `unauthorized response yields null`() {
        // Defence in depth: if an Authorization header is ever reintroduced and
        // OpenRouter rejects it, fetch must still fail softly.
        server.enqueue(MockResponse().setResponseCode(401).setBody("no"))
        assertNull(runBlocking { ModelCatalog.fetch() })
    }

    @Test
    fun `garbage body yields an empty list rather than throwing`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>not json</html>"))
        assertEquals(emptyList<ModelInfo>(), runBlocking { ModelCatalog.fetch() })
    }

    /**
     * An oversized response must be refused, not parsed.
     *
     * `MAX_BODY_BYTES` is 4 MiB. Sending just over it proves the guard runs
     * before the body is read into memory.
     */
    @Test
    fun `oversized response is refused rather than parsed`() {
        val tooBig = "x".repeat(4 * 1024 * 1024 + 1024)
        server.enqueue(MockResponse().setResponseCode(200).setBody(tooBig))
        assertNull(runBlocking { ModelCatalog.fetch() })
    }

    // ------------------------------------------------------------------
    // 3. Success path still filters and projects correctly.
    // ------------------------------------------------------------------

    @Test
    fun `valid catalog is fetched and filtered to text-output models`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validCatalog()))

        val models = runBlocking { ModelCatalog.fetch() }

        assertNotNull(models)
        val ids = models!!.map { it.id }
        assertTrue("text-output models must be kept, got $ids", ids.contains("openai/gpt-4o"))
        assertTrue("free text-output models must be kept, got $ids", ids.contains("vendor/free-model:free"))
        assertFalse("image-output models must be dropped, got $ids", ids.contains("stability/sdxl"))

        val gpt = models.first { it.id == "openai/gpt-4o" }
        assertEquals("GPT-4o", gpt.name)
        assertEquals(128000, gpt.contextLength)
        assertFalse(gpt.isFree)
        assertTrue(models.first { it.id == "vendor/free-model:free" }.isFree)
    }

    @Test
    fun `entry with no declared output modality is kept`() {
        // A schema change upstream must not silently empty the picker.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"vendor/model","name":"M"}]}""")
        )
        val models = runBlocking { ModelCatalog.fetch() }
        assertEquals(listOf("vendor/model"), models?.map { it.id })
    }

    /** A blank catalog ID must never become a selectable empty row. */
    @Test
    fun `blank catalog id is dropped`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"","name":"Blank"}]}""")
        )
        assertEquals(emptyList<ModelInfo>(), runBlocking { ModelCatalog.fetch() })
    }

    private fun validCatalog(): String = """
        {"data":[
          {"id":"openai/gpt-4o","name":"GPT-4o","context_length":128000,
           "architecture":{"output_modalities":["text"]},
           "pricing":{"prompt":"0.0000025","completion":"0.00001"}},
          {"id":"vendor/free-model:free","name":"Free One","context_length":8192,
           "architecture":{"output_modalities":["text"]},
           "pricing":{"prompt":"0","completion":"0"}},
          {"id":"stability/sdxl","name":"SDXL","context_length":0,
           "architecture":{"output_modalities":["image"]},
           "pricing":{"prompt":"0.001","completion":"0"}}
        ],"total_count":3}
    """.trimIndent()
}