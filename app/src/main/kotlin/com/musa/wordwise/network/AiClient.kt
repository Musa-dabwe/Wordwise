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
 * Singleton AI client for Google Gemini.
 *
 * OkHttpClient is shared across all calls to reuse the connection pool.
 * The API key is sent via the `x-goog-api-key` header — never in the URL —
 * so it cannot leak into request logs.
 */
object AiClient {

    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Success(val text: String) : Result()
        data class RateLimited(val message: String) : Result()
        data class Failure(val error: String) : Result()
    }

    /**
     * Sends [text] to Gemini for grammar and style correction.
     * Returns a [Result] — callers must handle all three cases.
     *
     * This function owns its own [withContext] switch. The call site in
     * GrammarFixService must NOT wrap this call in another withContext.
     */
    suspend fun fixGrammar(text: String, apiKey: String, model: String): Result =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$GEMINI_BASE_URL/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(buildRequestBody(text, model).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(request, model)
        }

    private fun buildRequestBody(text: String, model: String): String = buildJsonObject {
        put("system_instruction", buildJsonObject {
            put("parts", buildJsonArray {
                add(buildJsonObject { put("text", GRAMMAR_SYSTEM_PROMPT) })
            })
        })
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", text) })
                })
            })
        })
        // Grammar fixing needs determinism, not reasoning. Gemini 2.5 models
        // accept a zero thinking budget; Gemini 3+ models perform best with
        // their default sampling settings, so no config is sent for them.
        if (model.startsWith("gemini-2.5")) {
            put("generationConfig", buildJsonObject {
                put("temperature", 0.2)
                put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) })
            })
        }
    }.toString()

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private const val GRAMMAR_SYSTEM_PROMPT =
        "You are a grammar and style correction assistant. " +
        "Return only the corrected text. " +
        "Preserve the original language and meaning exactly. " +
        "Do not add any explanations, commentary, or quotation marks."

    private fun executeRequest(request: Request, model: String): Result {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> parseCandidateText(raw)
                        ?.let { Result.Success(it) }
                        ?: Result.Failure("No text returned — the response may have been blocked")
                    429 -> Result.RateLimited("Free-tier limit reached — wait a minute and try again")
                    400, 401, 403 -> {
                        val message = parseErrorMessage(raw)
                        if (message != null && message.contains("api key", ignoreCase = true)) {
                            Result.Failure("Invalid API key — check it in WordWise settings")
                        } else {
                            Result.Failure(message ?: "Request rejected (HTTP ${response.code})")
                        }
                    }
                    404 -> Result.Failure("Model '$model' is not available — pick another in WordWise")
                    in 500..599 -> Result.Failure("Gemini is having issues (HTTP ${response.code}) — try again")
                    else -> Result.Failure(parseErrorMessage(raw) ?: "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Unknown network error")
        }
    }

    private fun parseCandidateText(responseBody: String): String? = try {
        Json.parseToJsonElement(responseBody)
            .jsonObject["candidates"]
            ?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")
            ?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content?.trim()
    } catch (e: Exception) {
        null
    }

    private fun parseErrorMessage(responseBody: String): String? = try {
        Json.parseToJsonElement(responseBody)
            .jsonObject["error"]
            ?.jsonObject?.get("message")
            ?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }
}
