package com.musa.wordwise.server

import com.musa.wordwise.network.ModelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Regression guards for the server-rendered screens in [Views].
 *
 * The key-leak vulnerability these guard: `homeScreen` used to take the
 * OpenRouter API key as a parameter and render it into the HTML
 * (`value="${esc(key)}"`). The Ktor port is a shared loopback socket, so any
 * installed app could read that HTML and steal the key. The key is now
 * write-only: it is not a parameter at all, the input renders empty, and its
 * state is supplied client-side through the WebView bridge. Every part of that
 * property is locked down here.
 */
class ViewsTest {

    /**
     * The shipped client-side script.
     *
     * Read from the asset the APK actually bundles, rather than scraped out of
     * `Shell.page`. It used to be inlined in a Kotlin raw string, which is what
     * this extraction removed: the script is now a real file that can be read
     * directly and syntax-checked on its own.
     */
    private val script: String by lazy {
        val candidates = listOf(
            File("src/main/assets/web/wordwise.js"),
            File("app/src/main/assets/web/wordwise.js")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("wordwise.js not found; tried $candidates")
        file.readText()
    }

    // ---- 1. the key is not a parameter, and no key material is rendered ----

    /**
     * Vulnerability: the key used to be a `homeScreen` parameter rendered into
     * the HTML, readable by any app on the shared loopback namespace. The
     * signature must stay (boolean, String, String) — no Context, no key.
     */
    @Test
    fun `homeScreen signature takes no key parameter`() {
        val methods = Views::class.java.methods.filter { it.name == "homeScreen" }
        assertEquals("homeScreen must be uniquely defined", 1, methods.size)
        val params = methods.single().parameters
        assertEquals(3, params.size)
        assertEquals(Boolean::class.javaPrimitiveType, params[0].type)
        assertEquals(String::class.java, params[1].type)
        assertEquals(String::class.java, params[2].type)
    }

    /**
     * Vulnerability: no OpenRouter key material (`sk-or-...`) may appear anywhere
     * in the rendered settings screen, whatever the inputs.
     */
    @Test
    fun `home screen renders no api key material`() {
        val html = Views.homeScreen(true, ModelId.DEFAULT, "peach")
        assertFalse("key material must never reach the HTML", html.contains("sk-or-"))
        assertFalse("no value attribute may carry a key", html.contains("value=\"sk-"))
    }

    // ---- 2. the key input renders empty ----

    /**
     * The key input must render with no `value` attribute at all: the field
     * always starts empty and the stored key is never rendered back.
     */
    @Test
    fun `key input renders empty`() {
        val html = Views.homeScreen(true, ModelId.DEFAULT, "peach")
        val tag = Regex("""<input id="key-input"[^>]*>""").find(html)
            if (tag == null) {
                fail("key-input element missing")
                return
            }
        assertFalse("key input must not carry a value", tag.value.contains("value="))
        assertTrue("key input must stay a password field", tag.value.contains("type=\"password\""))
    }

    // ---- 3. the key-state note renders empty ----

    /**
     * `#key-state` must render empty; its text is supplied client-side by
     * `wwRefreshKeyState()` through the WebView bridge, never by the server.
     */
    @Test
    fun `key-state note renders empty and is filled client-side`() {
        val html = Views.homeScreen(true, ModelId.DEFAULT, "peach")
        assertTrue(
            "key-state must render as an empty element",
            Regex("""<div id="key-state"[^>]*></div>""").containsMatchIn(html)
        )
        assertTrue(
            "the shell script must define wwRefreshKeyState",
            script.contains("function wwRefreshKeyState(")
        )
        assertTrue(
            "the afterSwap wiring must refresh the key state on screen load",
            script.contains("wwRefreshKeyState()")
        )
    }

    // ---- 4. the remove-key control exists ----

    /**
     * The user must be able to delete the stored key (previously clearing app
     * data was the only option). The control exists, is hidden by default, and
     * is revealed client-side by `wwRefreshKeyState()`.
     */
    @Test
    fun `remove-key control exists and is hidden by default`() {
        val html = Views.homeScreen(true, ModelId.DEFAULT, "peach")
        val tag = Regex("""<button id="key-remove"[^>]*>""").find(html)
            if (tag == null) {
                fail("key-remove control missing")
                return
            }
        assertTrue("key-remove must be hidden initially", tag.value.contains("display:none"))
        assertTrue(
            "key-remove must be wired to wwRemoveKey",
            tag.value.contains("onclick=\"wwRemoveKey()\"")
        )
        assertTrue("the shell script must define wwRemoveKey", script.contains("function wwRemoveKey("))
        assertTrue("the shell script must reveal key-remove client-side", script.contains("key-remove"))
    }

    // ---- 5. escaping still works for the values that ARE rendered ----

    /**
     * A hostile model id must be escaped by `esc()` before interpolation, so a
     * catalog or preference value cannot inject markup into the page.
     */
    @Test
    fun `hostile model id is escaped not injected`() {
        val hostile = """"><script>alert(1)</script>"""
        val html = Views.homeScreen(true, hostile, "peach")
        assertFalse("no raw script tag may reach the page", html.contains("<script>"))
        assertFalse("the hostile prefix must not appear raw", html.contains(hostile))
        assertTrue(
            "the escaped payload must be present",
            html.contains("&lt;script&gt;alert(1)&lt;/script&gt;")
        )
        assertTrue("quotes must be escaped too", html.contains("&quot;"))
    }

    /**
     * A theme key outside `Themes.KEYS` must fall back to a safe built-in
     * theme rather than throwing or rendering the raw unknown key.
     */
    @Test
    fun `unknown theme key falls back to a safe theme`() {
        val unknown = "no-such-theme"
        assertFalse("precondition: the key is really unknown", Themes.KEYS.contains(unknown))
        val html = Views.homeScreen(true, ModelId.DEFAULT, unknown)
        assertFalse("the unknown key must not be rendered", html.contains(unknown))
        assertTrue("the fallback theme name must be shown", html.contains("Soft Peach"))
        assertTrue("the fallback theme must be selectable", html.contains("data-k=\"peach\""))
    }

    // ---- 6. the model picker ----

    /**
     * The dropdown label (`#model-label`) renders the current model id and
     * spells out the free router for [ModelId.DEFAULT]. `#model-input` itself
     * is filled client-side by `wwSetModelField()` on `htmx:load` (the same
     * write-only pattern as the key field), so it renders empty.
     */
    @Test
    fun `model picker renders the current model and spells out the free router`() {
        val html = Views.homeScreen(true, ModelId.DEFAULT, "peach")
        assertTrue("free router must be spelled out", html.contains("Free Models Router"))
        assertTrue(
            "the default model id must label the dropdown",
            html.contains("""id="model-label" class="val">openrouter/free""")
        )

        val custom = Views.homeScreen(true, "anthropic/claude-3.5-sonnet", "peach")
        assertTrue(
            "a custom model id must label the dropdown",
            custom.contains("""id="model-label" class="val">anthropic/claude-3.5-sonnet""")
        )

        val input = Regex("""<input id="model-input"[^>]*>""").find(html)
            if (input == null) {
                fail("model-input element missing")
                return
            }
        assertFalse("model-input must render empty", input.value.contains("value="))
        assertTrue(
            "the shell must fill model-input via wwSetModelField",
            script.contains("function wwSetModelField(")
        )
    }

    // ---- 7. the service-enabled flag ----

    /**
     * The service-enabled flag renders both states: the status pill text and
     * the button label must agree with the flag.
     */
    @Test
    fun `service flag renders both states`() {
        val on = Views.homeScreen(true, ModelId.DEFAULT, "peach")
        assertTrue(on.contains("SERVICE ACTIVE"))
        assertTrue(on.contains("Enabled ✓"))
        assertTrue(on.contains("""class="status-dot on""""))
        assertFalse(on.contains("SERVICE PAUSED"))

        val off = Views.homeScreen(false, ModelId.DEFAULT, "peach")
        assertTrue(off.contains("SERVICE PAUSED"))
        assertTrue(off.contains(">Enable<"))
        assertTrue(off.contains("""class="status-dot""""))
        assertFalse(off.contains("status-dot on"))
        assertFalse(off.contains("SERVICE ACTIVE"))
    }

    // ---- 8. the about screen ----

    /**
     * The About screen is static markdown: it must render with no key material
     * and no input controls at all.
     */
    @Test
    fun `about screen renders with no key material`() {
        val html = Views.aboutScreen()
        assertTrue(html.contains("WordWise"))
        assertTrue(html.contains("Security"))
        assertFalse("no key material on the about screen", html.contains("sk-or-"))
        assertFalse("the about screen has no inputs", html.contains("<input"))
    }

    // ---- 9. every inline handler exists in the shell script ----

    /**
     * Every inline `onclick`/`oninput` handler referenced by the settings screen
     * must be defined in the shell script. A broken reference is a silent dead
     * button: the HTML renders, the tap does nothing.
     */
    @Test
    fun `every inline handler in the home screen exists in the shell script`() {
        val html = Views.homeScreen(true, ModelId.DEFAULT, "peach")
        val handlers = Regex("""on(?:click|input)="(\w+)\(""")
            .findAll(html)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("the home screen must reference inline handlers", handlers.isNotEmpty())
        val missing = handlers.filterNot { script.contains("function $it(") }
        assertEquals(
            "handlers missing from the shell script: $missing",
            emptyList<String>(),
            missing
        )
    }
}
