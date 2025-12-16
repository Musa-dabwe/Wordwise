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

enum class FixMode {
    SENTENCE,  // ?fixs - Fix a single sentence
    PARAGRAPH, // ?fixp - Fix a paragraph
    ALL        // ?fixo - Fix all text in field
}

suspend fun fixGrammar(text: String, apiKey: String, mode: FixMode = FixMode.SENTENCE): String = withContext(Dispatchers.IO) {
    if (apiKey.isEmpty()) {
        Log.e("GrammarFix", "API Key is empty!")
        return@withContext text
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Create different prompts based on mode
    val prompt = when (mode) {
        FixMode.SENTENCE -> """
            Fix all grammar, spelling, and punctuation errors in this sentence. 
            Only output the corrected sentence with no explanations or extra commentary.
            Preserve the original meaning and tone.
            
            Sentence: $text
        """.trimIndent()
        
        FixMode.PARAGRAPH -> """
            Fix all grammar, spelling, and punctuation errors in this paragraph.
            Maintain proper paragraph structure and flow.
            Only output the corrected paragraph with no explanations or extra commentary.
            Preserve the original meaning and tone.
            
            Paragraph: $text
        """.trimIndent()
        
        FixMode.ALL -> """
            Fix all grammar, spelling, and punctuation errors in this entire text.
            Maintain the structure of multiple sentences and paragraphs.
            Keep line breaks and paragraph separations.
            Only output the corrected text with no explanations or extra commentary.
            Preserve the original meaning and tone.
            
            Text: $text
        """.trimIndent()
    }

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
            put("maxOutputTokens", when (mode) {
                FixMode.SENTENCE -> 500
                FixMode.PARAGRAPH -> 1500
                FixMode.ALL -> 4000
            })
        }
    }

    val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

    val modeLabel = when (mode) {
        FixMode.SENTENCE -> "SENTENCE"
        FixMode.PARAGRAPH -> "PARAGRAPH"
        FixMode.ALL -> "ALL TEXT"
    }
    
    Log.d("GrammarFix", "Sending request to Gemini (Mode: $modeLabel)...")
    Log.d("GrammarFix", "API Key starts with: ${apiKey.take(7)}...")
    Log.d("GrammarFix", "Text length: ${text.length} characters")
    
    // Gemini API endpoint
    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
    
    val request = Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/json")
        .post(requestBody)
        .build()

    try {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        Log.d("GrammarFix", "Response code: ${response.code}")
        
        if (response.isSuccessful && responseBody != null) {
            try {
                val jsonObject = Json.parseToJsonElement(responseBody).jsonObject
                
                val correctedText = jsonObject["candidates"]
                    ?.jsonArray
                    ?.get(0)
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonObject
                    ?.get("parts")
                    ?.jsonArray
                    ?.get(0)
                    ?.jsonObject
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.content
                    ?.trim()

                if (correctedText != null && correctedText.isNotEmpty()) {
                    Log.d("GrammarFix", "Success! Mode: $modeLabel")
                    Log.d("GrammarFix", "Original length: ${text.length}, Corrected length: ${correctedText.length}")
                    return@withContext correctedText
                } else {
                    Log.e("GrammarFix", "Corrected text is empty")
                    return@withContext text
                }
            } catch (e: Exception) {
                Log.e("GrammarFix", "JSON parsing failed: ${e.message}", e)
                Log.e("GrammarFix", "Response was: $responseBody")
                return@withContext text
            }
        } else {
            Log.e("GrammarFix", "API Error: ${response.code} ${response.message}")
            if (responseBody != null) {
                Log.e("GrammarFix", "Error details: $responseBody")
            }
            return@withContext text
        }
    } catch (e: Exception) {
        Log.e("GrammarFix", "Network error: ${e.message}", e)
        return@withContext text
    }
}