package com.musa.wordwise.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.musa.wordwise.network.ModelId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearPrefs() {
        // Prefs is an object using a fixed prefs name, so state persists inside
        // this classloader across tests — reset it to keep tests order-independent.
        context.getSharedPreferences("wordwise_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun clearPrefsAfter() {
        context.getSharedPreferences("wordwise_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // --- Model storage ---

    @Test
    fun unsetModel_resolvesToDefault() {
        // Regression guard: a fresh install must not hand the API client a blank
        // or null model — ModelId.DEFAULT is the agreed fallback.
        assertEquals(ModelId.DEFAULT, Prefs.getModel(context))
        assertEquals("openrouter/free", Prefs.getModel(context))
    }

    @Test
    fun setModel_thenGetModel_roundTrips() {
        val valid = ModelId.validate("anthropic/claude-3.5-sonnet") as ModelId.Result.Valid
        Prefs.setModel(context, valid)
        assertEquals("anthropic/claude-3.5-sonnet", Prefs.getModel(context))
    }

    @Test
    fun setModel_toDefault_storesEmptyAndStillResolvesToDefault() {
        // Deliberate storage of "" for the default (keeps the prefs value canonical).
        // Protects the documented behaviour: reading back after choosing "free"
        // must still yield ModelId.DEFAULT, never the raw empty string.
        val valid = ModelId.validate(ModelId.DEFAULT) as ModelId.Result.Valid
        Prefs.setModel(context, valid)
        val raw = context.getSharedPreferences("wordwise_prefs", Context.MODE_PRIVATE)
            .getString("selected_model", "missing")
        assertEquals("", raw)
        assertEquals(ModelId.DEFAULT, Prefs.getModel(context))
    }

    @Test
    fun malformedStoredModel_isCorrectedToDefault() {
        // Defence in depth: a value written by a backup restore or `adb` edit
        // bypasses setModel's type safety. getModel must NEVER hand such a
        // string to the API client — it must fall back to the default.
        context.getSharedPreferences("wordwise_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_model", "not a model at all!!").commit()
        assertEquals(ModelId.DEFAULT, Prefs.getModel(context))
        assertNotEquals("not a model at all!!", Prefs.getModel(context))
    }

    @Test
    fun validButUnfamiliarModel_isPreservedVerbatim() {
        // validate() is format-only, not an existence check — otherwise a new
        // OpenRouter model would be rejected until the app ships an update.
        val valid = ModelId.validate("openai/gpt-9000-turbo") as ModelId.Result.Valid
        Prefs.setModel(context, valid)
        assertEquals("openai/gpt-9000-turbo", Prefs.getModel(context))
    }

    // --- Theme ---

    @Test
    fun unsetTheme_returnsDefault_andThemeRoundTrips() {
        assertEquals(Prefs.DEFAULT_THEME, Prefs.getTheme(context))
        Prefs.setTheme(context, "midnight")
        assertEquals("midnight", Prefs.getTheme(context))
    }

    @Test
    fun unknownThemeValue_isReturnedVerbatim() {
        // Unlike getModel, getTheme performs no validation — document the
        // asymmetry deliberately rather than discovering it in production.
        context.getSharedPreferences("wordwise_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_theme", "ultraviolet-dx").commit()
        assertEquals("ultraviolet-dx", Prefs.getTheme(context))
    }

    @Test
    fun modelAndTheme_areIndependent() {
        // Regression guard: storing the model must not clobber the theme and
        // vice versa — the two keys live in the same prefs file.
        val valid = ModelId.validate("openai/gpt-4o") as ModelId.Result.Valid
        Prefs.setModel(context, valid)
        Prefs.setTheme(context, "midnight")
        assertEquals("openai/gpt-4o", Prefs.getModel(context))
        assertEquals("midnight", Prefs.getTheme(context))
        Prefs.setTheme(context, "peach")
        assertEquals("openai/gpt-4o", Prefs.getModel(context))
        assertEquals("peach", Prefs.getTheme(context))
    }
}
