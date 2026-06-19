package com.musa.wordwise.network

import com.musa.wordwise.data.Provider
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
 * Singleton AI client. Supports Ollama Cloud and Google Gemini as backends,
 * selected at call time via the [Provider] argument.
 *
 * OkHttpClient is shared across all calls to reuse the connection pool.
 */
object AiClient {

    // ── Ollama Cloud ──────────────────────────────────────────────────────────
    private const val OLLAMA_BASE_URL     = "https://ollama.com"
    private const val OLLAMA_CHAT_URL     = "$OLLAMA_BASE_URL/api/chat"
    // Lightest cloud model — smallest GPU footprint minimises free-tier quota burn.
    // Grammar correction is a short-context task; a 2B model is sufficient.
    private const val OLLAMA_DEFAULT_MODEL = "gemma4:e2b"

    // ── Google Gemini ─────────────────────────────────────────────────────────
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val GEMINI_MODEL    = "gemini-2.5-flash-lite"

    // ── Shared HTTP client ────────────────────────────────────────────────────
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)   // Ollama cloud cold-starts can be slow
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
     * Sends [text] to the chosen [provider] for grammar and style correction.
     * Returns a [Result] — callers must handle all three cases.
     *
     * This function owns its own [withContext] switch. The call site in
     * GrammarFixService must NOT wrap this call in another withContext.
     */
    suspend fun fixGrammar(text: String, apiKey: String, provider: Provider): Result =
        withContext(Dispatchers.IO) {
            when (provider) {
                Provider.OLLAMA -> fixGrammarOllama(text, apiKey)
                Provider.GEMINI -> fixGrammarGemini(text, apiKey)
            }
        }

    // ── Ollama implementation ─────────────────────────────────────────────────

    private fun fixGrammarOllama(text: String, apiKey: String): Result {
        val body = buildJsonObject {
            put("model", OLLAMA_DEFAULT_MODEL)
            put("stream", false)
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
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(OLLAMA_CHAT_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return executeRequest(request) { responseBody ->
            Json.parseToJsonElement(responseBody)
                .jsonObject["message"]
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: error("Unexpected Ollama response shape")
        }
    }

    // ── Gemini implementation ─────────────────────────────────────────────────

    private fun fixGrammarGemini(text: String, apiKey: String): Result {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", "$GRAMMAR_SYSTEM_PROMPT\n\n$text") })
                    })
                })
            })
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$GEMINI_BASE_URL/$GEMINI_MODEL:generateContent?key=$apiKey")
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
                        "Free-tier quota reached — try again later (resets every ~5 hours)"
                    )
                    else -> Result.Failure("HTTP ${response.code} from provider")
                }
            }
        }.getOrElse { Result.Failure(it.message ?: "Network error") }
    }
}
