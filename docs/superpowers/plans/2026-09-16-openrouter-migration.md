# OpenRouter Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace OpenCode Zen with OpenRouter as WordWise's sole AI provider, using the `openrouter/free` auto-routing model.

**Architecture:** Direct provider swap — `AiClient` endpoint, model, and headers change. `ApiKeyRepository` key preference renamed. Settings UI and migration notice updated. No new interfaces, no provider abstraction.

**Tech Stack:** Kotlin, OkHttp 4.12.0, Ktor (CIO), EncryptedSharedPreferences, kotlinx.serialization

**Spec:** `docs/superpowers/specs/2026-09-16-openrouter-migration-design.md`

## Global Constraints
- Kotlin, targeting Android (minSdk from build.gradle.kts)
- OkHttp 4.12.0 for HTTP
- EncryptedSharedPreferences (AES-256-GCM / AES-256-SIV) for key storage
- No new dependencies
- No provider abstraction — single hardcoded provider only

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt` | Modify | New endpoint, model, headers; updated system prompt; updated error messages |
| `app/src/main/kotlin/com/musa/wordwise/data/ApiKeyRepository.kt` | Modify | New key pref name; add Zen migration helpers; delete Gemini migration helpers |
| `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt` | Modify | Update migration notice (Zen → OpenRouter) |
| `app/src/main/kotlin/com/musa/wordwise/server/Views.kt` | Modify | New model badge, key placeholder, link to openrouter.ai/keys, about screen text |
| `app/src/main/kotlin/com/musa/wordwise/server/WwServer.kt` | Modify | No route changes needed (generic `/api/key` route works as-is) |
| `app/src/test/java/com/musa/wordwise/network/AiClientTest.kt` | Modify | Add OpenRouter header/endpoint tests |
| `docs/ARCHITECTURE.md` | Modify | Document OpenRouter architecture |

---

### Task 1: Update AiClient Constants and Headers

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt`

**Interfaces:**
- Produces: `AiClient.MODEL = "openrouter/free"`, `AiClient.ENDPOINT`, `AiClient.HTTP_REFERER`, `AiClient.APP_TITLE`

- [ ] **Step 1: Update the MODEL constant**

Change `const val MODEL = "big-pickle"` to `const val MODEL = "openrouter/free"`

- [ ] **Step 2: Update the ENDPOINT constant**

Change `private const val ENDPOINT = "https://opencode.ai/zen/v1/chat/completions"` to `private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"`

- [ ] **Step 3: Add HTTP_REFERER and APP_TITLE constants**

Add after the ENDPOINT line:
```kotlin
private const val HTTP_REFERER = "https://github.com/musa-dabwe/WordWise"
private const val APP_TITLE = "WordWise"
```

- [ ] **Step 4: Remove sessionId and x-opencode-session header**

Delete the `sessionId` lazy property:
```kotlin
// DELETE this line:
private val sessionId: String by lazy { UUID.randomUUID().toString() }
```

Delete the import for `java.util.UUID`.

In the `fixGrammar` request builder, remove:
```kotlin
.header("x-opencode-session", sessionId)
```

- [ ] **Step 5: Add OpenRouter headers to request builder**

In the `fixGrammar` request builder, add after the `Authorization` header:
```kotlin
.header("HTTP-Referer", HTTP_REFERER)
.header("X-Title", APP_TITLE)
```

- [ ] **Step 6: Update the system prompt**

Change `GRAMMAR_SYSTEM_PROMPT` to:
```kotlin
private const val GRAMMAR_SYSTEM_PROMPT =
    "You are a grammar and style correction assistant. " +
    "Return only the corrected text. " +
    "Preserve the original language and meaning exactly. " +
    "Do not add any explanations, commentary, or quotation marks. " +
    "Never use em-dashes (—); use a comma, colon, or restructure the sentence instead."
```

- [ ] **Step 7: Update all error messages to reference OpenRouter**

In `executeRequest`, replace every "OpenCode Zen" string with "OpenRouter":
- `"No content returned from OpenCode Zen"` → `"No content returned from OpenRouter"`
- `"Invalid OpenCode Zen API key — check settings"` → `"Invalid OpenRouter API key — check settings"`
- `"OpenCode Zen error (HTTP ..."` → `"OpenRouter error (HTTP ..."`
- `"OpenCode Zen issue (HTTP ..."` → `"OpenRouter issue (HTTP ...)"`
- `"Network error connecting to OpenCode Zen"` → `"Network error connecting to OpenRouter"`

- [ ] **Step 8: Update the class KDoc**

Change the doc comment from "Singleton AI client for OpenCode Zen" to "Singleton AI client for OpenRouter".

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt
git commit -m "feat: migrate AiClient to OpenRouter (openrouter/free)"
```

---

### Task 2: Update ApiKeyRepository

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/data/ApiKeyRepository.kt`

**Interfaces:**
- Produces: `hasLegacyZenKey()`, `removeLegacyZenKey()`
- Consumes: nothing (standalone change)

- [ ] **Step 1: Update KEY_API_KEY constant**

Change `KEY_API_KEY` from `"api_key_opencode_zen"` to `"api_key_openrouter"`

- [ ] **Step 2: Add KEY_LEGACY_ZEN constant**

In the `companion object`, add:
```kotlin
private const val KEY_LEGACY_ZEN = "api_key_opencode_zen"
```

- [ ] **Step 3: Add legacy Zen migration methods**

Add these methods to the class:
```kotlin
fun hasLegacyZenKey(): Boolean = prefs.contains(KEY_LEGACY_ZEN)

fun removeLegacyZenKey() {
    prefs.edit().remove(KEY_LEGACY_ZEN).apply()
}
```

- [ ] **Step 4: Delete Gemini migration methods**

Delete `hasLegacyGeminiKey()` and `removeLegacyGeminiKey()` methods entirely.

Delete `KEY_LEGACY_GEMINI` from the companion object.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/data/ApiKeyRepository.kt
git commit -m "refactor: rename key pref to openrouter, add Zen migration helpers"
```

---

### Task 3: Update GrammarFixService Migration Notice

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt`

**Interfaces:**
- Consumes: `ApiKeyRepository.hasLegacyZenKey()`, `ApiKeyRepository.removeLegacyZenKey()`
- Consumes: `ApiKeyRepository.hasApiKey()`

- [ ] **Step 1: Rewrite showMigrationNoticeIfNeeded()**

Replace the entire method body:
```kotlin
private fun showMigrationNoticeIfNeeded() {
    val prefs = getSharedPreferences("wordwise_prefs", Context.MODE_PRIVATE)
    if (prefs.getBoolean("zen_migration_notice_shown", false)) return

    if (apiKeyRepository.hasLegacyZenKey() && !apiKeyRepository.hasApiKey()) {
        showToast(
            "WordWise now uses OpenRouter for AI corrections. " +
            "Your old OpenCode Zen key is no longer used — add your OpenRouter key in settings.",
            long = true
        )
        prefs.edit().putBoolean("zen_migration_notice_shown", true).apply()
        apiKeyRepository.removeLegacyZenKey()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt
git commit -m "feat: update migration notice from Zen to OpenRouter"
```

---

### Task 4: Update Settings UI (Views.kt)

**Files:**
- Modify: `app/src/main/kotlin/com/musa/wordwise/server/Views.kt`

**Interfaces:**
- Produces: updated HTML for home and about screens

- [ ] **Step 1: Update home screen key input placeholder**

Change `placeholder="Paste your OpenCode Zen API key here"` to `placeholder="Paste your OpenRouter API key here"`

- [ ] **Step 2: Update home screen key link**

Change `<a class="key-link" href="https://opencode.ai">Get a free key at OpenCode Zen →</a>` to `<a class="key-link" href="https://openrouter.ai/keys" target="_blank">Get a free key at OpenRouter →</a>`

- [ ] **Step 3: Update home screen model badge**

Change `<div class="ww-model-badge" ...>big-pickle — Free Model (OpenCode Zen)</div>` to `<div class="ww-model-badge" style="display:inline-block; padding:6px 14px; border-radius:8px; background:#f0f0f0; font-family:monospace; font-size:14px;">openrouter/free — Free Models Router (OpenRouter)</div>`

- [ ] **Step 4: Update about screen — AI description**

Change "using OpenCode Zen's free `big-pickle` model" to "using OpenRouter's free models router"

Change "Sends it to the **big-pickle** model via OpenCode Zen" to "Sends it to **OpenRouter**'s free models"

- [ ] **Step 5: Update about screen — Tech Stack AI entry**

Change `<strong>AI</strong> — <strong>OpenCode Zen</strong> API with your own free key.` to `<strong>AI</strong> — <strong>OpenRouter</strong> API with your own free key.`

- [ ] **Step 6: Update about screen — Security section**

Change `your OpenCode Zen key is stored` to `your OpenRouter key is stored`

Change `sent only to <code>opencode.ai</code> over TLS 1.3` to `sent only to <code>openrouter.ai</code> over TLS`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/musa/wordwise/server/Views.kt
git commit -m "feat: update settings UI for OpenRouter migration"
```

---

### Task 5: Update AiClient Tests

**Files:**
- Modify: `app/src/test/java/com/musa/wordwise/network/AiClientTest.kt`

**Interfaces:**
- Consumes: `AiClient.parseContent()`, `AiClient.MODEL`, `AiClient.buildRequest()` (if exposed)

- [ ] **Step 1: Add test for OpenRouter model constant**

```kotlin
@Test
fun `MODEL is openrouter_free`() {
    assertEquals("openrouter/free", AiClient.MODEL)
}
```

- [ ] **Step 2: Verify existing parseContent tests still pass**

Run existing tests — they should pass unchanged since response format is identical:
```bash
./gradlew test --tests "com.musa.wordwise.network.AiClientTest"
```

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/musa/wordwise/network/AiClientTest.kt
git commit -m "test: add OpenRouter model constant test"
```

---

### Task 6: Update Architecture Documentation

**Files:**
- Modify: `docs/ARCHITECTURE.md`

**Interfaces:**
- Produces: updated architecture documentation

- [ ] **Step 1: Update Overview section**

Change "using OpenCode Zen's free `big-pickle` model" to "using OpenRouter's free models router (`openrouter/free`)"

- [ ] **Step 2: Update Component Map — AiClient row**

Change description to: "Singleton managing a shared `OkHttpClient` (4.12.0) and the OpenRouter backend. Exposes `suspend fun fixGrammar(text: String, apiKey: String): Result` which switches to `Dispatchers.IO` internally and authenticates via the `Authorization: Bearer` header. Includes `HTTP-Referer` and `X-Title` headers for OpenRouter rankings."

- [ ] **Step 3: Update Flow Diagram**

Change `OCZ[OpenCode Zen API]` to `OR[OpenRouter API]`

- [ ] **Step 4: Update Key Design Decisions**

Change "Single provider: OpenCode Zen's free `big-pickle` model is the sole AI backend." to "Single provider: OpenRouter's free models router (`openrouter/free`) is the sole AI backend — auto-routes to available free models."

- [ ] **Step 5: Commit**

```bash
git add docs/ARCHITECTURE.md
git commit -m "docs: update architecture for OpenRouter migration"
```

---

### Task 7: Update OpenCode Zen Spec (Historical Reference)

**Files:**
- Modify: `docs/OPENCODE_ZEN_SPEC.md`

**Interfaces:**
- Produces: updated spec with historical note

- [ ] **Step 1: Add historical note to top of file**

Add at the very top of the file, before the existing title:
```markdown
> **Historical Document:** This spec describes the OpenCode Zen integration that has since been replaced by OpenRouter. See `docs/superpowers/specs/2026-09-16-openrouter-migration-design.md` for the current provider specification.

---

```

- [ ] **Step 2: Commit**

```bash
git add docs/OPENCODE_ZEN_SPEC.md
git commit -m "docs: mark OpenCode Zen spec as historical"
```
