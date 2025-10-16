package com.musa.wordwise.network

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

suspend fun fixGrammar(text: String, apiKey: String): String {
    val client = OkHttpClient()
    val json = """
        {
            "model": "gpt-3.5-turbo",
            "messages": [
                {"role": "system", "content": "You are a grammar correction assistant. Only output the corrected text with no explanations."},
                {"role": "user", "content": "Fix grammar: $text"}
            ]
        }
    """.trimIndent()

    val request = Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()

    val response = client.newCall(request).execute()
    return if (response.isSuccessful) {
        try {
            val responseBody = response.body?.string()
            val jsonObject = Json.parseToJsonElement(responseBody!!).jsonObject
            jsonObject["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: text
        } catch (e: Exception) {
            Log.e("GrammarFix", "JSON parsing failed", e)
            text
        }
    } else {
        Log.e("GrammarFix", "API Error: ${response.code} ${response.message}")
        Log.e("GrammarFix", "Response body: ${response.body?.string()}")
        text // Return original on failure
    }
}