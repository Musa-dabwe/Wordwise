> **Historical Document:** This spec describes the OpenCode Zen integration that has since been replaced by OpenRouter. See `docs/superpowers/specs/2026-09-16-openrouter-migration-design.md` for the current provider specification.

---

# Specification: OpenCode Zen (`big-pickle`) as WordWise's Sole AI Provider

**Document Status:** Revised — Full Replacement (not multi-provider)
**Author:** WordWise Engineering
**Target Release:** WordWise 2.0
**Feature:** Replace Gemini entirely with OpenCode Zen's hardcoded `big-pickle` model

---

## 0. Revision Note

The original draft of this spec proposed a pluggable multi-provider architecture (`AiProvider` interface, `ProviderType` sealed class, provider selector UI) with Gemini and OpenCode Zen coexisting. **That is no longer the plan.** OpenCode Zen's `big-pickle` model is replacing Gemini outright as WordWise's only AI backend. This revision strips out every piece of multi-provider scaffolding that only existed to support switching between two providers, since there will only ever be one active provider going forward.

Keeping the abstraction would have been speculative generality for a codebase this size — it adds a sealed class, an interface, per-provider prefs branching, and a settings toggle, all to support a choice that no longer exists. Simpler wins here.

---

## 1. Executive Summary & Objectives

WordWise currently relies exclusively on Google Gemini (`gemini-2.5-flash-lite`, etc.) for text correction. Gemini's free-tier rate limits (HTTP 429) have been a recurring source of user friction. Rather than adding a second provider alongside Gemini, WordWise is **replacing Gemini entirely** with OpenCode Zen's free, unmetered stealth model `big-pickle`.

### Key Highlights
- **Full Replacement, Not Addition:** Gemini support (`GeminiProvider`/equivalent Gemini payload+parsing code, Gemini model selection UI, Gemini-specific prefs) is removed, not kept alongside OpenCode Zen.
- **Hardcoded Model (`big-pickle`):** No model selection UI is needed — `big-pickle` is the only model WordWise will ever call.
- **Simplified Network Layer:** `AiClient` talks directly to OpenCode Zen's OpenAI-compatible endpoint. No provider dispatch/router is needed since there is only one provider.
- **Single API Key:** `ApiKeyRepository` stores one encrypted key (OpenCode Zen). The old Gemini key entry is migrated/cleared, not kept as a second slot.
- **Simplified Settings UI:** The provider toggle is removed. Settings shows one API key field and a fixed `big-pickle` model badge.

---

## 2. Architectural Comparison

### Before (Gemini only)
```
+---------------------+     +--------------------+     +-------------------+
| GrammarFixService   | --> | ApiKeyRepository   | --> | Prefs (Gemini)    |
+---------------------+     +--------------------+     +-------------------+
          |
          v
+--------------------------------------------------------------------------+
| AiClient (Singleton)                                                     |
|  - Hardcoded URL: https://generativelanguage.googleapis.com/...          |
|  - Gemini JSON payload builder & candidate text parser                    |
+--------------------------------------------------------------------------+
```

### After (OpenCode Zen only — full replacement)
```
+---------------------+     +--------------------+     +-------------------+
| GrammarFixService   | --> | ApiKeyRepository   | --> | Prefs             |
+---------------------+     | (single key)       |     | (no provider flag)|
          |                 +--------------------+     +-------------------+
          v
+--------------------------------------------------------------------------+
| AiClient (Singleton — OpenCode Zen only)                                 |
|  - Hardcoded URL: https://opencode.ai/zen/v1/chat/completions           |
|  - Hardcoded model: "big-pickle"                                         |
|  - OpenAI-compatible JSON payload builder & choices[0].message parser    |
+--------------------------------------------------------------------------+
```

No router, no interface, no sealed class — `AiClient` *is* the OpenCode Zen client now, the same way it was the Gemini client before. This mirrors the original architecture's shape, just pointed at a different backend.

---

## 3. OpenCode Zen Protocol & Technical Specification

### 3.1 Endpoint & Transport Configuration
- **Base Endpoint:** `https://opencode.ai/zen/v1`
- **Route:** `/chat/completions` (OpenAI-compatible REST protocol)
- **HTTP Method:** `POST`
- **Content-Type:** `application/json`
- **TLS Version:** TLS 1.3 / TLS 1.2 strict

### 3.2 Authentication Header
```http
Authorization: Bearer <OPENCODE_ZEN_API_KEY>
x-opencode-session: <UUID>
```

The `x-opencode-session` header carries a client-generated, stable UUID v4 that persists for the life of the app process. Without this header, OpenCode Zen returns HTTP 400 `MissingSessionID` for free-tier models. The server uses this for routing/cache-affinity — it accepts any UUID the client generates.

### 3.3 Model Specification
- **Hardcoded Model Identifier:** `big-pickle`
- **Rationale:** Free, unmetered stealth model since launch — eliminates both configuration friction and the 429s that motivated this replacement in the first place.

### 3.4 Request Payload
```json
{
  "model": "big-pickle",
  "messages": [
    {
      "role": "system",
      "content": "You are a grammar and style correction assistant. Return only the corrected text. Preserve the original language and meaning exactly. Do not add any explanations, commentary, or quotation marks."
    },
    {
      "role": "user",
      "content": "i has a apple"
    }
  ]
}
```

**Note:** The `big-pickle` model served via `@ai-sdk/openai-compatible` rejects requests that include `temperature` or `max_tokens`. These fields are omitted to match the working reference implementation (pi-opencode).

### 3.5 Response Structure & Parsing
```json
{
  "id": "chatcmpl-opencode-12345",
  "object": "chat.completion",
  "created": 1718900000,
  "model": "big-pickle",
  "choices": [
    {
      "index": 0,
      "message": { "role": "assistant", "content": "I have an apple." },
      "finish_reason": "stop"
    }
  ],
  "usage": { "prompt_tokens": 42, "completion_tokens": 5, "total_tokens": 47 }
}
```

**JSON Parsing Extraction Path:** `choices[0].message.content` → trim whitespace and leading/trailing quotes if present.

### 3.6 Error Mapping Strategy
| HTTP Code | Condition | Mapped `Result` | User-Facing Message |
|-----------|-----------|------------------|----------------------|
| 200 | Valid JSON with `choices[0].message.content` | `Result.Success(text)` | "Text corrected" |
| 401 / 403 | Invalid, expired, or missing API key | `Result.Failure` | "Invalid OpenCode Zen API key — check settings" |
| 429 | Rate limit exceeded | `Result.RateLimited` | "OpenCode Zen rate limit reached — wait a moment" |
| 500–599 | Service unavailable / server error | `Result.Failure` | "OpenCode Zen issue (HTTP {code}) — try again" |
| Timeout / Exception | Socket or network failure | `Result.Failure` | "Network error connecting to OpenCode Zen" |

---

## 4. Component Design & Code Structure

### 4.1 `AiClient.kt` (Modified — replaces Gemini logic directly, no interface/dispatch)

```kotlin
package com.musa.wordwise.network

import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object AiClient {

    /** Hardcoded stealth free model — WordWise's only supported model. */
    const val MODEL = "big-pickle"
    private const val ENDPOINT = "https://opencode.ai/zen/v1/chat/completions"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private const val SYSTEM_PROMPT =
        "You are a grammar and style correction assistant. " +
        "Return only the corrected text. " +
        "Preserve the original language and meaning exactly. " +
        "Do not add any explanations, commentary, or quotation marks."

    sealed class Result {
        data class Success(val text: String) : Result()
        data class RateLimited(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    private val sessionId: String by lazy { UUID.randomUUID().toString() }

    fun buildRequest(text: String, apiKey: String): Request {
        val payload = buildJsonObject {
            put("model", MODEL)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", SYSTEM_PROMPT) })
                add(buildJsonObject { put("role", "user"); put("content", text) })
            })
        }.toString()

        return Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("x-opencode-session", sessionId)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    fun parseResponse(responseBody: String, statusCode: Int): Result = when (statusCode) {
        200 -> parseContent(responseBody)
            ?.let { Result.Success(it) }
            ?: Result.Failure("No content returned from OpenCode Zen")
        401, 403 -> Result.Failure("Invalid OpenCode Zen API key — check settings")
        429 -> Result.RateLimited("OpenCode Zen rate limit reached — wait a moment")
        in 500..599 -> Result.Failure("OpenCode Zen issue (HTTP $statusCode) — try again")
        else -> Result.Failure("OpenCode Zen error (HTTP $statusCode): $responseBody")
    }

    private fun parseContent(jsonString: String): String? = try {
        Json.parseToJsonElement(jsonString)
            .jsonObject["choices"]
            ?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content?.trim()
    } catch (e: Exception) {
        null
    }
}
```

Files removed entirely (no longer needed once there's a single provider): the Gemini payload-builder/parser code, `AiProvider.kt` interface, `ProviderType.kt` sealed class, and any provider-router dispatch code, if these existed in the codebase from earlier scaffolding.

### 4.2 Key Storage (`ApiKeyRepository.kt`) — single key, not multi-key

```kotlin
class ApiKeyRepository(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, PREFS_NAME, /* ... master key ... */,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) = prefs.edit().putString(KEY_API_KEY, key).apply()
    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""
    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    companion object {
        private const val PREFS_NAME = "secret_keys"
        private const val KEY_API_KEY = "api_key_opencode_zen"
    }
}
```

No `ProviderType` parameter — there is only one key to store.

### 4.3 App Preferences (`Prefs.kt`) — no provider flag, no model selection

The `selected_provider` pref, `DEFAULT_PROVIDER`, `getSelectedProvider()`, and `setSelectedProvider()` are all deleted. There is nothing left to select. Any Gemini model-selection pref (e.g. `selected_gemini_model`) is deleted as well.

---

## 5. UI & Web Frontend Updates (`Views.kt` & `WwServer.kt`)

The provider selector (tabs/dropdown between Gemini and OpenCode Zen) is **removed**, not conditionally shown. Settings becomes:

```html
<div class="ww-lab" style="margin-bottom:10px;">AI MODEL</div>
<div class="ww-model-badge">big-pickle — Free Model (OpenCode Zen)</div>

<div class="ww-lab" style="margin-top:18px; margin-bottom:10px;">API KEY</div>
<input type="password" name="opencode_zen_key" placeholder="Enter your OpenCode Zen API key" />
<button class="ww-btn" hx-post="/api/key/opencode_zen" hx-target="#home-content">Save</button>
<p class="ww-hint">Get a free key from the OpenCode Zen dashboard.</p>
```

- `/api/settings/provider` route is deleted (nothing to switch between).
- `/api/key/opencode_zen` remains as the sole key-saving route; `/api/key/gemini` is deleted.
- The Gemini model-selection dropdown UI is deleted.

---

## 6. Security, TLS & Privacy

1. **API Key Storage:** OpenCode Zen key encrypted via AES-256-GCM / AES-256-SIV in `EncryptedSharedPreferences`. Never logged or exposed in stack traces.
2. **Network Policy:** Cleartext HTTP blocked for external domains; OpenCode Zen requests enforce HTTPS/TLS 1.3. The old `generativelanguage.googleapis.com` network security config entry (if allow-listed explicitly) can be removed once Gemini calls are gone.
3. **Sensitive Field Filtering:** `GrammarFixService` continues to check `isSensitiveField(node)` before dispatching text — this check is provider-agnostic and unaffected by the swap.

---

## 7. Migration Strategy (Existing Users)

This is the part that actually needs care, since existing users have a working Gemini key and no OpenCode Zen key yet.

- **On first launch post-update:** if a stored Gemini key exists (`api_key_gemini`) and no OpenCode Zen key exists, show a one-time in-app notice: *"WordWise now runs on a new, free, unlimited AI model. Your old Gemini key is no longer used — please add your OpenCode Zen key to continue using WordWise."* Link directly to the API key field.
- **Do not silently fail:** if `GrammarFixService` runs with no OpenCode Zen key configured, it should surface the existing "API key missing" Toast/notification path (already built for the missing-key case) rather than a generic error.
- **Cleanup:** delete the `api_key_gemini` entry from `EncryptedSharedPreferences` after showing the one-time migration notice (or on next successful save of an OpenCode Zen key) so stale key material doesn't linger. Delete the `selected_provider` and any Gemini-model prefs at the same time.
- **No fallback to Gemini:** once removed, there is no code path back to Gemini. This is a one-way migration.

---

## 8. Testing & Verification Specification

### 8.1 Unit Tests (`AiClientTest.kt`)
- `buildRequest_createsValidOpenAICompatiblePayload`: Verifies JSON contains `model: "big-pickle"`, system prompt, user prompt, `Authorization: Bearer <key>` header, and `x-opencode-session` header.
- `parseResponse_extractsContentFromChoices`: Verifies parsing of `choices[0].message.content`.
- `parseResponse_handlesHttp401And429`: Verifies 401 → failure, 429 → rate-limited variant.
- All existing Gemini-specific unit tests (payload shape, `candidates[0].content.parts[0].text` parsing, Gemini error mapping) are deleted, not left disabled.

### 8.2 Manual Verification Routine
1. Fresh install (or update from a Gemini-only build) — confirm the migration notice appears if an old Gemini key is present.
2. Enter a valid OpenCode Zen API key and save.
3. Open WhatsApp/Gmail, type `This is bad grammar?fix`.
4. Verify inline spinner appears and text is replaced with corrected text via `big-pickle`.
5. Remove/blank the API key and confirm the missing-key Toast still fires correctly.
6. Confirm no UI element anywhere still references Gemini or a provider choice.

---

## 9. File-by-File Implementation Checklist

| File | Status | Description of Changes |
|------|--------|------------------------|
| `com/musa/wordwise/network/AiClient.kt` | Modified (rewritten) | Becomes the OpenCode Zen client directly — hardcoded endpoint, hardcoded `big-pickle` model, OpenAI-compatible payload + parser |
| `com/musa/wordwise/network/*Gemini*` (payload builder/parser, wherever it lives) | **Deleted** | Gemini request/response logic removed entirely |
| `com/musa/wordwise/network/AiProvider.kt` / `ProviderType.kt` | **Not created / deleted if present** | No interface or sealed class needed for a single provider |
| `com/musa/wordwise/data/ApiKeyRepository.kt` | Modified | Single-key API (`api_key_opencode_zen`); Gemini key migration handled per §7, then old key removed |
| `com/musa/wordwise/data/Prefs.kt` | Modified | `selected_provider` and Gemini model-selection prefs deleted |
| `com/musa/wordwise/server/WwServer.kt` | Modified | `/api/settings/provider` and `/api/key/gemini` routes deleted; `/api/key/opencode_zen` retained |
| `com/musa/wordwise/server/Views.kt` | Modified | Provider selector UI removed; single API key field + fixed `big-pickle` model badge |
| `com/musa/wordwise/GrammarFixService.kt` | Modified | Reads the single API key and calls `AiClient` directly — no provider lookup |
| `docs/ARCHITECTURE.md` | Modified | Documents the single-provider OpenCode Zen architecture; Gemini section removed or moved to a "history" note |
| `res/xml/network_security_config.xml` (if applicable) | Modified | Remove Gemini domain allow-list entry once unused |

---

## 10. Known Issues & Resolutions

### MissingSessionID 400 — RESOLVED

**Symptom:** OpenCode Zen returned HTTP 400 `MissingSessionID` for free-tier models (`big-pickle`, `mimo-v2.5-free`).

**Root cause:** Free models require an `x-opencode-session` header carrying a client-generated UUID. Without it, the gateway rejects the request regardless of API key validity.

**Fix:** Added `header("x-opencode-session", sessionId)` to `AiClient.kt` request builder (stable per-process UUID via `lazy { UUID.randomUUID().toString() }`).

**Investigation ruled out:** HTTP-Referer, X-Title, and custom User-Agent headers were investigated (Mobile-Harness OpenRouter comparison, `docs/harnessreport.md`) and confirmed unnecessary for OpenCode Zen. No TUI-vs-non-TUI client detection exists at the protocol level.

### 429 Rate Limits — Open

Big Pickle is a shared free-tier model. HTTP 429 (`FreeUsageLimitError`) may occur under load. See Phase 2 notes in session file for retry/backoff approach.
