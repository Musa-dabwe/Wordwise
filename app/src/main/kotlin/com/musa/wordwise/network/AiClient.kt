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
 */
object AiClient {

    // ── Google Gemini ─────────────────────────────────────────────────────────
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val GEMINI_MODEL    = "gemini-2.5-flash-lite"

    // ── Shared HTTP client ────────────────────────────────────────────────────
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Result type ───────────────────────────────────────────────────────────
    sealed class Result {
        data class Success(val text: String) : Result()
        data class RateLimited(val message: String) : Result()
        data class Failure(val error: String) : Result()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends [text] to Gemini for grammar and style correction.
     * Returns a [Result] — callers must handle all three cases.
     *
     * This function owns its own [withContext] switch. The call site in
     * GrammarFixService must NOT wrap this call in another withContext.
     */
    suspend fun fixGrammar(text: String, apiKey: String): Result =
        withContext(Dispatchers.IO) {
            fixGrammarGemini(text, apiKey)
        }

    // ── Gemini implementation ─────────────────────────────────────────────────

    private fun fixGrammarGemini(text: String, apiKey: String): Result {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", "\n\n") })
                    })
                })
            })
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("/:generateContent?key=")
            .post(body)
            .build()

        return executeRequest(request) { responseBody ->
            Json.parseToJsonElement(responseBody)
                .jsonObject["candidates"]
                ?.let { arr -> arr.jsonArray[0] }
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.let { arr -> arr.jsonArray[0] }
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content
                ?: error("Unexpected Gemini response shape")
        }
    }

    // ── Shared utilities ──────────────────────────────────────────────────────

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private const val GRAMMAR_SYSTEM_PROMPT =
        "You are a grammar and style correction assistant. " +
        "Return only the corrected text. " +
        "Preserve the original language and meaning exactly. " +
        "Do not add any explanations, commentary, or quotation marks."

    /**
     * Executes the [request], delegates response body parsing to [parseBody],
     * and maps HTTP error codes to the appropriate [Result] subtype.
     */
    private inline fun executeRequest(
        request: Request,
        parseBody: (String) -> String,
    ): Result {
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val raw = response.body?.string()
                            ?: return Result.Failure("Empty response body")
                        Result.Success(parseBody(raw))
                    }
                    401 -> Result.Failure("Invalid API key — check your key and try again")
                    429 -> Result.RateLimited(
                        "Free-tier quota reached — please try again later"
                    )
                    else -> Result.Failure("HTTP ${response.code} from Gemini")
                }
            }
        }.getOrElse { Result.Failure(it.message ?: "Network error") }
    }
}
