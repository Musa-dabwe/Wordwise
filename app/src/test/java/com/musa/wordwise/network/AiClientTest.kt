package com.musa.wordwise.network

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AiClientTest {

    @Test
    fun `DEFAULT_MODEL is openrouter_free`() {
        assertEquals("openrouter/free", AiClient.DEFAULT_MODEL)
        assertEquals(ModelId.DEFAULT, AiClient.DEFAULT_MODEL)
    }

    @Test
    fun `parseContent extracts text from valid JSON`() {
        val json = """{"choices":[{"message":{"content":"  Corrected text  "}}]}"""
        val result = AiClient.parseContent(json)
        assertEquals("Corrected text", result)
    }

    @Test
    fun `parseContent returns null for empty choices`() {
        val json = """{"choices":[]}"""
        val result = AiClient.parseContent(json)
        assertEquals(null, result)
    }

    @Test
    fun `parseContent returns null for malformed JSON`() {
        val json = """{"choices":[{"message":""""
        val result = AiClient.parseContent(json)
        assertEquals(null, result)
    }

    @Test
    fun `parseContent returns null for garbage JSON`() {
        val result = AiClient.parseContent("not json at all")
        assertEquals(null, result)
    }

    @Test
    fun `parseContent strips wrapping double quotes`() {
        val json = """{"choices":[{"message":{"content":"\"wrapped\""}}]}"""
        val result = AiClient.parseContent(json)
        assertEquals("wrapped", result)
    }

    @Test
    fun `ASK_SYSTEM_PROMPT contains plain text constraint`() {
        val field = AiClient::class.java.getDeclaredField("ASK_SYSTEM_PROMPT")
        field.isAccessible = true
        val prompt = field.get(null) as String
        assert(prompt.contains("plain text"))
        assert(prompt.contains("Markdown"))
    }

    @Test
    fun `ASK_SYSTEM_PROMPT requests no commentary`() {
        val field = AiClient::class.java.getDeclaredField("ASK_SYSTEM_PROMPT")
        field.isAccessible = true
        val prompt = field.get(null) as String
        assert(prompt.contains("no commentary"))
    }

    /** Both prompts forbid em-dashes: the result is inserted into a text field. */
    @Test
    fun `ASK_SYSTEM_PROMPT forbids em-dashes`() {
        val field = AiClient::class.java.getDeclaredField("ASK_SYSTEM_PROMPT")
        field.isAccessible = true
        val prompt = field.get(null) as String
        assert(prompt.contains("Never use em-dashes"))
        assert(prompt.contains("use a comma, colon, semicolon, or restructure"))
    }

    @Test
    fun `ASK_SYSTEM_PROMPT avoids clause-initial conjunctions`() {
        val field = AiClient::class.java.getDeclaredField("ASK_SYSTEM_PROMPT")
        field.isAccessible = true
        val prompt = field.get(null) as String
        assert(prompt.contains("conjunction"))
        assert(prompt.contains("'But', 'And', or 'So'"))
    }

    @Test
    fun `ASK_SYSTEM_PROMPT asks for concise sentences`() {
        val field = AiClient::class.java.getDeclaredField("ASK_SYSTEM_PROMPT")
        field.isAccessible = true
        val prompt = field.get(null) as String
        assert(prompt.contains("concise"))
    }

    @Test
    fun `GRAMMAR_SYSTEM_PROMPT forbids em-dashes`() {
        val field = AiClient::class.java.getDeclaredField("GRAMMAR_SYSTEM_PROMPT")
        field.isAccessible = true
        val prompt = field.get(null) as String
        assert(prompt.contains("Never use em-dashes"))
    }

    // ---- payload: the chosen model must actually reach OpenRouter ----
    // These guard against a regression where `put("model", ...)` reverts to a
    // hardcoded constant while every other test still passes.

    @Test
    fun `fixPayload carries the chosen model`() {
        assertEquals("openai/gpt-4o", AiClient.fixPayload("hi", "openai/gpt-4o")["model"]?.jsonPrimitive?.content)
    }

    @Test
    fun `askPayload carries the chosen model`() {
        assertEquals("anthropic/claude-3.5-sonnet", AiClient.askPayload("why?", "anthropic/claude-3.5-sonnet")["model"]?.jsonPrimitive?.content)
    }

    @Test
    fun `payload falls back to the default for a blank model`() {
        assertEquals(ModelId.DEFAULT, AiClient.fixPayload("hi", "")["model"]?.jsonPrimitive?.content)
        assertEquals(ModelId.DEFAULT, AiClient.askPayload("why?", "  ")["model"]?.jsonPrimitive?.content)
    }

    @Test
    fun `payload falls back to the default for a malformed model`() {
        assertEquals(ModelId.DEFAULT, AiClient.fixPayload("hi", "not a model")["model"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fixPayload sends the user text as the last message`() {
        val messages = AiClient.fixPayload("hello there", "openai/gpt-4o")["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("hello there", messages[1].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun `askPayload sends the prompt as the last message`() {
        val messages = AiClient.askPayload("why is the sky blue", "openai/gpt-4o")["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("why is the sky blue", messages[1].jsonObject["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun `payload model cannot break out of the json string`() {
        // Even if validation were bypassed, a quote in the model must stay
        // inside the JSON string value.
        val json = AiClient.fixPayload("hi", """a/b" , "injected":"yes""").toString()
        assert(!json.contains("\"injected\""))
    }
}
