package com.musa.wordwise.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object AiClient {
    private const val MAX_RETRIES = 3
    private const val INITIAL_DELAY_MS = 1000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fixGrammar(text: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) {
                Log.e("GrammarFix", "API Key is empty!")
                return@withContext text
            }

            // UNIFIED GRAMMAR CORRECTION PROMPT — WordWise v2
            // Temperature: 0.1 — deterministic correction with minimal entropy
            // for ambiguous cases (British/American spelling, context-dependent homonyms)
            val prompt = """
                Correct grammar, spelling, and punctuation in the text below.

                RULES:

                1. LANGUAGE — Automatically detect the input language and correct within
                that language. NEVER translate. If the input mixes multiple languages,
                preserve the mix and correct each segment in its own language.
                Supported languages include: Swahili, Chinyanja, Nyanja, Bemba, Tonga, Lozi, Kaonde, Luvale, Zulu, Xhosa, Shona, Ndebele, Sotho, Tswana, Venda, Tsonga, Afrikaans, Amharic, Hausa, Yoruba, Igbo, Twi, Wolof, Somali, Tigrinya, Oromo, Kinyarwanda, Kirundi, Luganda, Lingala, Kikuyu, Dholuo, Fula, Bambara, Ewe, Akan, Ga, Igala, Kanuri, Malagasy, Sango, Chichewa, English, French, Spanish, Portuguese, German, Italian, Dutch, Russian, Polish, Ukrainian, Romanian, Czech, Slovak, Hungarian, Greek, Swedish, Norwegian, Danish, Finnish, Croatian, Serbian, Bulgarian, Catalan, Turkish, Albanian, Macedonian, Slovenian, Estonian, Latvian, Lithuanian, Welsh, Irish, Basque, Maltese, Icelandic, Luxembourgish, Belarusian, Georgian, Armenian, Azerbaijani, Mandarin Chinese, Cantonese, Japanese, Korean, Hindi, Bengali, Urdu, Tamil, Telugu, Marathi, Gujarati, Punjabi, Kannada, Malayalam, Odia, Assamese, Nepali, Sinhala, Thai, Vietnamese, Indonesian, Malay, Tagalog, Burmese, Khmer, Lao, Javanese, Sundanese, Cebuano, Mongolian, Tibetan, Uyghur, Kazakh, Uzbek, Kyrgyz, Tajik, Turkmen, Pashto, Dari, Farsi, Kurdish, Hebrew, Arabic, Aramaic, Haitian Creole, Jamaican Patois, Quechua, Guaraní, Nahuatl, Māori, Hawaiian, Samoan, Tongan, Fijian, Tok Pisin.

                2. STRUCTURE — Preserve ALL line breaks, paragraph separations, and
                sentence boundaries exactly as written. NEVER merge two sentences into
                one. NEVER split one sentence into two. NEVER reorder content.

                3. TONE — Preserve the original tone, style, and meaning. Do not rephrase
                for style. Do not add words not implied. Do not remove words unless they
                are clearly duplicates or errors.

                4. OUTPUT — Output ONLY the corrected text. No explanations, no
                commentary, no labels, no quotation marks. If the text is already
                correct, return it exactly as received.

                5. CAPITALISATION — Always capitalise the first letter of the
                corrected output, regardless of how the input started. Every
                sentence in the output must start with a capital letter.

                6. SHORT TEXT — If the input is one or two words, correct obvious spelling
                errors only. Do not add punctuation that was not there.

                7. CODE-SWITCHING — If the input mixes two or more languages, treat each
                segment in its own language context. Do not normalize to a single language.

                Text: $text
            """.trimIndent()

            // Build JSON for Gemini API
            val jsonBody = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        putJsonArray("parts") {
                            addJsonObject {
                                put("text", prompt)
                            }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 4000)
                }
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

            Log.d("GrammarFix", "Sending request to Gemini...")
            Log.d("GrammarFix", "Input length: ${text.length} chars")

            var lastError: Exception? = null
            repeat(MAX_RETRIES) { attempt ->
                try {
                    val result = executeRequest(requestBody, apiKey, text)
                    if (result != null) return@withContext result
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < MAX_RETRIES - 1) {
                        val delayMs = INITIAL_DELAY_MS * (attempt + 1)
                        Log.w("GrammarFix",
                            "Attempt ${attempt + 1} failed, retrying in ${delayMs}ms")
                        delay(delayMs)
                    }
                }
            }

            Log.e("GrammarFix", "All $MAX_RETRIES attempts failed: ${lastError?.message}")
            return@withContext text
        }

    private fun executeRequest(
        requestBody: okhttp3.RequestBody,
        apiKey: String,
        originalText: String
    ): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-2.5-flash-lite:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()

        Log.d("GrammarFix", "Response code: ${response.code}")

        // Retry on 429 (rate limit) and 5xx (server errors)
        if (response.code == 429 || response.code >= 500) {
            Log.w("GrammarFix", "Retryable error: ${response.code}")
            return null // signals retry
        }

        if (response.isSuccessful && responseBody != null) {
            return parseResponse(responseBody, originalText)
        } else {
            Log.e("GrammarFix", "API Error: ${response.code} ${response.message}")
            return originalText // non-retryable error, return original
        }
    }

    private fun parseResponse(responseBody: String, originalText: String): String {
        return try {
            val jsonObject = Json.parseToJsonElement(responseBody).jsonObject
            val correctedText = jsonObject["candidates"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content
                ?.trim()

            if (!correctedText.isNullOrEmpty()) {
                Log.d("GrammarFix", "Success!")
                Log.d("GrammarFix", "Output length: ${correctedText.length} chars")
                correctedText
            } else {
                Log.e("GrammarFix", "Corrected text is empty")
                originalText
            }
        } catch (e: Exception) {
            Log.e("GrammarFix", "JSON parsing failed: ${e.message}", e)
            originalText
        }
    }
}
