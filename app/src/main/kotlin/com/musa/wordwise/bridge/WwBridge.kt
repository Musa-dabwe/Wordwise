// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.bridge

import com.musa.wordwise.network.ModelId
import com.musa.wordwise.server.Themes

/**
 * The OpenRouter API key store, as the bridge needs it.
 *
 * An interface rather than [com.musa.wordwise.data.ApiKeyRepository] directly,
 * because the real one is built on `EncryptedSharedPreferences` and Robolectric
 * ships no AndroidKeyStore shadow — so on the JVM it cannot be constructed at
 * all. Injecting the seam keeps every bridge rule testable as a plain unit test.
 */
interface KeyStore {
    fun hasKey(): Boolean
    fun saveKey(key: String)
    fun clearKey()
}

/** Non-secret app settings, as the bridge needs them. */
interface AppSettings {
    fun model(): String
    fun setModel(validated: ModelId.Result.Valid)
    fun theme(): String
    fun setTheme(key: String)
}

/** Outcome of a mutating bridge call. */
sealed interface BridgeResult {
    /**
     * Succeeded. [note] is user-facing text for the caller to surface, normally
     * empty. [statusBarColor] is a hex colour the caller should apply, or null
     * when the call has no effect on chrome.
     */
    data class Ok(val note: String = "", val statusBarColor: String? = null) : BridgeResult

    /** [reason] is user-facing copy explaining what to fix. */
    data class Err(val reason: String) : BridgeResult
}

/**
 * The decision-making behind the one JS-to-native trust boundary.
 *
 * This is the app's only way for the WebView to read or change anything, so the
 * rules live here where they can be tested directly rather than inside a
 * 324-line Activity. [com.musa.wordwise.MainActivity.WwNativeBridge] adapts this
 * to JavaScript and does nothing but hop to the UI thread.
 *
 * Two properties matter and are asserted in `WwBridgeTest`:
 *
 * 1. **The key is write-only.** Nothing here returns key material — only
 *    [hasApiKey]'s boolean. There is deliberately no getter.
 * 2. **Rejection is explicit.** An invalid value returns a reason instead of
 *    being silently downgraded, so a typo can never look like a successful save.
 *
 * Instances are stateless and safe to call from any thread; the stores behind
 * them are the shared, thread-safe singletons.
 */
class WwBridge(
    private val keys: KeyStore,
    private val settings: AppSettings
) {

    /** Longest accepted key. Real OpenRouter keys are far shorter. */
    private val maxKeyLength = 200

    // ---------- API key ----------

    /**
     * Whether a key is stored. Deliberately a boolean: the key is never readable
     * from JS, so it cannot leak back out through the bridge, through the local
     * server, or into a screenshot of the DOM.
     */
    fun hasApiKey(): Boolean = keys.hasKey()

    /** Saves [key], replacing any existing one. */
    fun saveApiKey(key: String): BridgeResult {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return BridgeResult.Err("API key cannot be empty")
        if (trimmed.length > maxKeyLength) {
            return BridgeResult.Err("That does not look like an OpenRouter key")
        }
        keys.saveKey(trimmed)
        return BridgeResult.Ok()
    }

    /**
     * Deletes the stored key.
     *
     * Needed because the key is write-only: making it unreadable also removed the
     * user's only in-app way to get rid of it, which would otherwise be clearing
     * app data.
     */
    fun clearApiKey(): BridgeResult {
        keys.clearKey()
        return BridgeResult.Ok()
    }

    // ---------- model ----------

    fun getModel(): String = settings.model()

    /** Validates and stores the model, returning the reason on rejection. */
    fun setModel(raw: String): BridgeResult =
        when (val result = ModelId.validate(raw)) {
            is ModelId.Result.Valid -> {
                settings.setModel(result)
                BridgeResult.Ok()
            }
            is ModelId.Result.Invalid -> BridgeResult.Err(result.reason)
        }

    // ---------- theme ----------

    fun getTheme(): String = settings.theme()

    /**
     * Stores the theme and reports the status bar colour to apply.
     *
     * The colour is returned rather than applied here: this class must not touch
     * a view, so the caller hops to the UI thread.
     */
    fun setTheme(key: String): BridgeResult {
        if (key !in Themes.KEYS) return BridgeResult.Err("Unknown theme")
        settings.setTheme(key)
        return BridgeResult.Ok(statusBarColor = Themes.byKey(key).statusBar)
    }
}