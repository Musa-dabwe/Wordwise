package com.musa.wordwise

import org.junit.Assert.*
import org.junit.Test

class GrammarFixServiceTest {

    // --- Regex matching ---

    private val fixRegex = Regex("""\?fix\s*$""")
    private val askRegex = Regex("""\?ask\s*$""", RegexOption.IGNORE_CASE)

    @Test
    fun `fixRegex matches basic fix trigger`() {
        assertTrue(fixRegex.containsMatchIn("hello?fix"))
    }

    @Test
    fun `fixRegex matches fix with trailing whitespace`() {
        assertTrue(fixRegex.containsMatchIn("hello?fix "))
    }

    @Test
    fun `fixRegex does not match ask trigger`() {
        assertFalse(fixRegex.containsMatchIn("hello?ask"))
    }

    @Test
    fun `askRegex matches basic ask trigger`() {
        assertTrue(askRegex.containsMatchIn("hello?ask"))
    }

    @Test
    fun `askRegex matches ask with trailing whitespace`() {
        assertTrue(askRegex.containsMatchIn("hello?ask "))
    }

    @Test
    fun `askRegex matches ask with trailing newline`() {
        assertTrue(askRegex.containsMatchIn("hello?ask\n"))
    }

    @Test
    fun `askRegex is case insensitive`() {
        assertTrue(askRegex.containsMatchIn("hello?Ask"))
        assertTrue(askRegex.containsMatchIn("hello?ASK"))
        assertTrue(askRegex.containsMatchIn("hello?aSk"))
    }

    @Test
    fun `askRegex does not match fix trigger`() {
        assertFalse(askRegex.containsMatchIn("hello?fix"))
    }

    @Test
    fun `askRegex does not match ask in middle of string`() {
        assertFalse(askRegex.containsMatchIn("hello?ask world"))
    }

    // --- Prompt extraction ---

    @Test
    fun `extract fix prompt strips trigger`() {
        val text = "hello world?fix"
        val prompt = text.replace(fixRegex, "").trim()
        assertEquals("hello world", prompt)
    }

    @Test
    fun `extract ask prompt strips trigger`() {
        val text = "write the anthem?ask"
        val prompt = text.replace(askRegex, "").trim()
        assertEquals("write the anthem", prompt)
    }

    @Test
    fun `extract ask prompt strips trigger with trailing space`() {
        val text = "write the anthem?ask "
        val prompt = text.replace(askRegex, "").trim()
        assertEquals("write the anthem", prompt)
    }

    @Test
    fun `extract ask prompt preserves internal whitespace`() {
        val text = "write the national anthem for me?ask"
        val prompt = text.replace(askRegex, "").trim()
        assertEquals("write the national anthem for me", prompt)
    }

    // --- Word count ---

    private fun countWords(text: String): Int =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

    @Test
    fun `word count of empty string is zero`() {
        assertEquals(0, countWords(""))
    }

    @Test
    fun `word count of single word is one`() {
        assertEquals(1, countWords("hello"))
    }

    @Test
    fun `word count handles multiple spaces`() {
        assertEquals(3, countWords("hello   world   foo"))
    }

    @Test
    fun `word count handles leading trailing spaces`() {
        assertEquals(2, countWords("  hello world  "))
    }

    @Test
    fun `word count handles punctuation`() {
        assertEquals(4, countWords("hello, world. foo! bar?"))
    }

    @Test
    fun `word count handles non-Latin text`() {
        assertEquals(3, countWords("你好 世界 早上"))
    }

    @Test
    fun `word count of long prompt exceeds threshold`() {
        val words = List(10_001) { "word" }.joinToString(" ")
        assertTrue(countWords(words) > 10_000)
    }
}
