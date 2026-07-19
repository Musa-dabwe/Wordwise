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
 * Non-secret app settings (model, theme) in plain SharedPreferences.
 * The Gemini API key lives in [ApiKeyRepository].
 */
object Prefs {

    private const val PREFS_NAME = "wordwise_prefs"
    private const val KEY_MODEL = "selected_model"
    private const val KEY_THEME = "selected_theme"

    const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
    const val DEFAULT_THEME = "peach"

    // Free-tier Gemini models (Flash / Flash-Lite families only).
    val GEMINI_MODELS = listOf(
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedModel(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun setSelectedModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, model).apply()
    }

    fun getTheme(context: Context): String =
        prefs(context).getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }
}
