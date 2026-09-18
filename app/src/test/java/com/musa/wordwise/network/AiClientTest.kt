package com.musa.wordwise.network

import org.junit.Assert.assertEquals
import org.junit.Test

class AiClientTest {

    @Test
    fun `MODEL is openrouter_free`() {
        assertEquals("openrouter/free", AiClient.MODEL)
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
}
