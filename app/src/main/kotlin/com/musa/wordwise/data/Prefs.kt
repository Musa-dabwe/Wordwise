// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.data

import android.content.Context

/**
 * Non-secret app settings (model, accent, canvas tint) in plain
 * SharedPreferences. The Gemini API key lives in [ApiKeyRepository].
 */
object Prefs {

    private const val PREFS_NAME = "wordwise_prefs"
    private const val KEY_MODEL = "selected_model"
    private const val KEY_ACCENT = "selected_accent"
    private const val KEY_TINT = "selected_tint"

    const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
    const val DEFAULT_ACCENT = "#b9a5ec"
    const val DEFAULT_TINT = "Lavender"

    // Free-tier Gemini models (Flash / Flash-Lite families only).
    val GEMINI_MODELS = listOf(
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash"
    )

    /**
         * Provides access to the app's private preferences.
         *
         * @return The app's shared preferences.
         */
        private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
         * Retrieves the selected Gemini model.
         *
         * @return The stored model identifier, or the default model when none is stored.
         */
        fun getSelectedModel(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    /**
     * Stores the selected Gemini model.
     *
     * @param context The Android context used to access application preferences.
     * @param model The Gemini model identifier to store.
     */
    fun setSelectedModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, model).apply()
    }

    /**
         * Retrieves the saved accent color.
         *
         * @param context The context used to access application preferences.
         * @return The saved accent color, or the default accent color when none is stored.
         */
        fun getAccent(context: Context): String =
        prefs(context).getString(KEY_ACCENT, DEFAULT_ACCENT) ?: DEFAULT_ACCENT

    /**
     * Stores the accent color preference.
     *
     * @param context The Android context used to access preferences.
     * @param accent The accent color value to store.
     */
    fun setAccent(context: Context, accent: String) {
        prefs(context).edit().putString(KEY_ACCENT, accent).apply()
    }

    /**
         * Retrieves the configured canvas tint.
         *
         * @param context The context used to access app preferences.
         * @return The stored canvas tint, or the default tint when none is configured.
         */
        fun getTint(context: Context): String =
        prefs(context).getString(KEY_TINT, DEFAULT_TINT) ?: DEFAULT_TINT

    /**
     * Stores the canvas tint preference.
     *
     * @param context The Android context used to access the preferences.
     * @param tint The canvas tint value to store.
     */
    fun setTint(context: Context, tint: String) {
        prefs(context).edit().putString(KEY_TINT, tint).apply()
    }
}
