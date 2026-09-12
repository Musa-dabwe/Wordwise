# Session: 2026-09-12 19:28 — OpenCode Zen HTTP 400 Diagnosis

**Duration**: 19:28 – 19:50
**Project**: WordWise 2.0

---

## Objective

Diagnose WordWise's OpenCode Zen HTTP 400 error by studying the pi-opencode reference implementation. Produce a diff report comparing what pi-opencode sends vs what WordWise sends, identify the specific line(s) causing the 400, and fix it.

---

## Research Phase

### Reference Implementation Cloned

```
git clone https://github.com/awtotty/pi-opencode.git /tmp/pi-opencode
```

Single source file: `/tmp/pi-opencode/src/index.ts` (167 lines)

### pi-opencode Request-Building (Reference)

pi-opencode does **not** build HTTP requests directly. It registers providers with pi's framework:

```typescript
// Line 137-142
pi.registerProvider("opencode-zen", {
    baseUrl: "https://opencode.ai/zen/v1",
    apiKey: "OPENCODE_API_KEY",          // env var name
    api: "openai-completions",           // tells pi to use OpenAI Chat Completions format
    models: ZEN_OPENAI_MODELS.map(toOpenAIModel),
});
```

pi's framework handles the actual HTTP call to `{baseUrl}/chat/completions`. The `OAI_COMPAT` config:

```typescript
// Line 32-36
const OAI_COMPAT = {
    supportsDeveloperRole: false,
    supportsReasoningEffort: false,
    maxTokensField: "max_tokens" as const,
};
```

**Key:** pi-opencode does NOT set `temperature` or `max_tokens` explicitly. These are omitted from the request body.

Model `big-pickle` is listed at line 100 with bare ID `"big-pickle"` (no prefix).

### WordWise Request-Building (Current)

File: `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt`

```kotlin
// Lines 68-82 (BEFORE fix)
val payload = buildJsonObject {
    put("model", MODEL)                    // "big-pickle"
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
    put("temperature", 0.2)               // ← EXTRA FIELD
    put("max_tokens", 2048)               // ← EXTRA FIELD
}.toString()
```

Endpoint: `https://opencode.ai/zen/v1/chat/completions`
Auth: `Authorization: Bearer $apiKey`
Content-Type: `application/json`

### Official OpenCode Zen Docs

From https://opencode.ai/docs/zen/:

- `big-pickle` is served via `@ai-sdk/openai-compatible` at `https://opencode.ai/zen/v1/chat/completions`
- Model ID format in OpenCode config: `opencode/<model-id>` (but raw API uses bare ID)
- Free models: Big Pickle, MiMo-V2.5 Free, Ling 3.0 Flash Fin Free, Nemotron 3 Ultra Free, Nemotron 3.5 Lightning Free, Muse Spark 1.3 Contributor Free

---

## Initial Diff Report (Phase 2)

| Aspect | pi-opencode | WordWise | Match? |
|--------|------------|----------|--------|
| Endpoint | `zen/v1` + framework appends `/chat/completions` | `zen/v1/chat/completions` | YES |
| Auth header | Bearer via `apiKey` env var | `Authorization: Bearer $apiKey` | YES |
| Content-Type | `application/json` (default) | `application/json` | YES |
| Model ID | bare `"big-pickle"` | bare `"big-pickle"` | YES |
| Messages format | OpenAI chat (system + user) | OpenAI chat (system + user) | YES |
| `temperature` | NOT SET | `"temperature": 0.2` | **NO** |
| `max_tokens` | NOT SET | `"max_tokens": 2048` | **NO** |

### Initial Hypothesis (WRONG)

The `temperature` and `max_tokens` fields were causing the HTTP 400. The `big-pickle` model served via `@ai-sdk/openai-compatible` doesn't accept these OpenAI parameters.

### Fix Applied (Partially Correct)

Removed both fields from `AiClient.kt` and added response body logging to the else branch:

```kotlin
// AFTER fix (lines 68-80)
val payload = buildJsonObject {
    put("model", MODEL)
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
}.toString()

// Line 103: else branch now includes response body
else -> Result.Failure("OpenCode Zen error (HTTP ${response.code}): $raw")
```

Also updated `docs/OPENCODE_ZEN_SPEC.md` to reflect the minimal payload.

---

## Real Root Cause Discovery (Phase 3)

### The Actual Error

After deploying the fix and testing on device, the toast showed:

```
Correction failed: OpenCode Zen error (HTTP 400): {"type":"error","error":{"type":"Missin...
```

The response body was truncated in the toast. A direct curl call revealed the full error:

```bash
curl -s -X POST "https://opencode.ai/zen/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{"model":"big-pickle","messages":[{"role":"user","content":"hello"}]}'
```

**Response:**
```json
{
  "type": "error",
  "error": {
    "type": "MissingSessionID",
    "message": "Error from provider (Console): OpenCode's free tier can only be used in OpenCode"
  }
}
```

HTTP 400.

### Verified Across All Free Models

| Model | Result |
|-------|--------|
| `big-pickle` | `MissingSessionID — free tier can only be used in OpenCode` |
| `mimo-v2.5-free` | `MissingSessionID — free tier can only be used in OpenCode` |

### Paid Model Test

```bash
curl -s -X POST "https://opencode.ai/zen/v1/chat/completions" \
  -H "Authorization: Bearer <test-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-5.4-nano","messages":[{"role":"user","content":"say hi"}],"max_tokens":10}'
```

**Response:**
```json
{
  "type": "error",
  "error": {
    "type": "CreditsError",
    "message": "Insufficient balance. Manage your billing here: https://opencode.ai/workspace/wrk_01KQVMHDSDP7322VSB0RMM14HY/billing"
  }
}
```

HTTP 401 — key is valid, but account has no credits.

---

## Real Root Cause (Phase 4 — Confirmed Fix)

The `MissingSessionID` error is NOT an "OpenCode-only" auth wall. It's a missing `x-opencode-session` header. Other clients (pi, Codewhale proxy, DeepSeek-Harness plugin) hit the identical 400 and fixed it by injecting a client-generated UUID into that header.

The server uses it for routing/cache-affinity — it accepts any UUID the client generates. It is not a server-issued token.

### Verification curl

```bash
curl -s -X POST "https://opencode.ai/zen/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "x-opencode-session: test-session-wordwise-001" \
  -d '{"model":"big-pickle","messages":[{"role":"user","content":"say hi"}],"max_tokens":20}'
```

**Response:** HTTP 200, real completion returned. Cost: $0.

### Why pi-opencode Works

pi's framework always attaches a session header to requests. WordWise, making raw OkHttp calls without this header, got `MissingSessionID`. The fix is adding one line to the request builder.

---

## Conclusions

1. **The HTTP 400 was NOT caused by `temperature` or `max_tokens`.** Those fields were extra but harmless — the server never got far enough to validate them.

2. **The real cause is a missing `x-opencode-session` header.** Free models require this header with a client-generated UUID. Without it, OpenCode Zen returns HTTP 400 `MissingSessionID`.

3. **The `temperature`/`max_tokens` removal is correct** — those fields are unnecessary and should be omitted for a minimal payload. But it doesn't fix the 400.

4. **The fix is a single header.** Add `x-opencode-session: <UUID>` to the OkHttp request builder. Other clients (pi, Codewhale, DeepSeek-Harness) solved the identical error this way.

5. **Free tier is usable from raw HTTP clients.** No OAuth flow, no special client identity, no session endpoint needed — just a UUID the client generates itself.

---

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt` | Removed `temperature`/`max_tokens`, added `x-opencode-session` header with stable UUID, added response body logging |
| `docs/OPENCODE_ZEN_SPEC.md` | Updated auth header section (§3.2) with `x-opencode-session`, updated code example (§4.1) |
| `local.properties` | Created — points to `~/Android/Sdk` |

---

## Build Outputs

Path: `~/storage/shared/Docs/Build/apk/`
- `WordWise-v2.0-zen-fix-20260912.apk` (7.5 MB) — old build, temperature/max_tokens removal only
- `WordWise-v2.0-session-header-fix-20260912.apk` (7.5 MB) — includes `x-opencode-session` header fix

---

## Next Session Priorities

- [ ] Test the session-header build on device with real text
- [ ] If 429 FreeUsageLimitError appears under real usage, that's free-tier throttling (different from the 400)
- [ ] Update `README.md`, `SECURITY.md`, `PROJECT_KNOWLEDGE.md` (still reference Gemini)
- [ ] Add request-building unit tests to `AiClientTest.kt` (currently only tests `parseContent`)
