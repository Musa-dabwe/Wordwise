package com.musa.wordwise.network

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
        val jsonObject = Json.parseToJsonElement(response.body!!.string()).jsonObject
        jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    } else {
        text // Return original on failure
    }
}