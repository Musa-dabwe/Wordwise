package com.musa.wordwise.bridge

import com.musa.wordwise.network.ModelId
import com.musa.wordwise.server.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Every rule behind the JS-to-native trust boundary.
 *
 * These are plain JVM tests with fake stores, deliberately. The real
 * [KeyStore] is `EncryptedSharedPreferences`, which Robolectric cannot construct
 * at all (it ships no AndroidKeyStore shadow), so testing through the real
 * implementation would mean no coverage of the rules that matter — or, as before
 * this extraction, no coverage at all.
 *
 * The two properties that carry the security weight:
 * - the key is **write-only**: nothing returns key material
 * - rejection is **explicit**: a bad value is refused with a reason rather than
 *   silently downgraded
 */
class WwBridgeTest {

    private lateinit var keys: FakeKeyStore
    private lateinit var settings: FakeSettings
    private lateinit var bridge: WwBridge

    @Before
    fun setUp() {
        keys = FakeKeyStore()
        settings = FakeSettings()
        bridge = WwBridge(keys, settings)
    }

    // ------------------------------------------------------------------
    // The key is write-only.
    // ------------------------------------------------------------------

    @Test
    fun `no bridge method returns key material`() {
        bridge.saveApiKey("sk-or-v1-abcdefghijklmnop")
        // Every read-shaped method must be safe to expose to JS.
        assertFalse(bridge.getModel().contains("sk-or"))
        assertFalse(bridge.getTheme().contains("sk-or"))
        assertFalse("${bridge.hasApiKey()}".contains("sk-or"))
        assertFalse((bridge.setTheme(Themes.ALL.first().key) as BridgeResult.Ok).note.contains("sk-or"))
        assertFalse((bridge.setModel("vendor/model") as BridgeResult.Ok).note.contains("sk-or"))
        assertFalse((bridge.clearApiKey() as BridgeResult.Ok).note.contains("sk-or"))
    }

    @Test
    fun `hasApiKey is false before anything is saved`() {
        assertFalse(bridge.hasApiKey())
    }

    @Test
    fun `hasApiKey is true after a save`() {
        bridge.saveApiKey("sk-or-v1-abcdefghijklmnop")
        assertTrue(bridge.hasApiKey())
    }

    @Test
    fun `saving trims surrounding whitespace`() {
        bridge.saveApiKey("  sk-or-v1-abcdefghijklmnop \n")
        assertEquals("sk-or-v1-abcdefghijklmnop", keys.stored)
    }

    @Test
    fun `an empty or whitespace-only key is rejected`() {
        for (bad in listOf("", "   ", "\t\n")) {
            val result = bridge.saveApiKey(bad)
            assertTrue("'$bad' should be rejected", result is BridgeResult.Err)
            assertFalse("nothing may be stored for '$bad'", keys.hasKey())
        }
    }

    @Test
    fun `an over-long key is rejected and nothing is stored`() {
        val result = bridge.saveApiKey("k".repeat(201))
        assertTrue(result is BridgeResult.Err)
        assertEquals("That does not look like an OpenRouter key", (result as BridgeResult.Err).reason)
        assertFalse(keys.hasKey())
    }

    @Test
    fun `a key of exactly the maximum length is accepted`() {
        val result = bridge.saveApiKey("k".repeat(200))
        assertTrue(result is BridgeResult.Ok)
        assertEquals(200, keys.stored.length)
    }

    @Test
    fun `clearing removes the key`() {
        bridge.saveApiKey("sk-or-v1-abcdefghijklmnop")
        assertTrue(bridge.hasApiKey())
        assertTrue(bridge.clearApiKey() is BridgeResult.Ok)
        assertFalse(bridge.hasApiKey())
        assertEquals("", keys.stored)
    }

    @Test
    fun `clearing when no key exists is harmless`() {
        assertTrue(bridge.clearApiKey() is BridgeResult.Ok)
        assertFalse(bridge.hasApiKey())
    }

    @Test
    fun `a successful save returns an empty note`() {
        assertEquals("", (bridge.saveApiKey("sk-or-v1-abcdefghijklmnop") as BridgeResult.Ok).note)
    }

    // ------------------------------------------------------------------
    // Model validation is explicit, never a silent downgrade.
    // ------------------------------------------------------------------

    @Test
    fun `a valid model is stored`() {
        val result = bridge.setModel("anthropic/claude-3.5-sonnet")
        assertTrue(result is BridgeResult.Ok)
        assertEquals("anthropic/claude-3.5-sonnet", settings.model())
    }

    @Test
    fun `a blank model resets to the free router`() {
        bridge.setModel("anthropic/claude-3.5-sonnet")
        assertTrue(bridge.setModel("") is BridgeResult.Ok)
        assertEquals(ModelId.DEFAULT, settings.model())
    }

    @Test
    fun `a malformed model is rejected and the previous one survives`() {
        bridge.setModel("openai/gpt-4o")
        val result = bridge.setModel("not a model")
        assertTrue(result is BridgeResult.Err)
        assertTrue((result as BridgeResult.Err).reason.isNotBlank())
        // A rejected save must not silently downgrade to the free router.
        assertEquals("openai/gpt-4o", settings.model())
    }

    @Test
    fun `every malformed shape is rejected with a reason`() {
        for (bad in listOf("gpt-4o", "vendor/group/model", "a b/c", "/leading", "trailing/")) {
            val result = bridge.setModel(bad)
            assertTrue("'$bad' should be rejected", result is BridgeResult.Err)
            assertTrue(
                "'$bad' must come with a user-facing reason",
                (result as BridgeResult.Err).reason.isNotBlank()
            )
        }
    }

    @Test
    fun `getModel returns what was stored`() {
        assertEquals(ModelId.DEFAULT, bridge.getModel())
        bridge.setModel("vendor/model")
        assertEquals("vendor/model", bridge.getModel())
    }

    // ------------------------------------------------------------------
    // Theme validation, plus the status bar colour the UI must apply.
    // ------------------------------------------------------------------

    @Test
    fun `a known theme is stored and reports its status bar colour`() {
        val target = Themes.ALL.first()
        val result = bridge.setTheme(target.key)
        assertTrue(result is BridgeResult.Ok)
        assertEquals(target.statusBar, (result as BridgeResult.Ok).statusBarColor)
        assertEquals(target.key, settings.theme())
    }

    @Test
    fun `every advertised theme key is accepted`() {
        for (theme in Themes.ALL) {
            val result = bridge.setTheme(theme.key)
            assertTrue("${theme.key} should be accepted", result is BridgeResult.Ok)
        }
    }

    @Test
    fun `an unknown theme is rejected and nothing changes`() {
        bridge.setTheme(Themes.ALL.first().key)
        val before = settings.theme()
        val result = bridge.setTheme("no-such-theme")
        assertEquals("Unknown theme", (result as BridgeResult.Err).reason)
        assertEquals(before, settings.theme())
    }

    /**
 * A rejected theme must not leave the caller with a colour to apply.
 *
 * [BridgeResult.Err] cannot carry one — the field only exists on `Ok` — so this
 * asserts the shape the UI depends on rather than a value.
 */
@Test
    fun `a rejected theme carries no status bar colour`() {
        val result = bridge.setTheme("no-such-theme")
        assertTrue("expected a rejection, got $result", result is BridgeResult.Err)
        assertTrue(
            "Err must not expose a statusBarColor the UI could apply",
            result !is BridgeResult.Ok
        )
    }

    @Test
    fun `getTheme returns what was stored`() {
        assertEquals("peach", bridge.getTheme())
        val target = Themes.ALL.last()
        bridge.setTheme(target.key)
        assertEquals(target.key, bridge.getTheme())
    }

    // ------------------------------------------------------------------

    private class FakeKeyStore : KeyStore {
        var stored: String = ""
        override fun hasKey(): Boolean = stored.isNotBlank()
        override fun saveKey(key: String) { stored = key }
        override fun clearKey() { stored = "" }
    }

    private class FakeSettings : AppSettings {
        private var modelValue: String = ModelId.DEFAULT
        private var themeValue: String = "peach"
        override fun model(): String = modelValue
        override fun setModel(validated: ModelId.Result.Valid) { modelValue = validated.modelId }
        override fun theme(): String = themeValue
        override fun setTheme(key: String) { themeValue = key }
    }
}