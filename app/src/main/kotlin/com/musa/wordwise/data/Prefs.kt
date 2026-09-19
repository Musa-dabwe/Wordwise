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
 * Non-secret app settings (theme) in plain SharedPreferences.
 * The OpenRouter API key lives in [ApiKeyRepository].
 */
object Prefs {

    private const val PREFS_NAME = "wordwise_prefs"
    private const val KEY_THEME = "selected_theme"

    const val DEFAULT_THEME = "peach"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTheme(context: Context): String =
        prefs(context).getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }
}
