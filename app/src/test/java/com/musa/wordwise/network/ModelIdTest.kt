package com.musa.wordwise.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelIdTest {

    private fun validId(raw: String): String? =
        (ModelId.validate(raw) as? ModelId.Result.Valid)?.modelId

    private fun rejectReason(raw: String): String? =
        (ModelId.validate(raw) as? ModelId.Result.Invalid)?.reason

    @Test
    fun `DEFAULT is the free router`() {
        assertEquals("openrouter/free", ModelId.DEFAULT)
    }

    @Test
    fun `accepts a vendor slash model id`() {
        assertEquals("anthropic/claude-3.5-sonnet", validId("anthropic/claude-3.5-sonnet"))
    }

    @Test
    fun `accepts a colon variant suffix`() {
        assertEquals(
            "meta-llama/llama-3.3-70b-instruct:free",
            validId("meta-llama/llama-3.3-70b-instruct:free")
        )
    }

    @Test
    fun `accepts a tilde latest-version alias`() {
        // 18 real catalog ids look like ~openai/gpt-astra-latest.
        assertEquals("~openai/gpt-astra-latest", validId("~openai/gpt-astra-latest"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("openai/gpt-4o", validId("  openai/gpt-4o\n"))
    }

    @Test
    fun `blank resolves to the free router`() {
        assertEquals(ModelId.DEFAULT, validId(""))
        assertEquals(ModelId.DEFAULT, validId("   "))
    }

    @Test
    fun `rejects an id with no slash`() {
        assert(rejectReason("gpt-4o") != null)
    }

    @Test
    fun `rejects an id with two slashes`() {
        assert(rejectReason("vendor/group/model") != null)
    }

    @Test
    fun `rejects a leading slash`() {
        assert(rejectReason("/gpt-4o") != null)
    }

    @Test
    fun `rejects a trailing slash`() {
        assert(rejectReason("openai/") != null)
    }

    @Test
    fun `rejects internal whitespace`() {
        assert(rejectReason("open ai/gpt-4o") != null)
    }

    @Test
    fun `rejects an over-long id`() {
        val long = "openai/" + "a".repeat(ModelId.MAX_LENGTH)
        assert(rejectReason(long) != null)
    }

    @Test
    fun `rejects a disallowed character`() {
        assert(rejectReason("openai/gpt?4o") != null)
    }

    @Test
    fun `resolve returns a valid id unchanged`() {
        assertEquals("openai/gpt-4o", ModelId.resolve("openai/gpt-4o"))
    }

    @Test
    fun `resolve falls back to default for malformed stored value`() {
        assertEquals(ModelId.DEFAULT, ModelId.resolve("not a model"))
        assertEquals(ModelId.DEFAULT, ModelId.resolve("gpt-4o"))
    }

    @Test
    fun `resolve treats blank as default`() {
        assertEquals(ModelId.DEFAULT, ModelId.resolve(""))
    }
}