// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.network

/**
 * Format rules for OpenRouter model IDs.
 *
 * OpenRouter identifies a model as `vendor/model` (e.g. `anthropic/claude-3.5-sonnet`)
 * with an optional `:variant` suffix (e.g. `meta-llama/llama-3.3-70b-instruct:free`).
 * A leading `~` marks an internal "latest version" alias such as `~openai/gpt-astra-latest`.
 *
 * This is a *format* check only, deliberately not an existence check. Verifying
 * that a model actually exists would require a network call, and OpenRouter
 * already returns an explicit error for an unknown model ID. Rejecting a
 * well-formed ID we simply have not heard of would be worse than passing it
 * through and surfacing OpenRouter's own message.
 *
 * Deliberately free of Android imports so it is unit-testable on the JVM.
 */
object ModelId {

    /** The free models router — used when the user has not chosen a model. */
    const val DEFAULT = "openrouter/free"

    /**
     * Upper bound on an accepted ID.
     *
     * The longest real OpenRouter ID observed is 56 characters; 120 is a
     * deliberate ceiling so a pasted paragraph of text is rejected outright
     * instead of being forwarded to the API.
     */
    const val MAX_LENGTH = 120

    /**
     * One `/`, two non-empty segments of `[A-Za-z0-9._:~-]`.
     * `~` is included so "latest version" aliases stay pasteable.
     */
    private val PATTERN = Regex("^[A-Za-z0-9._:~-]+/[A-Za-z0-9._:~-]+$")

    sealed class Result {
        /** [modelId] is safe to persist and to send to OpenRouter. */
        data class Valid(val modelId: String) : Result()
        /** [reason] is user-facing copy explaining what to fix. */
        data class Invalid(val reason: String) : Result()
    }

    /**
     * Validates a user-entered model ID.
     *
     * A blank entry is **valid** and resolves to [DEFAULT] — this is how the
     * user clears a custom model and goes back to the free router.
     */
    fun validate(raw: String): Result {
        val trimmed = raw.trim()

        if (trimmed.isEmpty()) return Result.Valid(DEFAULT)

        if (trimmed.length > MAX_LENGTH) {
            return Result.Invalid("Model name is too long — expected vendor/model")
        }
        if (trimmed.any { it.isWhitespace() }) {
            return Result.Invalid("Model name cannot contain spaces")
        }
        if (!PATTERN.matches(trimmed)) {
            return Result.Invalid("Use the format vendor/model — for example anthropic/claude-3.5-sonnet")
        }
        return Result.Valid(trimmed)
    }

    /**
     * Returns the model to actually use, falling back to [DEFAULT] when the
     * stored value is blank or malformed.
     *
     * Preferences are user-editable outside the app (backup restore, adb), so
     * this is the last line of defence before a bad string reaches the API.
     */
    fun resolve(stored: String): String =
        when (val result = validate(stored)) {
            is Result.Valid -> result.modelId
            is Result.Invalid -> DEFAULT
        }
}