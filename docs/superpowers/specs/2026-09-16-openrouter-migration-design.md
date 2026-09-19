# Specification: Migrate WordWise API Provider to OpenRouter (Free Models Router)

**Document Status:** Draft
**Author:** WordWise Engineering
**Date:** 16-09-2026
**Feature:** Replace OpenCode Zen with OpenRouter as the sole AI provider, hardcoded to auto-route free models

---

## 1. Executive Summary & Objectives

WordWise currently relies on OpenCode Zen's `big-pickle` model for grammar correction. This migration replaces OpenCode Zen entirely with **OpenRouter**, using their **Free Models Router** (`openrouter/free`) which auto-routes requests to available free models at zero cost.

### Key Highlights
- **Full Replacement, Not Addition:** OpenCode Zen support is removed — no dual-provider architecture, no provider toggle.
- **Auto-Routed Free Models:** The `openrouter/free` model ID tells OpenRouter to pick the best available free model for each request. No model selection UI needed.
- **OpenAI-Compatible Protocol:** Same `/v1/chat/completions` endpoint shape, minimal payload changes.
- **Prompt Optimization:** Updated system prompt includes instruction to never use em-dashes.
- **Single API Key:** `ApiKeyRepository` stores one encrypted OpenRouter key. Legacy OpenCode Zen key is migrated/cleared.

---

## 2. Architectural Comparison

### Before (OpenCode Zen only)
```
+---------------------+     +--------------------+     +-------------------+
| GrammarFixService   | --> | ApiKeyRepository   | --> | Prefs             |
+---------------------+     | (single key)       |     |                   |
          |                 +--------------------+     +-------------------+
          v
+--------------------------------------------------------------------------+
| AiClient (Singleton — OpenCode Zen only)                                 |
|  - Hardcoded URL: https://opencode.ai/zen/v1/chat/completions           |
|  - Hardcoded model: "big-pickle"                                         |
|  - x-opencode-session header (UUID)                                      |
+--------------------------------------------------------------------------+
```

### After (OpenRouter only — full replacement)
```
+---------------------+     +--------------------+     +-------------------+
| GrammarFixService   | --> | ApiKeyRepository   | --> | Prefs             |
+---------------------+     | (single key)       |     |                   |
          |                 +--------------------+     +-------------------+
          v
+--------------------------------------------------------------------------+
| AiClient (Singleton — OpenRouter only)                                   |
|  - Hardcoded URL: https://openrouter.ai/api/v1/chat/completions         |
|  - Hardcoded model: "openrouter/free"                                    |
|  - HTTP-Referer + X-Title headers                                        |
|  - No session UUID required                                              |
+--------------------------------------------------------------------------+
```

No router, no interface, no sealed class — `AiClient` *is* the OpenRouter client now.

---

## 3. OpenRouter Protocol & Technical Specification

### 3.1 Endpoint & Transport Configuration
- **Base Endpoint:** `https://openrouter.ai/api/v1`
- **Route:** `/chat/completions` (OpenAI-compatible REST protocol)
- **HTTP Method:** `POST`
- **Content-Type:** `application/json`
- **TLS Version:** TLS 1.3 / TLS 1.2 strict

### 3.2 Authentication & Headers
```http
Authorization: Bearer <OPENROUTER_API_KEY>
Content-Type: application/json
HTTP-Referer: https://github.com/musa-dabwe/WordWise
X-Title: WordWise
```

- **`Authorization`**: Bearer token — the user's OpenRouter API key.
- **`HTTP-Referer`**: App identifier used by OpenRouter for rankings and usage tracking. Set to the project GitHub URL.
- **`X-Title`**: Human-readable app name for OpenRouter's dashboard.
- **No session UUID required.** OpenRouter does not need an equivalent of `x-opencode-session`.

### 3.3 Model Specification
- **Hardcoded Model Identifier:** `openrouter/free`
- **Behavior:** OpenRouter auto-selects a free model from its available pool, filtering for request feature compatibility (text output, etc.). The routed model may vary between requests.
- **Cost:** $0 for prompt and completion tokens.

### 3.4 Request Payload
```json
{
  "model": "openrouter/free",
  "messages": [
    {
      "role": "system",
      "content": "You are a grammar and style correction assistant. Return only the corrected text. Preserve the original language and meaning exactly. Do not add any explanations, commentary, or quotation marks. Never use em-dashes (—); use a comma, colon, or restructure the sentence instead."
    },
    {
      "role": "user",
      "content": "i has a apple"
    }
  ]
}
```

**System prompt change:** Added "Never use em-dashes (—); use a comma, colon, or restructure the sentence instead." to the existing prompt.

**Omitted fields:** `temperature` and `max_tokens` are not sent — matching the current approach to avoid rejections from free-tier models.

### 3.5 Response Structure & Parsing
OpenRouter returns the same OpenAI-compatible response shape:

```json
{
  "id": "gen-1234567890",
  "object": "chat.completion",
  "created": 1718900000,
  "model": "openrouter/free",
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

**JSON Parsing Extraction Path:** `choices[0].message.content` → trim whitespace and leading/trailing quotes if present. **No change needed** — the existing `parseContent` function already handles this path.

### 3.6 Error Mapping Strategy
| HTTP Code | Condition | Mapped `Result` | User-Facing Message |
|-----------|-----------|------------------|----------------------|
| 200 | Valid JSON with `choices[0].message.content` | `Result.Success(text)` | "Text corrected" |
| 401 / 403 | Invalid, expired, or missing API key | `Result.Failure` | "Invalid OpenRouter API key — check settings" |
| 429 | Rate limit exceeded (free tier: 20 RPM, 50 req/day) | `Result.RateLimited` | "OpenRouter rate limit reached — try again shortly" |
| 500–599 | Service unavailable / server error | `Result.Failure` | "OpenRouter issue (HTTP {code}) — try again" |
| Timeout / Exception | Socket or network failure | `Result.Failure` | "Network error connecting to OpenRouter" |

**Note:** Free tier rate limits are stricter than OpenCode Zen. 429s may be more frequent. The existing single-retry with 3s backoff is retained.

---

## 4. Component Design & Code Changes

### 4.1 `AiClient.kt` — Rewrite Constants and Headers

```kotlin
object AiClient {

    /** Hardcoded free model router — WordWise's only supported model. */
    const val MODEL = "openrouter/free"
    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private const val HTTP_REFERER = "https://github.com/musa-dabwe/WordWise"
    private const val APP_TITLE = "WordWise"

    // DELETE: sessionId (x-opencode-session no longer needed)
    // DELETE: x-opencode-session header from request builder

    private const val GRAMMAR_SYSTEM_PROMPT =
        "You are a grammar and style correction assistant. " +
        "Return only the corrected text. " +
        "Preserve the original language and meaning exactly. " +
        "Do not add any explanations, commentary, or quotation marks. " +
        "Never use em-dashes (—); use a comma, colon, or restructure the sentence instead."
}
```

**Changes in `fixGrammar` request builder:**
- Remove `.header("x-opencode-session", sessionId)`
- Add `.header("HTTP-Referer", HTTP_REFERER)`
- Add `.header("X-Title", APP_TITLE)`

**Changes in error messages:**
- Replace all "OpenCode Zen" strings with "OpenRouter" in `Result.Failure` and `Result.RateLimited` messages.

### 4.2 `ApiKeyRepository.kt` — Single Key, New Preference Name

```kotlin
companion object {
    private const val PREFS_NAME = "secret_keys"
    private const val KEY_API_KEY = "api_key_openrouter"  // was api_key_opencode_zen
    private const val KEY_LEGACY_ZEN = "api_key_opencode_zen"  // for migration cleanup
}
```

**New methods for OpenCode Zen migration:**
```kotlin
fun hasLegacyZenKey(): Boolean = prefs.contains(KEY_LEGACY_ZEN)

fun removeLegacyZenKey() {
    prefs.edit().remove(KEY_LEGACY_ZEN).apply()
}
```

The existing `hasLegacyGeminiKey()` / `removeLegacyGeminiKey()` methods are **deleted** — the Gemini→Zen migration window is closed.

### 4.3 `GrammarFixService.kt` — Migration Notice Update

Update `showMigrationNoticeIfNeeded()`:
- Check for legacy **OpenCode Zen** key (not Gemini) + missing OpenRouter key
- Show notice: *"WordWise now uses OpenRouter for AI corrections. Your old OpenCode Zen key is no longer used — add your OpenRouter key in settings."*
- Clean up the old Zen key after showing the notice

The legacy Gemini migration notice code (`hasLegacyGeminiKey`, `removeLegacyGeminiKey`, and the `migration_notice_shown` pref check for Gemini) is deleted.

### 4.4 Settings UI (`Views.kt`)

```html
<div class="ww-lab" style="margin-bottom:10px;">AI MODEL</div>
<div class="ww-model-badge">openrouter/free — Free Models Router (OpenRouter)</div>

<div class="ww-lab" style="margin-top:18px; margin-bottom:10px;">API KEY</div>
<input type="password" name="openrouter_key" placeholder="Enter your OpenRouter API key" />
<button class="ww-btn" hx-post="/api/key/openrouter" hx-target="#home-content">Save</button>
<p class="ww-hint">Get a free key from <a href="https://openrouter.ai/keys" target="_blank">openrouter.ai/keys</a>.</p>
```

### 4.5 Server Routes (`WwServer.kt`)

- Rename `/api/key/opencode_zen` → `/api/key/openrouter`
- Delete any lingering `/api/key/gemini` route (if still present)
- Delete `/api/settings/provider` route (if still present)

---

## 5. Security, TLS & Privacy

1. **API Key Storage:** OpenRouter key encrypted via AES-256-GCM / AES-256-SIV in `EncryptedSharedPreferences`. Never logged or exposed in stack traces.
2. **Network Policy:** Cleartext HTTP blocked for external domains; OpenRouter requests enforce HTTPS/TLS 1.2+. Remove `opencode.ai` from network security config allow-list if present.
3. **Sensitive Field Filtering:** `GrammarFixService.isSensitiveField()` is provider-agnostic — no change needed.
4. **Key Scope:** OpenRouter keys have no access to OpenCode Zen and vice versa. Old Zen key is purged after migration.

---

## 6. Migration Strategy (Existing Users)

Existing users have a working OpenCode Zen key and no OpenRouter key.

- **On first launch post-update:** If a stored OpenCode Zen key exists (`api_key_opencode_zen`) and no OpenRouter key exists, show a one-time in-app notice: *"WordWise now uses OpenRouter for AI corrections. Your old OpenCode Zen key is no longer used — add your OpenRouter key in settings."* Link directly to the API key field.
- **Do not silently fail:** If `GrammarFixService` runs with no OpenRouter key, surface the existing "API key missing" Toast path.
- **Cleanup:** Delete the `api_key_opencode_zen` entry from `EncryptedSharedPreferences` after showing the one-time migration notice (or on next successful save of an OpenRouter key).
- **No fallback to OpenCode Zen:** This is a one-way migration. Once the Zen code is removed, there is no code path back.
- **Gemini migration code:** The old Gemini→Zen migration notice (`hasLegacyGeminiKey`, `removeLegacyGeminiKey`) is deleted — that migration window is long closed.

---

## 7. Testing & Verification Specification

### 7.1 Unit Tests (`AiClientTest.kt`)
- `parseContent_extractsContentFromValidJson` — existing test, no change (response format is identical)
- `parseContent_returnsNullForEmptyChoices` — existing test, no change
- `parseContent_returnsNullForMalformedJson` — existing test, no change
- `parseContent_stripsWrappingDoubleQuotes` — existing test, no change
- **New test:** `buildRequest_includesOpenRouterHeaders` — verify `HTTP-Referer` and `X-Title` headers are present, `x-opencode-session` is absent
- **New test:** `buildRequest_usesOpenRouterEndpointAndModel` — verify URL is `https://openrouter.ai/api/v1/chat/completions` and model is `openrouter/free`
- **Delete:** Any Gemini-specific tests (payload shape, `candidates[0].content.parts[0].text` parsing, Gemini error mapping)

### 7.2 Manual Verification Routine
1. Fresh install (or update from OpenCode Zen build) — confirm migration notice appears if old Zen key is present
2. Enter a valid OpenRouter API key and save
3. Open WhatsApp/Gmail, type `This is bad grammar?fix`
4. Verify inline spinner appears and text is replaced with corrected text via a free model
5. Remove/blank the API key and confirm the missing-key Toast still fires
6. Confirm no UI element anywhere still references OpenCode Zen or Gemini
7. Verify system prompt produces text without em-dashes (test with a sentence that would naturally use one)

---

## 8. File-by-File Implementation Checklist

| File | Status | Description of Changes |
|------|--------|------------------------|
| `com/musa/wordwise/network/AiClient.kt` | Modified | New endpoint, new model (`openrouter/free`), new headers (`HTTP-Referer`, `X-Title`), remove `x-opencode-session` + `sessionId`, update system prompt (no em-dashes), update error messages to "OpenRouter" |
| `com/musa/wordwise/data/ApiKeyRepository.kt` | Modified | Key preference renamed to `api_key_openrouter`, add `hasLegacyZenKey()` / `removeLegacyZenKey()`, delete `hasLegacyGeminiKey()` / `removeLegacyGeminiKey()` |
| `com/musa/wordwise/GrammarFixService.kt` | Modified | Update migration notice to check for Zen key (not Gemini), new notice text referencing OpenRouter |
| `com/musa/wordwise/server/WwServer.kt` | Modified | Route `/api/key/opencode_zen` → `/api/key/openrouter`, delete `/api/key/gemini` and `/api/settings/provider` if present |
| `com/musa/wordwise/server/Views.kt` | Modified | New model badge, new key input field name, link to openrouter.ai/keys |
| `com/musa/wordwise/network/AiClientTest.kt` | Modified | Add OpenRouter header/endpoint tests, delete Gemini tests |
| `docs/ARCHITECTURE.md` | Modified | Document OpenRouter architecture, remove OpenCode Zen references |
| `res/xml/network_security_config.xml` | Modified | Remove `opencode.ai` domain allow-list entry if present |

---

## 9. Known Risks & Mitigations

### Free Tier Rate Limits
**Risk:** OpenRouter free tier allows 20 RPM / 50 req/day (or 1000 req/day with $10+ lifetime credits). This is tighter than OpenCode Zen's limits.

**Mitigation:** The existing 429 retry with 3s backoff handles transient bursts. For sustained usage, users can purchase $10+ in OpenRouter credits to unlock 1000 req/day. The error message should hint at this: "OpenRouter rate limit reached — try again shortly (free tier: 50 req/day)".

### Model Variability
**Risk:** `openrouter/free` routes to different models each request. Response quality may vary.

**Mitigation:** Acceptable for grammar correction — all free models handle this task competently. No quality guarantee is needed beyond "corrected text returned".

### API Key Differentiation
**Risk:** Users may confuse OpenRouter keys with OpenCode Zen keys.

**Mitigation:** Settings UI clearly labels "OpenRouter API key" with a direct link to openrouter.ai/keys. Migration notice explicitly names both providers.
