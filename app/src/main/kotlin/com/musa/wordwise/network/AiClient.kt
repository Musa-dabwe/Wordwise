package com.musa.wordwise.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class GrammarResult {
    data class Success(val correctedText: String) : GrammarResult()
    data class RateLimited(val retryAfterSeconds: Int?) : GrammarResult()
    data class AuthError(val code: Int) : GrammarResult()
    data class NetworkError(val message: String?) : GrammarResult()
    data class Unchanged(val reason: String) : GrammarResult()
}

object AiClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fixGrammar(text: String, apiKey: String): GrammarResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) {
                return@withContext GrammarResult.Unchanged("API key is empty")
            }

            // UNIFIED GRAMMAR CORRECTION PROMPT — WordWise v2
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

            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.5-flash-lite:generateContent?key=$apiKey"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    Log.d("GrammarFix", "Response code: ${response.code}")

                    when {
                        response.code == 429 -> {
                            val retryAfter = response.header("Retry-After")?.toIntOrNull()
                            GrammarResult.RateLimited(retryAfter)
                        }
                        response.code == 401 || response.code == 403 -> {
                            GrammarResult.AuthError(response.code)
                        }
                        !response.isSuccessful -> {
                            GrammarResult.NetworkError("${response.code} ${response.message}")
                        }
                        responseBody == null -> {
                            GrammarResult.NetworkError("Empty response body")
                        }
                        else -> {
                            parseResponse(responseBody, text)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GrammarFix", "Network error: ${e.message}", e)
                GrammarResult.NetworkError(e.message)
            }
        }

    private fun parseResponse(responseBody: String, originalText: String): GrammarResult {
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
                if (correctedText == originalText) {
                    GrammarResult.Unchanged("No corrections needed")
                } else {
                    GrammarResult.Success(correctedText)
                }
            } else {
                GrammarResult.NetworkError("JSON parsing failed: empty result")
            }
        } catch (e: Exception) {
            Log.e("GrammarFix", "JSON parsing failed: ${e.message}", e)
            GrammarResult.NetworkError("JSON parsing failed")
        }
    }
}
