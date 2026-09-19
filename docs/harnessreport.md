# Mobile-Harness OpenRouter Gateway Analysis

**Date:** 2026-09-12
**Repo:** https://github.com/techjarves/Mobile-Harness (depth-1 clone, deleted after inspection)

---

## 1. Key Files Examined

| File | Role |
|------|------|
| `app/src/main/java/com/jarves/mh/network/ProviderApiClient.kt` | HTTP request builder, model discovery, connection validation |
| `app/src/main/java/com/jarves/mh/runtime/LocalFormatGateway.kt` | Loopback Anthropic-to-OpenAI format bridge for Claude Code |
| `app/src/main/java/com/jarves/mh/model/Models.kt` | `ProviderProtocol` enum, `ProviderKind` defaults |
| `app/build.gradle.kts` | Dependencies, build config |

---

## 2. OpenRouter Request Shape

### Base URL
- **Configured:** `https://openrouter.ai/api` (Model line 21, `ProviderKind.LLM_ROUTER`)
- **Discovery endpoint:** `{baseUrl}/v1/models`
- **Messages endpoint:** `{baseUrl}/v1/messages` (Anthropic Messages format, NOT OpenAI Chat Completions)

### Request Headers (ProviderApiClient.kt:93-103)

```java
// OpenRouter path (ProviderProtocol.OPENROUTER)
setRequestProperty("Accept", "application/json")
setRequestProperty("Content-Type", "application/json")
setRequestProperty("Authorization", "Bearer $apiKey")
// x-api-key and anthropic-version are SKIPPED for OpenRouter (line 100)
```

**What Mobile-Harness sends for OpenRouter:**
| Header | Value | Notes |
|--------|-------|-------|
| `Accept` | `application/json` | Standard |
| `Content-Type` | `application/json` | Standard |
| `Authorization` | `Bearer $apiKey` | Standard |

**What Mobile-Harness does NOT send for OpenRouter:**
| Header | Notes |
|--------|-------|
| `x-api-key` | Only sent for Anthropic direct / ANTHROPIC_GATEWAY (line 101) |
| `anthropic-version` | Only sent for Anthropic direct / ANTHROPIC_GATEWAY (line 102) |
| `HTTP-Referer` | **Never sent** — OpenRouter docs recommend this but MH omits it |
| `X-Title` | **Never sent** — OpenRouter docs recommend this but MH omits it |
| `User-Agent` | **Never sent** — no client identification at all |
| `x-opencode-session` | N/A (not an OpenCode provider) |

### Request Body (Validation)

```json
{
  "model": "anthropic/claude-sonnet-latest",
  "max_tokens": 1,
  "messages": [{"role": "user", "content": "Reply OK"}]
}
```

Note: Validation body uses `max_tokens` (OpenAI-compatible field), not `max_output_tokens`.

### Streaming

**LocalFormatGateway** converts Anthropic Messages format to OpenAI Chat Completions format for the upstream provider:

1. Receives Anthropic-format request from Claude Code on loopback
2. Converts to OpenAI format via `toOpenAi()` (line 82-141)
3. Sends non-streaming request to provider (line 85: `put("stream", false)`)
4. Converts response back to Anthropic format via `fromOpenAi()`
5. Emits Anthropic SSE stream events to Claude Code via `writeStream()`

**Key:** The upstream provider call is always non-streaming. The streaming emulation is done locally in the gateway.

---

## 3. Dependency Comparison

| Aspect | Mobile-Harness | WordWise |
|--------|---------------|----------|
| HTTP Client | `java.net.HttpURLConnection` (JDK built-in) | OkHttp 4.12.0 |
| JSON Parser | `org.json.JSONObject` (Android built-in) | `kotlinx.serialization.json` |
| Provider SDK | None (raw HTTP) | None (raw HTTP) |
| Coroutine support | Yes (kotlinx-coroutines-android 1.9.0) | Yes (kotlinx.coroutines) |
| Streaming | SSE emulation in LocalFormatGateway | Not implemented (non-streaming only) |

---

## 4. Header Comparison: Mobile-Harness vs WordWise

### Mobile-Harness OpenRouter Request
```
POST https://openrouter.ai/api/v1/messages
Accept: application/json
Content-Type: application/json
Authorization: Bearer <key>
```

### WordWise OpenCode Zen Request
```
POST https://opencode.ai/zen/v1/chat/completions
Content-Type: application/json
Authorization: Bearer <key>
x-opencode-session: <UUID>
```

### Headers Mobile-Harness Sends That WordWise Does Not
| Header | MH Purpose | WordWise Needs It? |
|--------|-----------|-------------------|
| `Accept: application/json` | Standard HTTP content negotiation | No — server assumes JSON for POST with Content-Type |
| `HTTP-Referer` | OpenRouter recommends it for app identification | No — OpenCode Zen doesn't use this |
| `X-Title` | OpenRouter recommends it for app identification | No — OpenCode Zen doesn't use this |
| `User-Agent` | HTTP standard client identification | Potentially — if server uses UA for rate-limiting |

### Headers WordWise Sends That Mobile-Harness Does Not
| Header | WordWise Purpose | MH Needs It? |
|--------|-----------------|-------------|
| `x-opencode-session` | OpenCode Zen free-tier routing | N/A — not an OpenCode provider |

---

## 5. Flagging Analysis

Mobile-Harness's approach to OpenRouter is relevant to WordWise's flagging question:

1. **No special client identification:** MH sends zero client-identification headers (no User-Agent, no HTTP-Referer, no X-Title). It treats OpenRouter as a bare API endpoint.

2. **No session affinity:** MH doesn't send any session identifier. Each request is stateless.

3. **Protocol mismatch is intentional:** MH uses Anthropic Messages format for OpenRouter (not OpenAI Chat Completions). This is because OpenRouter's `/v1/messages` endpoint accepts Anthropic format.

4. **LocalFormatGateway pattern:** MH acts as a format bridge — Claude Code talks Anthropic protocol, MH converts to OpenAI format for the upstream provider. This means the upstream provider sees OpenAI-shaped requests even though the client thinks it's talking Anthropic.

5. **No rate-limit headers:** MH doesn't set any headers to signal rate-limit awareness or retry behavior.

**Relevance to WordWise:** Mobile-Harness confirms that OpenRouter/OpenCode Zen providers don't require special client identification headers. The `x-opencode-session` header WordWise added is the only OpenCode-specific requirement. MH's lack of HTTP-Referer/X-Title on OpenRouter suggests these aren't strictly required by the gateway either.

---

## 6. Protocol Architecture Difference

| Aspect | Mobile-Harness | WordWise |
|--------|---------------|----------|
| Client speaks | Anthropic Messages | OpenAI Chat Completions |
| Provider endpoint | `/v1/messages` | `/chat/completions` |
| Format conversion | Gateway converts Anthropic ↔ OpenAI | Direct OpenAI format |
| Streaming | Local SSE emulation | Non-streaming only |

Mobile-Harness's `LocalFormatGateway` is a more complex architecture — it acts as a local proxy that translates between protocols. WordWise's approach is simpler: it speaks the provider's native format directly.

---

## 7. Conclusion

Mobile-Harness's OpenRouter implementation is a **minimal, bare-API approach** — no special headers, no session identifiers, no client identification. It relies entirely on the API key for authentication.

The only header Mobile-Harness sends that WordWise doesn't is `Accept: application/json`, which is optional and not the cause of any flagging.

WordWise's `x-opencode-session` header is an OpenCode-specific requirement that Mobile-Harness doesn't need because it targets OpenRouter, not OpenCode Zen.

**If WordWise is being flagged differently than expected, the cause is not in the request headers.** The `x-opencode-session` header fix is the correct and complete solution for the OpenCode Zen `MissingSessionID` 400 error.
