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
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Singleton AI client for OpenRouter.
 *
 * OkHttpClient is shared across all calls to reuse the connection pool.
 * The API key is sent via the Authorization header — never in the URL —
 * so it cannot leak into request logs.
 */
object AiClient {

    private const val TAG = "AiClient"
    const val MODEL = "openrouter/free"
    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private const val HTTP_REFERER = "https://github.com/musa-dabwe/WordWise"
    private const val APP_TITLE = "WordWise"

    // 429 retry backoff delay in milliseconds.
    private const val RETRY_DELAY_MS = 3_000L

    // Local counter for 429 occurrences (visible in logcat under AiClient tag).
    @Volatile
    private var rateLimitCount = 0

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
        "Do not add any explanations, commentary, or quotation marks. " +
        "Never use em-dashes (—); use a comma, colon, or restructure the sentence instead."

    sealed class Result {
        data class Success(val text: String) : Result()
        data class RateLimited(val message: String) : Result()
        data class Failure(val error: String) : Result()
    }

    /**
     * Sends [text] to OpenRouter for grammar and style correction.
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
            }.toString()

            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", HTTP_REFERER)
                .header("X-Title", APP_TITLE)
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
                        ?: Result.Failure("No content returned from OpenRouter")
                    401, 403 -> Result.Failure("Invalid OpenRouter API key — check settings")
                    429 -> {
                        rateLimitCount++
                        Log.w(TAG, "429 rate limit (occurrence #$rateLimitCount) — retrying in ${RETRY_DELAY_MS}ms")
                        Thread.sleep(RETRY_DELAY_MS)
                        // Single retry: re-execute the same request
                        httpClient.newCall(request).execute().use { retry ->
                            val retryRaw = retry.body?.string().orEmpty()
                            when (retry.code) {
                                200 -> parseContent(retryRaw)
                                    ?.let { Result.Success(it) }
                                    ?: Result.Failure("No content returned from OpenRouter")
                                429 -> {
                                    Log.w(TAG, "429 rate limit persisted after retry (total: $rateLimitCount)")
                                    Result.RateLimited("Correction busy — try again shortly")
                                }
                                else -> Result.Failure("OpenRouter error (HTTP ${retry.code}): $retryRaw")
                            }
                        }
                    }
                    in 500..599 -> Result.Failure("OpenRouter issue (HTTP ${response.code}) — try again")
                    else -> Result.Failure("OpenRouter error (HTTP ${response.code}): $raw")
                }
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Network error connecting to OpenRouter")
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
