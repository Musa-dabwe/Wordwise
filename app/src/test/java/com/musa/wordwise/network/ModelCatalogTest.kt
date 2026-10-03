package com.musa.wordwise.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the four fields `Shell.kt` reads off `/api/models`. */
private data class WireModel(val id: String, val name: String, val ctx: Int, val free: Boolean)

private fun decodeWire(json: String): List<WireModel> =
    Json.parseToJsonElement(json).jsonArray.map { element ->
        val o = element.jsonObject
        WireModel(
            id = o["id"]!!.jsonPrimitive.content,
            name = o["name"]!!.jsonPrimitive.content,
            ctx = o["ctx"]!!.jsonPrimitive.int,
            free = o["free"]!!.jsonPrimitive.boolean
        )
    }

class ModelCatalogTest {

    private fun entry(
        id: String = "openai/gpt-4o",
        name: String = "GPT-4o",
        ctx: Int = 128000,
        output: String = """["text"]""",
        prompt: String = "\"0.0000025\"",
        completion: String = "\"0.00001\""
    ) = """
        {"id":"$id","name":"$name","context_length":$ctx,
         "architecture":{"output_modalities":$output},
         "pricing":{"prompt":$prompt,"completion":$completion}}
    """.trimIndent()

    private fun catalog(vararg entries: String) =
        """{"data":[${entries.joinToString(",")}],"total_count":${entries.size}}"""

    @Test
    fun `parses a well-formed entry`() {
        val models = ModelCatalog.parse(catalog(entry()))
        assertEquals(1, models.size)
        val m = models.first()
        assertEquals("openai/gpt-4o", m.id)
        assertEquals("GPT-4o", m.name)
        assertEquals(128000, m.contextLength)
        assertFalse(m.isFree)
    }

    @Test
    fun `flags zero pricing as free`() {
        val models = ModelCatalog.parse(
            catalog(entry(prompt = "\"0\"", completion = "\"0\""))
        )
        assertTrue(models.first().isFree)
    }

    @Test
    fun `a one-sided zero price is not free`() {
        // Free on input but not output still costs money.
        val models = ModelCatalog.parse(
            catalog(entry(prompt = "\"0\"", completion = "\"0.00001\""))
        )
        assertFalse(models.first().isFree)
    }

    @Test
    fun `drops models that cannot output text`() {
        val models = ModelCatalog.parse(
            catalog(entry(id = "stability/sdxl", output = """["image"]"""))
        )
        assertTrue(models.isEmpty())
    }

    @Test
    fun `keeps a model whose output includes text alongside image`() {
        val models = ModelCatalog.parse(
            catalog(entry(output = """["text","image"]"""))
        )
        assertEquals(1, models.size)
    }

    @Test
    fun `keeps a model with no declared output modality`() {
        // Defends the picker against an upstream schema change emptying it.
        val json = """{"data":[{"id":"vendor/model","name":"M"}]}"""
        val models = ModelCatalog.parse(json)
        assertEquals(1, models.size)
        assertEquals("vendor/model", models.first().id)
    }

    @Test
    fun `falls back to the id when name is missing`() {
        val json = """{"data":[{"id":"vendor/model"}]}"""
        assertEquals("vendor/model", ModelCatalog.parse(json).first().name)
    }

    @Test
    fun `drops an entry whose id is not vendor slash model`() {
        val models = ModelCatalog.parse(catalog(entry(id = "gpt-4o")))
        assertTrue(models.isEmpty())
    }

    @Test
    fun `drops entries without an id`() {
        assertTrue(ModelCatalog.parse("""{"data":[{"name":"nameless"}]}""").isEmpty())
    }

    /** A blank ID is Valid-as-input ("reset to default") but must never be a row. */
    @Test
    fun `drops an entry with a blank id`() {
        assertTrue(ModelCatalog.parse("""{"data":[{"id":"","name":"Blank"}]}""").isEmpty())
        assertTrue(ModelCatalog.parse("""{"data":[{"id":"   ","name":"Blank"}]}""").isEmpty())
    }

    @Test
    fun `trims a padded id`() {
        val models = ModelCatalog.parse(catalog(entry(id = "  openai/gpt-4o  ")))
        assertEquals("openai/gpt-4o", models.single().id)
    }

    @Test
    fun `caps an absurdly long name`() {
        val models = ModelCatalog.parse(catalog(entry(name = "N".repeat(5000))))
        assert(models.single().name.length <= 160)
    }

    @Test
    fun `clamps a negative context length`() {
        val models = ModelCatalog.parse(catalog(entry(ctx = -5)))
        assertEquals(0, models.single().contextLength)
    }

    @Test
    fun `keeps a valid entry when a sibling is malformed`() {
        val models = ModelCatalog.parse(
            catalog(entry(id = "openai/gpt-4o"), entry(id = "bad id with spaces"))
        )
        assertEquals(1, models.size)
        assertEquals("openai/gpt-4o", models.first().id)
    }

    @Test
    fun `returns empty for malformed json`() {
        assertTrue(ModelCatalog.parse("""{"data":[""").isEmpty())
    }

    @Test
    fun `returns empty for a missing data array`() {
        assertTrue(ModelCatalog.parse("""{"error":"nope"}""").isEmpty())
    }

    @Test
    fun `returns empty for garbage`() {
        assertTrue(ModelCatalog.parse("not json at all").isEmpty())
    }

    /**
     * `toJson` is the server-to-WebView wire format (`ctx`/`free`, bare array);
     * `parse` consumes OpenRouter's upstream envelope (`context_length`/`pricing`).
     * The two schemas deliberately differ, so the wire output is verified against
     * the fields the frontend actually reads rather than round-tripped.
     */
    @Test
    fun `toJson emits the fields the frontend reads`() {
        val original = listOf(
            ModelInfo("openai/gpt-4o", "GPT-4o", 128000, false),
            ModelInfo("vendor/free:free", "Free \"Model\"", 0, true)
        )
        assertEquals(
            listOf(
                WireModel("openai/gpt-4o", "GPT-4o", 128000, false),
                WireModel("vendor/free:free", "Free \"Model\"", 0, true)
            ),
            decodeWire(ModelCatalog.toJson(original))
        )
    }

    @Test
    fun `toJson escapes a name containing quotes`() {
        val json = ModelCatalog.toJson(listOf(ModelInfo("a/b", "Say \"hi\"", 1, true)))
        assertEquals("Say \"hi\"", decodeWire(json).first().name)
    }

    @Test
    fun `toJson escapes a name containing a backslash`() {
        val json = ModelCatalog.toJson(listOf(ModelInfo("a/b", "back\\slash", 1, false)))
        assertEquals("back\\slash", decodeWire(json).first().name)
    }

    @Test
    fun `toJson emits a bare array for the models route`() {
        val json = ModelCatalog.toJson(listOf(ModelInfo("a/b", "M", 1, true)))
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
    }

    @Test
    fun `toJson preserves order`() {
        val ids = listOf("z/b", "a/a", "m/c").map { ModelInfo(it, it, 1, false) }
        assertEquals(ids.map { it.id }, decodeWire(ModelCatalog.toJson(ids)).map { it.id })
    }

    @Test
    fun `toJson of an empty list is an empty array`() {
        assertEquals("[]", ModelCatalog.toJson(emptyList()))
    }
}