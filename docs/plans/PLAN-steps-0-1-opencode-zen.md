# Implementation Plan: Steps 0 & 1 — Replace Gemini with OpenCode Zen

**Spec:** `OPENCODE_ZEN_SPEC.md` (revised full-replacement version)
**Scope:** Step 0 (Recon) + Step 1 (Add OpenCode Zen alongside Gemini for safe testing)
**Status:** Ready for manual review

---

## Step 0 — Recon (Complete)

### 0.1 Gemini Reference Inventory

All current Gemini references in the codebase, by file:

| File | Line(s) | Reference | What It Does |
|------|---------|-----------|--------------|
| `network/AiClient.kt` | 28, 36, 51, 81–84, 118 | `GEMINI_BASE_URL`, Gemini payload builder, Gemini candidate parser, Gemini error messages | The entire Gemini network call lives here — endpoint, auth header (`x-goog-api-key`), JSON payload (`system_instruction`/`contents`/`parts`), response parsing (`candidates[0].content.parts[0].text`), and error mapping |
| `data/Prefs.kt` | 15, 23, 26–31 | `DEFAULT_MODEL`, `GEMINI_MODELS` list | Stores which Gemini model is selected; list of 4 free-tier models |
| `data/ApiKeyRepository.kt` | 44, 48, 53 | `KEY_API_KEY_GEMINI` | Encrypted prefs key name `api_key_gemini` |
| `server/Views.kt` | 18–21, 121, 127, 133, 136–139, 147, 152–153 | `MODEL_NOTES` map, about page Gemini text, model dropdown, security section mentions | UI renders Gemini model dropdown, about page describes Gemini integration, security section references `generativelanguage.googleapis.com` |
| `server/WwServer.kt` | 134 | `Prefs.GEMINI_MODELS` validation | `/api/settings/model` route validates model against `GEMINI_MODELS` |
| `res/values/strings.xml` | 12 | `toast_api_key_missing` | Toast says "Add your Gemini API key in WordWise first" |

### 0.2 Spec Assumptions vs. Actual Codebase

| Assumption in Spec | Actual State | Action Needed |
|--------------------|--------------|---------------|
| "Gemini payload-builder/parser code" may be in a separate file | It's all inside `AiClient.kt` — no separate Gemini provider file | Delete/rewrite `AiClient.kt` directly |
| `AiProvider.kt` / `ProviderType.kt` may need deletion | **Do not exist** — no multi-provider scaffolding was started | Skip this deletion step |
| `selected_provider` pref exists | **Does not exist** — only `selected_model` | No provider pref to delete; just the model pref |
| Provider-selector UI may have been partially built | **Not built** — v1.0 has a model dropdown, not a provider toggle | Remove model dropdown, replace with fixed badge |
| `/api/settings/provider` route may exist | **Does not exist** — only `/api/settings/model` | Delete `/api/settings/model` instead |
| `/api/key/gemini` route may exist | **Does not exist** — only `/api/key` (single route, no provider param) | Rename/repurpose `/api/key` → `/api/key/opencode_zen` |
| Existing Gemini unit tests to delete | **No tests exist anywhere** in the project | No tests to delete; new tests must be written from scratch |
| `generativelanguage.googleapis.com` in network security config | **Not in the config** — only base cleartext block + localhost allow | No network config change needed |

### 0.3 File Inventory for Steps 0–1

Files that will be touched:

| File | Step 1 Action | Notes |
|------|---------------|-------|
| `network/AiClient.kt` | **Modified** — add `callOpenCodeZen()` alongside existing `fixGrammar()` | Dual path: old Gemini path stays, new OpenCode Zen path added |
| `data/ApiKeyRepository.kt` | **Modified** — add `api_key_opencode_zen` key slot | Keep `api_key_gemini` for now |
| `data/Prefs.kt` | Unchanged in Step 1 | Model selection stays Gemini for now; OpenCode Zen hardcodes `big-pickle` |
| `server/Views.kt` | **Modified** — add temporary OpenCode Zen key input field | Minimal UI, not final polish |
| `server/WwServer.kt` | **Modified** — add `/api/key/opencode_zen` route | Temporary route for key save |
| `GrammarFixService.kt` | **Modified** — add branching to call new path | Build flag or conditional to route through OpenCode Zen |
| `res/values/strings.xml` | Unchanged in Step 1 | Toast text update happens in Step 2 |

---

## Step 1 — Add OpenCode Zen Alongside Gemini (Temporary Dual Path)

### Goal
Get a working, testable OpenCode Zen code path *before* deleting Gemini. The app must remain fully functional on Gemini during this phase — OpenCode Zen is an opt-in alternative, not a replacement yet.

### 1.1 Modify `ApiKeyRepository.kt` — Add OpenCode Zen Key Slot

**What changes:**
- Add a second key constant `KEY_API_KEY_OPENCODE_ZEN = "api_key_opencode_zen"`
- Add methods to save/load the OpenCode Zen key
- Keep the existing single-arg methods working (they read/write `api_key_gemini`) so nothing breaks

**Approach — minimal and temporary:**
```kotlin
// New constants
private const val KEY_API_KEY_OPENCODE_ZEN = "api_key_opencode_zen"

// New methods (old ones stay untouched)
fun saveOpenCodeZenKey(key: String) {
    prefs.edit().putString(KEY_API_KEY_OPENCODE_ZEN, key).apply()
}

fun getOpenCodeZenKey(): String {
    return prefs.getString(KEY_API_KEY_OPENCODE_ZEN, "") ?: ""
}

fun hasOpenCodeZenKey(): Boolean = getOpenCodeZenKey().isNotBlank()
```

**Why not refactor to a provider parameter now?** Because the spec says to avoid speculative abstraction. We add the minimum needed for two keys, and the provider parameter refactoring happens in Step 3 when Gemini is removed and everything collapses to a single key.

**Verification:** App compiles, existing Gemini key path still works (no regression).

### 1.2 Modify `AiClient.kt` — Add OpenCode Zen Call Path

**What changes:**
- Add a new `suspend fun fixGrammarOpenCodeZen(text: String, apiKey: String): Result` function alongside the existing `fixGrammar()`
- This new function implements the spec §4.1 OpenCode Zen logic:
  - Hardcoded `MODEL = "big-pickle"`
  - Hardcoded `ENDPOINT = "https://api.opencode.zen/v1/chat/completions"`
  - `Authorization: Bearer <key>` header (not `x-goog-api-key`)
  - OpenAI-compatible payload (`messages` array with `system` + `user` roles)
  - Parse `choices[0].message.content` (not `candidates[0].content.parts[0].text`)
- The existing `fixGrammar()` method stays completely untouched

**New constants to add:**
```kotlin
private const val OPENCODE_ZEN_MODEL = "big-pickle"
private const val OPENCODE_ZEN_ENDPOINT = "https://api.opencode.zen/v1/chat/completions"
```

**New function (spec §4.1, adapted as a second method):**
```kotlin
suspend fun fixGrammarOpenCodeZen(text: String, apiKey: String): Result =
    withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", OPENCODE_ZEN_MODEL)
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
            put("temperature", 0.2)
            put("max_tokens", 2048)
        }.toString()

        val request = Request.Builder()
            .url(OPENCODE_ZEN_ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeOpenCodeZenRequest(request)
    }
```

**New response parser:**
```kotlin
private fun executeOpenCodeZenRequest(request: Request): Result {
    return try {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            when (response.code) {
                200 -> parseOpenCodeZenContent(raw)
                    ?.let { Result.Success(it) }
                    ?: Result.Failure("No content returned from OpenCode Zen")
                401, 403 -> Result.Failure("Invalid OpenCode Zen API key — check settings")
                429 -> Result.RateLimited("OpenCode Zen rate limit reached — wait a moment")
                in 500..599 -> Result.Failure("OpenCode Zen issue (HTTP ${response.code}) — try again")
                else -> Result.Failure("OpenCode Zen error (HTTP ${response.code})")
            }
        }
    } catch (e: Exception) {
        Result.Failure(e.message ?: "Network error connecting to OpenCode Zen")
    }
}

private fun parseOpenCodeZenContent(jsonString: String): String? = try {
    Json.parseToJsonElement(jsonString)
        .jsonObject["choices"]
        ?.jsonArray?.getOrNull(0)
        ?.jsonObject?.get("message")
        ?.jsonObject?.get("content")
        ?.jsonPrimitive?.content?.trim()
} catch (e: Exception) {
    null
}
```

**Key design decisions:**
- The shared `httpClient` (OkHttp singleton) is reused — no new OkHttpClient instance
- The shared `GRAMMAR_SYSTEM_PROMPT` is reused — same prompt, different wire format
- The shared `Result` sealed class is reused — same return type, different internal path
- `executeRequest()` (Gemini) and `executeOpenCodeZenRequest()` (OpenCode Zen) are separate private functions to keep the Gemini path untouched

**Verification:** File compiles, existing `fixGrammar()` method still works (no regression).

### 1.3 Modify `WwServer.kt` — Add Temporary Key Route

**What changes:**
- Add a new `post("/api/key/opencode_zen")` route that saves the OpenCode Zen key via `apiKeyRepository.saveOpenCodeZenKey(key)`
- The existing `post("/api/key")` route (Gemini) stays untouched

**New route (after the existing `/api/key` route):**
```kotlin
post("/api/key/opencode_zen") {
    val key = call.receiveParameters()["key"]?.trim().orEmpty()
    if (key.isEmpty()) {
        toast("API key cannot be empty")
    } else {
        apiKeyRepository.saveOpenCodeZenKey(key)
        call.response.header(
            "HX-Trigger",
            """{"ww-toast":"OpenCode Zen API key saved securely","ww-saved":true}"""
        )
        call.respond(HttpStatusCode.NoContent)
    }
}
```

**Verification:** App compiles, existing `/api/key` route still works (no regression).

### 1.4 Modify `Views.kt` — Add Temporary OpenCode Zen Key Field

**What changes:**
- Add a temporary OpenCode Zen API key input section below the existing Gemini key section in `homeScreen()`
- This is not final UI — just enough to enter and save a key for testing
- Include a note that this is the new provider being tested

**Temporary UI addition (append after the existing `</form>` in `homeScreen()`):**
```html
<div style="margin-top:24px; padding-top:18px; border-top:1px solid #eee;">
  <form hx-post="/api/key/opencode_zen" hx-swap="none">
    <div class="ww-lab" style="margin-bottom:10px;">OPENCODE ZEN API KEY (NEW)</div>
    <div class="key-wrap">
      <input type="password" name="key" placeholder="Enter your OpenCode Zen key" autocomplete="off" autocapitalize="off" spellcheck="false">
      <button type="button" class="key-eye" onclick="wwToggleKey(this)">SHOW</button>
    </div>
    <a class="key-link" href="https://opencode.zen">Get a free key at OpenCode Zen →</a>
    <button type="submit" class="ww-save" style="margin-top:18px;">SAVE OPENCODE ZEN KEY</button>
  </form>
</div>
```

**Also update the home screen header text** — the existing description mentions "using Google Gemini". Temporarily soften it:
- Change `using Google Gemini` to `using AI` (or similar neutral wording)

**Verification:** App compiles, home screen renders both key fields, no layout breakage.

### 1.5 Modify `GrammarFixService.kt` — Add Routing Logic

**What changes:**
- Add a simple check to decide which path to use
- If OpenCode Zen key is configured → use `AiClient.fixGrammarOpenCodeZen()`
- If not → fall back to existing `AiClient.fixGrammar()` (Gemini)
- This ensures the app still works for existing Gemini users while testing the new path

**Modified section in `onAccessibilityEvent()` (replace lines 108–109):**
```kotlin
// Replace the existing call:
//   val model = Prefs.getSelectedModel(this@GrammarFixService)
//   val result = AiClient.fixGrammar(textToFix, apiKey, model)
// With:
val openCodeZenKey = apiKeyRepository.getOpenCodeZenKey()
val result = if (openCodeZenKey.isNotEmpty()) {
    AiClient.fixGrammarOpenCodeZen(textToFix, openCodeZenKey)
} else {
    val model = Prefs.getSelectedModel(this@GrammarFixService)
    AiClient.fixGrammar(textToFix, apiKey, model)
}
```

**Key decision:** The routing is based on whether an OpenCode Zen key exists, not a user-facing toggle. If the user has saved an OpenCode Zen key, it's used. If not, Gemini is the fallback. This avoids building a settings toggle that will be deleted in Step 2 anyway.

**Verification:**
- With only Gemini key saved → Gemini path is used (no regression)
- With OpenCode Zen key saved → OpenCode Zen path is used
- With neither key → "API key missing" toast fires (existing behavior)

### 1.6 Manual Verification Checklist

Before moving to Step 2, confirm ALL of the following:

1. **App builds and installs** without errors
2. **Gemini path still works:** with a Gemini key saved (no OpenCode Zen key), type `?fix` in a text field → corrected text appears via Gemini
3. **OpenCode Zen path works:** save an OpenCode Zen key, type `?fix` → corrected text appears via `big-pickle`
4. **OpenCode Zen 401 handling:** save an invalid key, type `?fix` → "Invalid OpenCode Zen API key" error toast
5. **OpenCode Zen 429 handling:** if rate-limited (unlikely with `big-pickle` but test if possible) → rate-limited toast
6. **Missing key handling:** remove both keys, type `?fix` → "Add your Gemini API key" toast (existing behavior)
7. **Both key fields visible** on home screen, both save correctly
8. **No UI breakage** — layout renders properly on a real device

### 1.7 Rollback Point

After Step 1 is verified, commit this as a standalone commit. This is the **rollback point** — if OpenCode Zen's `big-pickle` model turns out to be unavailable or broken in practice, reverting this single commit restores the working Gemini-only app.

**Commit message:** `feat: add OpenCode Zen alongside Gemini for dual-path testing`

---

## What Happens Next (Steps 2–6, Not Part of This Plan)

For reference, after Step 1 is verified:
- **Step 2:** Delete all Gemini code (the old `fixGrammar()` path, `GEMINI_MODELS`, model dropdown, etc.)
- **Step 3:** Finalize `AiClient.kt` to spec §4.1 (single object, no dual path)
- **Step 4:** Migration handling for existing users (one-time notice)
- **Step 5:** Write `AiClientTest.kt` unit tests
- **Step 6:** Docs and cleanup pass

---

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| `big-pickle` model is actually rate-limited or unavailable | Can't verify Step 1 | Step 1 keeps Gemini as fallback — test OpenCode Zen path first, confirm it works before relying on it |
| OpenCode Zen API response format differs from spec §3.5 | Parsing fails silently (returns null) | The `parseOpenCodeZenContent()` catch block returns `null` → mapped to `Result.Failure("No content returned")` — visible error, not silent failure |
| `Authorization: Bearer` auth doesn't work (spec might be wrong about auth method) | 401 on every request | Test with a real key first — 401 maps to clear error message |
| Temporary dual-path code is hard to clean up in Step 2 | Technical debt | Keep the dual path minimal — new functions are additive, no existing code modified except `GrammarFixService.kt` routing logic |
