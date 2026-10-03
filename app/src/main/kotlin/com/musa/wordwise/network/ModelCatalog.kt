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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * One row in the model picker.
 *
 * Only display-relevant fields are kept: the full catalog is 762 KB of JSON and
 * the WebView only ever renders name, ID, context size and a free badge.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val contextLength: Int,
    val isFree: Boolean
)

/**
 * Reads the OpenRouter model catalog so the settings screen can offer a
 * browsable picker.
 *
 * Deliberately unauthenticated. `GET /api/v1/models` is public, and sending the
 * user's key would mean every catalog refresh spends their credential on a
 * request whose response is not account-specific anyway. A model the account
 * cannot actually use is reported by OpenRouter at completion time, which is the
 * same authority that validates the ID in the first place.
 */
object ModelCatalog {

    private const val TAG = "ModelCatalog"
    private const val ENDPOINT = "https://openrouter.ai/api/v1/models"
    private const val HTTP_REFERER = "https://github.com/musa-dabwe/WordWise"
    private const val APP_TITLE = "WordWise"

    /** Display names are untrusted; cap what one row can carry. */
    private const val MAX_NAME_LENGTH = 160

    /**
     * The catalog is ~762 KB today. Refuse anything wildly larger rather than
     * letting a substituted or corrupted response drive an unbounded read.
     */
    private const val MAX_BODY_BYTES = 4L * 1024 * 1024

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the catalog, or null if the request failed.
     *
     * Failure is non-fatal: the user can still paste a model ID by hand, which
     * is the primary interaction. A null here just means the dropdown stays
     * unavailable.
     */
    suspend fun fetch(): List<ModelInfo>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("HTTP-Referer", HTTP_REFERER)
                .header("X-Title", APP_TITLE)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w(TAG, "Catalog request failed: HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                if (body.contentLength() > MAX_BODY_BYTES) {
                    android.util.Log.w(TAG, "Catalog too large: ${body.contentLength()} bytes")
                    return@withContext null
                }
                parse(body.string())
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Catalog request failed: ${e.message}")
            null
        }
    }

    /**
     * Parses the catalog and projects it down to [ModelInfo].
     *
     * Kept `internal` and Android-free so JVM tests can drive it with a JSON
     * string, mirroring how `AiClient.parseContent` is tested.
     *
     * Only text-output models are kept: WordWise inserts a plain-text result
     * into a text field, so an image-in/image-out model is never usable here.
     * That trims 464 models to 156 and roughly 762 KB to 15 KB.
     */
    internal fun parse(jsonString: String): List<ModelInfo> = try {
        val data = Json.parseToJsonElement(jsonString)
            .jsonObject["data"]
            ?.jsonArray
            ?: return emptyList()

        data.mapNotNull { element -> (element as? JsonObject)?.toModelInfo() }
    } catch (e: Exception) {
        emptyList()
    }

    private fun JsonObject.toModelInfo(): ModelInfo? {
        val id = this["id"]?.jsonPrimitive?.contentOrNull?.trim() ?: return null
        // ModelId.validate treats a blank string as "reset to default", which is
        // the right meaning for user input but the wrong filter for a catalog
        // row: a blank ID here would render as an empty selectable row. Require
        // the validated ID to equal the ID we were given.
        val validated = ModelId.validate(id)
        if (validated !is ModelId.Result.Valid || validated.modelId != id) return null

        // Untrusted display text: cap it so one absurd upstream value cannot be
        // amplified into every rendered row.
        val name = (this["name"]?.jsonPrimitive?.contentOrNull ?: id).take(MAX_NAME_LENGTH)
        val contextLength = this["context_length"]?.jsonPrimitive?.longOrNull
            ?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0

        val architecture = this["architecture"] as? JsonObject
        val outputModalities = (architecture?.get("output_modalities") as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        // A catalog entry with no declared output modality is treated as text so
        // a schema change upstream cannot silently empty the picker.
        if (outputModalities != null && outputModalities.none { it == "text" }) return null

        val pricing = this["pricing"] as? JsonObject
        val prompt = pricing?.get("prompt")?.jsonPrimitive?.contentOrNull
        val completion = pricing?.get("completion")?.jsonPrimitive?.contentOrNull
        val isFree = (prompt != null && prompt.toDoubleOrNull() == 0.0) &&
            (completion != null && completion.toDoubleOrNull() == 0.0)

        return ModelInfo(id, name, contextLength, isFree)
    }

    /**
     * Serialises the catalog for the `/api/models` route.
     *
     * Built by hand rather than with kotlinx.serialization's generated encoder
     * so the payload stays small and the key names stay short.
     */
    fun toJson(models: List<ModelInfo>): String = buildString(models.size * 96) {
        append('[')
        models.forEachIndexed { index, m ->
            if (index > 0) append(',')
            append("""{"id":""").append(Json.encodeToString(String.serializer(), m.id))
            append(""","name":""").append(Json.encodeToString(String.serializer(), m.name))
            append(""","ctx":""").append(m.contextLength)
            append(""","free":""").append(m.isFree).append('}')
        }
        append(']')
    }
}