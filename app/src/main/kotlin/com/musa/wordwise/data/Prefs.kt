// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.data

import android.content.Context
import com.musa.wordwise.network.ModelId

/**
 * Non-secret app settings (theme, chosen OpenRouter model) in plain
 * SharedPreferences. The OpenRouter API key lives in [ApiKeyRepository].
 *
 * The model is public information — it is only ever sent to OpenRouter and
 * shown in the UI — so encrypting it would add cost with no benefit. Keeping it
 * out of the encrypted store also keeps the secret store holding exactly one
 * thing.
 */
object Prefs {

    private const val PREFS_NAME = "wordwise_prefs"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_MODEL = "selected_model"

    const val DEFAULT_THEME = "peach"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTheme(context: Context): String =
        prefs(context).getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }

    /**
     * The user's chosen OpenRouter model, or [ModelId.DEFAULT] when unset.
     *
     * Runs the stored value back through [ModelId.resolve] so a hand-edited or
     * restored-from-backup preference can never push a malformed ID to the API.
     */
    fun getModel(context: Context): String =
        ModelId.resolve(prefs(context).getString(KEY_MODEL, "").orEmpty())

    /**
     * Persists an already-validated model.
     *
     * Takes [ModelId.Result.Valid] rather than a [String] so an invalid value
     * cannot be passed at all: a silent downgrade to [ModelId.DEFAULT] would
     * make a typo look like a successful save while the user's real choice was
     * discarded. Rejecting belongs to the caller (see `POST /api/settings/model`),
     * which can then tell the user why.
     */
    fun setModel(context: Context, validated: ModelId.Result.Valid) {
        val modelId = validated.modelId
        prefs(context).edit()
            .putString(KEY_MODEL, if (modelId == ModelId.DEFAULT) "" else modelId)
            .apply()
    }
}
