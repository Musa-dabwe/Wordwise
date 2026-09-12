// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Singleton AI client for OpenCode Zen.
 *
 * OkHttpClient is shared across all calls to reuse the connection pool.
 * The API key is sent via the Authorization header — never in the URL —
 * so it cannot leak into request logs.
 */
object AiClient {

    const val MODEL = "big-pickle"
    private const val ENDPOINT = "https://opencode.ai/zen/v1/chat/completions"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private const val GRAMMAR_SYSTEM_PROMPT =
        "You are a grammar and style correction assistant. " +
        "Return only the corrected text. " +
        "Preserve the original language and meaning exactly. " +
        "Do not add any explanations, commentary, or quotation marks."

    sealed class Result {
        data class Success(val text: String) : Result()
        data class RateLimited(val message: String) : Result()
        data class Failure(val error: String) : Result()
    }

    /**
     * Sends [text] to OpenCode Zen for grammar and style correction.
     * Returns a [Result] — callers must handle all three cases.
     *
     * This function owns its own [withContext] switch. The call site in
     * GrammarFixService must NOT wrap this call in another withContext.
     */
    suspend fun fixGrammar(text: String, apiKey: String): Result =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("model", MODEL)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", GRAMMAR_SYSTEM_PROMPT)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", text)
                    })
                })
                put("temperature", 0.2)
                put("max_tokens", 2048)
            }.toString()

            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            executeRequest(request)
        }

    private fun executeRequest(request: Request): Result {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> parseContent(raw)
                        ?.let { Result.Success(it) }
                        ?: Result.Failure("No content returned from OpenCode Zen")
                    401, 403 -> Result.Failure("Invalid OpenCode Zen API key — check settings")
                    429 -> Result.RateLimited("OpenCode Zen rate limit reached — wait a moment")
                    in 500..599 -> Result.Failure("OpenCode Zen issue (HTTP ${response.code}) — try again")
                    else -> Result.Failure("OpenCode Zen error (HTTP ${response.code})")
                }
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Network error connecting to OpenCode Zen")
        }
    }

    internal fun parseContent(jsonString: String): String? = try {
        Json.parseToJsonElement(jsonString)
            .jsonObject["choices"]
            ?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
            ?.trim()
            ?.trim('"')
    } catch (e: Exception) {
        null
    }
}
