package com.musa.wordwise.server

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTest {

    @Test
    fun `esc escapes the html significant characters`() {
        assertEquals("&lt;&gt;&amp;&quot;&#39;", esc("<>&\"'"))
    }

    /** Escaping & first would double-encode, so the order matters. */
    @Test
    fun `esc does not double encode an already escaped entity`() {
        assertEquals("&amp;amp;", esc("&amp;"))
    }

    @Test
    fun `esc leaves ordinary text alone`() {
        assertEquals("openrouter/free", esc("openrouter/free"))
    }

    @Test
    fun `jsonStr quotes and escapes a plain value`() {
        assertEquals("\"openrouter/free\"", jsonStr("openrouter/free"))
    }

    @Test
    fun `jsonStr escapes a quote and a backslash`() {
        assertEquals("\"say \\\"hi\\\"\"", jsonStr("say \"hi\""))
        assertEquals("\"back\\\\slash\"", jsonStr("back\\slash"))
    }

    @Test
    fun `jsonStr escapes control characters`() {
        assertEquals("\"a\\nb\"", jsonStr("a\nb"))
        assertEquals("\"a\\tb\"", jsonStr("a\tb"))
        assertEquals("\"\\u0000\"", jsonStr("\u0000"))
    }

    /**
     * A value interpolated into an inline <script> block must not be able to
     * close it. Preferences are editable outside the app (backup restore, adb),
     * so this cannot rely on the validator that normally rejects such input.
     */
    @Test
    fun `jsonStr cannot break out of an inline script block`() {
        val out = jsonStr("</script><img src=x onerror=alert(1)>")
        assertFalse(out.contains("<"))
        assertFalse(out.contains(">"))
        assertTrue(out.contains("\\u003c"))
    }

    @Test
    fun `jsonStr escapes ampersand`() {
        assertEquals("\"\\u0026\"", jsonStr("&"))
    }

    /** The escaped output must still decode back to the original string. */
    @Test
    fun `jsonStr output round trips through a real json parser`() {
        val nasty = "</script>&\"'\\ \n\t\u0001 ~ok"
        val encoded = jsonStr(nasty)
        val decoded = kotlinx.serialization.json.Json.parseToJsonElement(encoded).jsonPrimitive.content
        assertEquals(nasty, decoded)
    }
}
