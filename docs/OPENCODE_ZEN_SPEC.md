# Specification: OpenCode Zen Integration & Multi-Provider Architecture

**Document Status:** Proposed / Under Review
**Author:** WordWise Engineering
**Target Release:** WordWise 2.0
**Feature:** OpenCode Zen AI Provider Support with Hardcoded `big-pickle` Model

---

## 1. Executive Summary & Objectives

WordWise currently relies exclusively on Google Gemini (`gemini-3.1-flash-lite`, `gemini-3.5-flash`, etc.) for text correction. While Gemini offers robust free-tier performance, relying on a single provider presents a single point of failure when rate limits (HTTP 429) or service outages occur.

This specification details the architecture and implementation plan for adding **OpenCode Zen** as a supported AI provider in WordWise, alongside a clean multi-provider refactoring of the network and settings layers.

### Key Highlights
- **Hardcoded Stealth Model (`big-pickle`):** OpenCode Zen's free stealth model `big-pickle` will be hardcoded as the default and sole model for OpenCode Zen. Because `big-pickle` has been free and unmetered since launch, it provides a stable, zero-cost, limit-free option for users without requiring complex model selection UI.
- **Pluggable Architecture:** Introduction of an `AiProvider` interface separating provider-specific payload creation and response parsing from the dispatch logic.
- **Secure Key Management:** Support for multi-provider API keys stored securely via `EncryptedSharedPreferences`.
- **Seamless UI Switching:** Interactive provider toggle in the htmx embedded web frontend with dynamic key management and model status display.

---

## 2. Architectural Comparison

### Current Architecture (Single Provider)
```
+---------------------+     +--------------------+     +-------------------+
| GrammarFixService   | --> | ApiKeyRepository   | --> | Prefs (Gemini)    |
+---------------------+     +--------------------+     +-------------------+
          |
          v
+--------------------------------------------------------------------------+
| AiClient (Singleton)                                                     |
|  - Hardcoded URL: https://generativelanguage.googleapis.com/...          |
|  - Gemini Json payload builder & candidate text parser                    |
+--------------------------------------------------------------------------+
```

### Target Architecture (Multi-Provider Architecture)
```
+---------------------+     +--------------------+     +-------------------+
| GrammarFixService   | --> | ApiKeyRepository   | --> | Prefs             |
+---------------------+     | (Gemini & OpenCode)|     | (Provider & Model)|
          |                 +--------------------+     +-------------------+
          v
+--------------------------------------------------------------------------+
| AiClient (Provider Router / Singleton Dispatcher)                        |
|   - Delegates request to active provider implementation                  |
+--------------------------------------------------------------------------+
           |                                        |
           v                                        v
+-----------------------+                +------------------------+
| GeminiProvider        |                | OpenCodeZenProvider    |
| - Endpoint: Google AI |                | - Endpoint: OpenCode   |
| - Custom Gemini JSON  |                | - OpenAI-compatible    |
| - Models: gemini-*    |                | - Model: "big-pickle"  |
+-----------------------+                +------------------------+
```

---

## 3. OpenCode Zen Protocol & Technical Specification

### 3.1 Endpoint & Transport Configuration
- **Base Endpoint:** `https://api.opencode.zen/v1` (with optional runtime fallback support for custom base URLs if configured).
- **Route:** `/chat/completions` (OpenAI-compatible REST protocol).
- **HTTP Method:** `POST`
- **Content-Type:** `application/json`
- **TLS Version:** TLS 1.3 / TLS 1.2 strict.

### 3.2 Authentication Header
OpenCode Zen uses standard HTTP Bearer token authentication:
```http
Authorization: Bearer <OPENCODE_ZEN_API_KEY>
```

### 3.3 Model Specification
- **Hardcoded Model Identifier:** `big-pickle`
- **Description:** OpenCode Zen's free stealth model.
- **Rationale:** Hardcoding `big-pickle` eliminates configuration friction for end users and guarantees access to OpenCode's stable free tier.

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
  ],
  "temperature": 0.2,
  "max_tokens": 2048
}
```

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
      "message": {
        "role": "assistant",
        "content": "I have an apple."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 42,
    "completion_tokens": 5,
    "total_tokens": 47
  }
}
```

**JSON Parsing Extraction Path:**
`choices[0].message.content` -> trim whitespace and trailing/leading quotes if present.

### 3.6 Error Mapping Strategy
| HTTP Code | OpenCode Zen Response / Condition | Mapped WordWise `Result` | User-Facing Message |
|-----------|-----------------------------------|--------------------------|---------------------|
| 200 | Valid response JSON with `choices[0].message.content` | `Result.Success(text)` | "Text corrected" |
| 401 / 403 | Invalid, expired, or missing API key | `Result.Failure` | "Invalid OpenCode Zen API key — check settings" |
| 429 | Rate limit exceeded | `Result.RateLimited` | "OpenCode Zen rate limit reached — wait a moment" |
| 500 - 599 | OpenCode Zen service unavailable / server error | `Result.Failure` | "OpenCode Zen issue (HTTP {code}) — try again" |
| Timeout / Exception | Socket or network connection failure | `Result.Failure` | "Network error connecting to OpenCode Zen" |

---

## 4. Component Design & Code Structure

### 4.1 Interface Abstraction (`AiProvider.kt`)

```kotlin
package com.musa.wordwise.network

import okhttp3.Request

sealed class ProviderType(val id: String, val displayName: String) {
    object Gemini : ProviderType("gemini", "Google Gemini")
    object OpenCodeZen : ProviderType("opencode_zen", "OpenCode Zen")

    companion object {
        fun fromId(id: String): ProviderType = when (id) {
            OpenCodeZen.id -> OpenCodeZen
            else -> Gemini
        }
    }
}

interface AiProvider {
    val providerType: ProviderType

    /** Build OkHttp Request for grammar correction */
    fun buildRequest(text: String, apiKey: String, model: String): Request

    /** Parse raw HTTP response body into Result */
    fun parseResponse(responseBody: String, statusCode: Int): AiClient.Result
}
```

### 4.2 OpenCode Zen Implementation (`OpenCodeZenProvider.kt`)

```kotlin
package com.musa.wordwise.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object OpenCodeZenProvider : AiProvider {

    override val providerType = ProviderType.OpenCodeZen

    /** Hardcoded stealth free model for OpenCode Zen */
    const val HARDCODED_MODEL = "big-pickle"
    private const val BASE_URL = "https://api.opencode.zen/v1/chat/completions"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private const val SYSTEM_PROMPT =
        "You are a grammar and style correction assistant. " +
        "Return only the corrected text. " +
        "Preserve the original language and meaning exactly. " +
        "Do not add any explanations, commentary, or quotation marks."

    override fun buildRequest(text: String, apiKey: String, model: String): Request {
        val payload = buildJsonObject {
            put("model", HARDCODED_MODEL)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", text)
                })
            })
            put("temperature", 0.2)
            put("max_tokens", 2048)
        }.toString()

        return Request.Builder()
            .url(BASE_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    override fun parseResponse(responseBody: String, statusCode: Int): AiClient.Result {
        return when (statusCode) {
            200 -> {
                val text = parseContent(responseBody)
                if (text != null) AiClient.Result.Success(text)
                else AiClient.Result.Failure("No content returned from OpenCode Zen")
            }
            401, 403 -> AiClient.Result.Failure("Invalid OpenCode Zen API key — check settings")
            429 -> AiClient.Result.RateLimited("OpenCode Zen rate limit reached — wait a moment")
            in 500..599 -> AiClient.Result.Failure("OpenCode Zen issue (HTTP $statusCode) — try again")
            else -> AiClient.Result.Failure("OpenCode Zen error (HTTP $statusCode)")
        }
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

### 4.3 Key Storage Update (`ApiKeyRepository.kt`)

`ApiKeyRepository` will be updated to store keys keyed by provider type:
```kotlin
fun saveApiKey(provider: ProviderType, key: String) {
    val prefKey = when (provider) {
        ProviderType.Gemini -> KEY_API_KEY_GEMINI
        ProviderType.OpenCodeZen -> KEY_API_KEY_OPENCODE_ZEN
    }
    prefs.edit().putString(prefKey, key).apply()
}

fun getApiKey(provider: ProviderType): String {
    val prefKey = when (provider) {
        ProviderType.Gemini -> KEY_API_KEY_GEMINI
        ProviderType.OpenCodeZen -> KEY_API_KEY_OPENCODE_ZEN
    }
    return prefs.getString(prefKey, "") ?: ""
}

companion object {
    private const val PREFS_NAME = "secret_keys"
    private const val KEY_API_KEY_GEMINI = "api_key_gemini"
    private const val KEY_API_KEY_OPENCODE_ZEN = "api_key_opencode_zen"
}
```

### 4.4 App Preferences (`Prefs.kt`)

```kotlin
private const val KEY_PROVIDER = "selected_provider"
const val DEFAULT_PROVIDER = "gemini"

fun getSelectedProvider(context: Context): ProviderType {
    val id = prefs(context).getString(KEY_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER
    return ProviderType.fromId(id)
}

fun setSelectedProvider(context: Context, provider: ProviderType) {
    prefs(context).edit().putString(KEY_PROVIDER, provider.id).apply()
}
```

---

## 5. UI & Web Frontend Updates (`Views.kt` & `WwServer.kt`)

### 5.1 Provider Selection UI
The home screen will feature a Provider Selector component allowing seamless switching between **Google Gemini** and **OpenCode Zen**:

1. **Provider Tabs / Dropdown:**
   - Option 1: **Google Gemini** (Selectable Gemini models: `gemini-3.1-flash-lite`, etc.)
   - Option 2: **OpenCode Zen** (Hardcoded model badge: `big-pickle` - Stealth Free Model)

2. **OpenCode Zen Settings Fragment:**
   - Dedicated API Key Input field for OpenCode Zen.
   - Information callout: *"OpenCode Zen uses stealth model big-pickle (Free & Unlimited)."*
   - Link to OpenCode Zen dashboard for key generation.

```html
<div class="ww-lab" style="margin-bottom:10px;">AI PROVIDER</div>
<div style="display:flex; gap:10px; margin-bottom:18px;">
  <button class="ww-btn ${if (activeProvider == ProviderType.Gemini) "active" else ""}"
          hx-post="/api/settings/provider?p=gemini" hx-target="#home-content">
    Google Gemini
  </button>
  <button class="ww-btn ${if (activeProvider == ProviderType.OpenCodeZen) "active" else ""}"
          hx-post="/api/settings/provider?p=opencode_zen" hx-target="#home-content">
    OpenCode Zen
  </button>
</div>
```

When OpenCode Zen is selected as the active provider:
- The Model Selection card displays a fixed non-editable row:
  `big-pickle — Hardcoded Stealth Model (Free)`

---

## 6. Security, TLS & Privacy

1. **API Key Isolation:**
   - OpenCode Zen keys are encrypted via AES-256-GCM / AES-256-SIV in `EncryptedSharedPreferences`.
   - Never logged or exposed in stack traces.
2. **Network Policy:**
   - Cleartext HTTP traffic is blocked for external domains.
   - OpenCode Zen requests enforce HTTPS / TLS 1.3.
3. **Sensitive Field Filtering:**
   - `GrammarFixService` checks `isSensitiveField(node)` before dispatching text to OpenCode Zen. Password and PIN fields are never inspected.

---

## 7. Backward Compatibility & Migration Strategy

- **Default State:** Defaults to `ProviderType.Gemini` for existing users.
- **Key Migration:** Existing stored Gemini keys in `secret_keys.xml` under `api_key_gemini` remain untouched.
- **Fallback:** If OpenCode Zen is selected but its API key is missing, `GrammarFixService` alerts the user via Toast ("OpenCode Zen API key missing — configure in WordWise").

---

## 8. Testing & Verification Specification

### 8.1 Unit Tests (`OpenCodeZenProviderTest.kt`)
- `buildRequest_createsValidOpenAICompatiblePayload`: Verifies JSON structure contains `model: "big-pickle"`, system prompt, user prompt, and `Authorization: Bearer <key>` header.
- `parseResponse_extractsContentFromChoices`: Verifies parsing of JSON `choices[0].message.content`.
- `parseResponse_handlesHttp401And429`: Verifies mapping of 401 to failure and 429 to rate limit variant.

### 8.2 Manual Verification Routine
1. Select **OpenCode Zen** in WordWise Settings.
2. Enter valid OpenCode Zen API key and save.
3. Open WhatsApp/Gmail, type `This is bad grammar?fix`.
4. Verify inline spinner appears and text is replaced with corrected text via OpenCode Zen's `big-pickle` model.

---

## 9. File-by-File Implementation Checklist

| File | Status | Description of Changes |
|------|--------|------------------------|
| `com/musa/wordwise/network/AiProvider.kt` | New File | Interface for AI providers and `ProviderType` sealed class |
| `com/musa/wordwise/network/GeminiProvider.kt` | New File | Extracted Gemini provider implementation |
| `com/musa/wordwise/network/OpenCodeZenProvider.kt` | New File | OpenCode Zen provider with hardcoded `big-pickle` model |
| `com/musa/wordwise/network/AiClient.kt` | Modified | Refactored into dispatcher routing requests to active provider |
| `com/musa/wordwise/data/ApiKeyRepository.kt` | Modified | Multi-key support (`api_key_gemini`, `api_key_opencode_zen`) |
| `com/musa/wordwise/data/Prefs.kt` | Modified | Added `KEY_PROVIDER` getter/setter and defaults |
| `com/musa/wordwise/server/WwServer.kt` | Modified | Added routes `/api/settings/provider`, `/api/key/opencode_zen` |
| `com/musa/wordwise/server/Views.kt` | Modified | Provider selector UI, conditional model card for `big-pickle` |
| `com/musa/wordwise/GrammarFixService.kt` | Modified | Read active provider and API key, delegate to `AiClient` |
| `docs/ARCHITECTURE.md` | Modified | Document multi-provider architecture and OpenCode Zen |

---
