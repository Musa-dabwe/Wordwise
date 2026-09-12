# Implementation Plan: Steps 2–6 — Complete the OpenCode Zen Migration

**Spec:** `docs/OPENCODE_ZEN_SPEC.md` (revised full-replacement version)
**Prerequisite:** Steps 0–1 applied and verified (dual-path is live, OpenCode Zen `big-pickle` confirmed working against real API)
**Scope:** Remove Gemini entirely, finalize architecture, add migration notice, write tests, docs cleanup

---

## Current State After Step 1

Every file below has temporary dual-path code from Step 1 that must be collapsed or deleted:

| File | What's Dual/Temporary | Target State (Spec §) |
|------|----------------------|----------------------|
| `AiClient.kt` (224 lines) | Gemini path (lines 28–147) + OpenCode Zen path (lines 149–223) coexist | Single OpenCode Zen object (spec §4.1) — delete lines 28–147 entirely |
| `ApiKeyRepository.kt` (70 lines) | `saveApiKey()`/`getApiKey()` (Gemini) + `saveOpenCodeZenKey()`/`getOpenCodeZenKey()` (OCZ) | Single `saveApiKey()`/`getApiKey()` backed by `api_key_opencode_zen` (spec §4.2) |
| `Prefs.kt` (50 lines) | `GEMINI_MODELS`, `DEFAULT_MODEL`, `getSelectedModel()`, `setSelectedModel()` | Delete all model-selection code; keep only theme prefs (spec §4.3) |
| `GrammarFixService.kt` (256 lines) | Dual key check (lines 90–101) + dual routing (lines 114–119) | Single key check, single `AiClient.fixGrammar()` call (spec §4.1) |
| `WwServer.kt` (176 lines) | `/api/key` (Gemini) + `/api/key/opencode_zen` + `/api/settings/model` routes | Single `/api/key` route for OCZ; delete model route (spec §5) |
| `Views.kt` (185 lines) | Two key forms, Gemini model dropdown, Gemini about-screen text | Single API key field, fixed `big-pickle` badge, rewritten about screen (spec §5) |
| `strings.xml` | `toast_api_key_missing` says "Gemini" | Update to OpenCode Zen |

---

## Step 2 — Remove Gemini Entirely

This is the delete step. Every Gemini code path, pref, UI element, and route is removed. Nothing Gemini-related survives except the migration-notice string (Step 4) and the architecture history note (Step 6).

### 2.1 `AiClient.kt` — Delete Gemini Path

**Delete lines 28–147** (the entire Gemini block):
- Remove `GEMINI_BASE_URL` constant
- Remove `fixGrammar()` suspend function
- Remove `buildRequestBody()` private function
- Remove `executeRequest()` private function
- Remove `parseCandidateText()` private function
- Remove `parseErrorMessage()` private function

**Keep lines 149–223** (the OpenCode Zen block), but:
- Rename `fixGrammarOpenCodeZen()` → `fixGrammar()` (it's the only path now)
- Remove the `// Temporary dual-path additions` comment block — it's permanent now, not temporary
- The `OPENCODE_ZEN_MODEL`, `OPENCODE_ZEN_ENDPOINT`, `GRAMMAR_SYSTEM_PROMPT`, `httpClient`, `JSON_MEDIA_TYPE`, and `Result` sealed class all stay

**Update the class-level KDoc** (line 27–33):
```kotlin
/**
 * Singleton AI client for OpenCode Zen.
 *
 * OkHttpClient is shared across all calls to reuse the connection pool.
 * The API key is sent via the Authorization header — never in the URL —
 * so it cannot leak into request logs.
 */
```

**Verify:** `grep -rn "gemini\|Gemini\|generativelanguage\|x-goog-api-key" AiClient.kt` returns zero hits.

### 2.2 `ApiKeyRepository.kt` — Collapse to Single Key

**Current:** two key slots (`api_key_gemini` + `api_key_opencode_zen`), two pairs of get/save methods.

**Target:** single key slot, single pair of methods.

1. Rename `saveOpenCodeZenKey()` → `saveApiKey()`
2. Rename `getOpenCodeZenKey()` → `getApiKey()`
3. Delete `hasOpenCodeZenKey()` (unused in the new architecture)
4. Delete the old `saveApiKey()`/`getApiKey()` methods (lines 43–49) that read/write `api_key_gemini`
5. In `companion object`: delete `KEY_API_KEY_GEMINI`, keep `KEY_API_KEY_OPENCODE_ZEN`
6. Delete the `TODO(step-2)` comment block

**Verify:** `grep -rn "gemini\|GEMINI" ApiKeyRepository.kt` returns zero hits.

### 2.3 `Prefs.kt` — Delete Model Selection

**Delete:**
- `KEY_MODEL` constant (line 20)
- `DEFAULT_MODEL` constant (line 23)
- `GEMINI_MODELS` list (lines 27–32)
- `getSelectedModel()` function (lines 37–38)
- `setSelectedModel()` function (lines 40–42)
- The Gemini reference in the KDoc (line 15)

**Keep:**
- `KEY_THEME`, `DEFAULT_THEME`, `getTheme()`, `setTheme()` — theme selection is unrelated to the provider swap

**Verify:** `grep -rn "gemini\|Gemini\|GEMINI\|model\|Model" Prefs.kt` returns only the `prefs` function name (false positive on "model" substring).

### 2.4 `GrammarFixService.kt` — Collapse Dual Path

**Replace lines 90–119** (the dual key check + dual routing block):

```kotlin
// --- Single key check (OpenCode Zen only, per spec §4.1) ---
val apiKey = apiKeyRepository.getApiKey()
if (apiKey.isEmpty()) {
    showToast(getString(R.string.toast_api_key_missing), long = true)
    source.safeRecycle()
    return
}
```

**Replace the routing block (lines 114–119):**
```kotlin
val result = AiClient.fixGrammar(textToFix, apiKey)
```

**Remove:**
- `import com.musa.wordwise.data.Prefs` (line 21) — no longer needed here
- The `val model = Prefs.getSelectedModel(...)` line
- The `val openCodeZenKey = ...` line
- The `val geminiApiKey = ...` line
- The `if (openCodeZenKey.isNotEmpty())` branching
- All `TODO(step-2/3)` comments

**Verify:** `grep -rn "gemini\|Gemini\|Prefs\." GrammarFixService.kt` returns zero hits (except possibly the Prefs import if not cleaned up).

### 2.5 `WwServer.kt` — Delete Gemini Routes

**Delete the old `/api/key` route** (lines 110–122) — this was the Gemini key-saving route.

**Rename `/api/key/opencode_zen` → `/api/key`** — this becomes the sole key route:
```kotlin
post("/api/key") {
    val key = call.receiveParameters()["key"]?.trim().orEmpty()
    if (key.isEmpty()) {
        toast("API key cannot be empty")
    } else {
        apiKeyRepository.saveApiKey(key)
        call.response.header(
            "HX-Trigger",
            """{"ww-toast":"API key saved securely","ww-saved":true}"""
        )
        call.respond(HttpStatusCode.NoContent)
    }
}
```

**Delete the `/api/settings/model` route** (lines 150–157) — no model selection exists anymore.

**Delete the `/api/status` route's `hasKey` check** (lines 99–106) — update it to call `apiKeyRepository.getApiKey()` (the new single method):
```kotlin
get("/api/status") {
    val enabled = isServiceEnabled(app)
    val hasKey = apiKeyRepository.getApiKey().isNotEmpty()
    call.respondText(
        """{"enabled":$enabled,"hasKey":$hasKey}""",
        ContentType.Application.Json
    )
}
```

**Delete the `/screens/home` route's `apiKeyRepository.getApiKey()` call** (line 88) — update to use the new single method (same call, just verify it works with the renamed method).

**Delete `import com.musa.wordwise.data.Prefs`** (line 15) — no longer needed in this file.

**Delete the TODO comments** (lines 124–126).

**Verify:** `grep -rn "gemini\|Gemini\|GEMINI_MODELS\|/api/settings/model" WwServer.kt` returns zero hits.

### 2.6 `Views.kt` — Delete Gemini UI

**Delete `MODEL_NOTES` map** (lines 17–22) — no model dropdown exists.

**Replace `homeScreen()` method** — remove the Gemini key form (lines 38–46) and the temporary dual-path OpenCode Zen form (lines 48–61). Replace with a single key form matching spec §5:

```html
<form hx-post="/api/key" hx-swap="none">
  <div class="ww-lab" style="margin-bottom:10px;">API KEY</div>
  <div class="key-wrap">
    <input id="key-input" type="password" name="key" value="${esc(key)}" placeholder="Paste your OpenCode Zen API key here" autocomplete="off" autocapitalize="off" spellcheck="false">
    <button type="button" class="key-eye" onclick="wwToggleKey(this)">SHOW</button>
  </div>
  <a class="key-link" href="https://opencode.ai">Get a free key at OpenCode Zen →</a>
  <button id="save-btn" type="submit" class="ww-save" style="margin-top:18px;">SAVE API KEY</button>
</form>
```

**Delete the `modelCard()` method** (lines 84–106) and its `<div id="model-card">` in `homeScreen()` (lines 63–65). Replace with a fixed model badge:

```html
<div>
  <div class="ww-lab" style="margin-bottom:10px;">AI MODEL</div>
  <div class="ww-model-badge" style="display:inline-block; padding:6px 14px; border-radius:8px; background:#f0f0f0; font-family:monospace; font-size:14px;">big-pickle — Free Model (OpenCode Zen)</div>
</div>
```

**Delete the `import com.musa.wordwise.data.Prefs`** (line 12) — no longer needed.

**Rewrite `aboutScreen()`** — replace all Gemini references with OpenCode Zen:
- Line 136: "using Google Gemini" → "using OpenCode Zen's free `big-pickle` model"
- Lines 141–142: "your chosen **Gemini** model" → "the **big-pickle** model via OpenCode Zen"
- Lines 147–155: Delete the entire "Models" section and Gemini model table
- Line 162: "Google **Gemini** API" → "**OpenCode Zen** API"
- Line 167: "your Gemini key" → "your OpenCode Zen key"
- Line 168: "generativelanguage.googleapis.com" → "opencode.ai"

**Verify:** `grep -rn "gemini\|Gemini\|generativelanguage\|GEMINI\|modelCard\|MODEL_NOTES" Views.kt` returns zero hits.

### 2.7 `strings.xml` — Update Toast Text

**Line 12:** Change:
```xml
<string name="toast_api_key_missing">Add your Gemini API key in WordWise first</string>
```
To:
```xml
<string name="toast_api_key_missing">Add your OpenCode Zen API key in WordWise first</string>
```

**Line 16:** Change:
```xml
<string name="warning_large_text">Large text detected — this may use more of your free quota</string>
```
To:
```xml
<string name="warning_large_text">Large text detected — this may take longer to correct</string>
```

(Since OpenCode Zen is unmetered, "free quota" is no longer accurate.)

### 2.8 Verification Gate — Step 2

Before moving to Step 3, confirm:

1. `grep -rn "gemini\|Gemini\|generativelanguage\|x-goog-api-key\|GEMINI" --include="*.kt"` returns **zero hits** in source code
2. `grep -rn "gemini\|Gemini" --include="*.xml"` returns **zero hits** in resources
3. Build compiles without errors
4. The app installs and `?fix` works through the OpenCode Zen path
5. The missing-key toast shows "OpenCode Zen" not "Gemini"
6. No model dropdown visible anywhere
7. Single API key field on home screen
8. About screen references OpenCode Zen, not Gemini

---

## Step 3 — Finalize `AiClient.kt` and Settings UI

This step is mostly cosmetic cleanup after Step 2's deletions. The architecture already matches spec §4.1 after Step 2 — this step just polishes it.

### 3.1 `AiClient.kt` — Verify Spec Compliance

After Step 2, `AiClient.kt` should already be a single OpenCode Zen object. Verify it matches spec §4.1 exactly:

- ✅ `const val MODEL = "big-pickle"` (or the renamed `OPENCODE_ZEN_MODEL`)
- ✅ `private const val ENDPOINT = "https://opencode.ai/zen/v1/chat/completions"`
- ✅ `Authorization: Bearer <key>` header
- ✅ `messages` array with `system` + `user` roles
- ✅ `parseOpenCodeZenContent()` parses `choices[0].message.content`
- ✅ Error mapping matches spec §3.6 table

**One optional rename for clarity:** rename `OPENCODE_ZEN_MODEL` → `MODEL` and `OPENCODE_ZEN_ENDPOINT` → `ENDPOINT` since there's only one provider now. The spec uses `MODEL` and `ENDPOINT`. Not required, but matches the spec.

### 3.2 `Views.kt` — Verify Settings UI Matches Spec §5

The home screen should now show:
1. Status pill (service active/paused)
2. Single API key field (labeled "API KEY", posts to `/api/key`)
3. Fixed model badge ("big-pickle — Free Model (OpenCode Zen)")
4. Theme picker
5. How-to-use steps

No model dropdown. No provider toggle. No Gemini references.

### 3.3 Verification Gate — Step 3

1. Build compiles
2. Home screen matches spec §5 layout exactly
3. About screen matches spec §5 (no Gemini, no model table)

---

## Step 4 — Migration Handling for Existing Users

This is the highest-risk step. Existing users have a working Gemini key and no OpenCode Zen key. The transition must be graceful.

### 4.1 Migration Notice Logic

**Where:** `GrammarFixService.kt` — on first `?fix` trigger after update, or on app startup.

**Logic (spec §7):**
1. Check: does `api_key_gemini` exist in `EncryptedSharedPreferences`?
2. Check: does `api_key_opencode_zen` NOT exist?
3. If both conditions true → show one-time migration notice Toast

**Implementation approach:** Add a migration check in `GrammarFixService.onServiceConnected()` (runs once when the service starts). Use a separate SharedPreferences flag (`migration_notice_shown`) to ensure it only shows once.

```kotlin
override fun onServiceConnected() {
    super.onServiceConnected()
    Log.d(TAG, "Service connected!")
    showMigrationNoticeIfNeeded()
    showToast(getString(R.string.toast_service_ready))
}

private fun showMigrationNoticeIfNeeded() {
    val prefs = getSharedPreferences("wordwise_prefs", Context.MODE_PRIVATE)
    if (prefs.getBoolean("migration_notice_shown", false)) return

    // Check if old Gemini key exists but no OpenCode Zen key
    val secretPrefs = EncryptedSharedPreferences (or use ApiKeyRepository internals)
    // ... check api_key_gemini exists, api_key_opencode_zen does not

    if (oldKeyExists && !newKeyExists) {
        showToast("WordWise now uses a new free AI model. Your old Gemini key is no longer used — add your OpenCode Zen key in settings.", long = true)
        prefs.edit().putBoolean("migration_notice_shown", true).apply()
    }
}
```

**Complication:** `ApiKeyRepository` currently doesn't expose a way to check if the old Gemini key exists (we deleted `getApiKey()` that reads `api_key_gemini` in Step 2). Two options:

- **Option A (recommended):** Before deleting the Gemini key methods in Step 2.6, add a `hasLegacyGeminiKey(): Boolean` method that checks `prefs.contains("api_key_gemini")`. Use this in the migration check, then delete it after the migration notice is shown.
- **Option B:** Access the encrypted prefs directly in `GrammarFixService` to check for the old key. More invasive, less clean.

**Recommended:** Option A. Add `hasLegacyGeminiKey()` to `ApiKeyRepository` in Step 2.2, use it in Step 4.1, delete it in Step 4.3.

### 4.2 One-Time Notice Display

The notice should appear as a long Toast on first `?fix` after update. It must:
- Mention the new model name (`big-pickle`)
- Tell the user their old key no longer works
- Direct them to settings to add a new key
- Show only once

### 4.3 Cleanup After Migration

After the notice is shown (or after the user saves an OpenCode Zen key):
1. Delete the `api_key_gemini` entry from `EncryptedSharedPreferences`
2. Delete the `selected_model` entry from plain `SharedPreferences`
3. Delete the `migration_notice_shown` flag (or leave it — it's harmless)
4. Delete the `hasLegacyGeminiKey()` method from `ApiKeyRepository`

### 4.4 No Fallback to Gemini

Per spec §7: "once removed, there is no code path back to Gemini." The migration is one-way. If a user never adds an OpenCode Zen key, they just get the "API key missing" toast on every `?fix` attempt.

### 4.5 Verification Gate — Step 4

1. **Simulate upgrading user:** manually seed `api_key_gemini` in encrypted prefs (no `api_key_opencode_zen`), launch app
2. Confirm migration notice appears on first `?fix`
3. Confirm notice does NOT appear on second `?fix`
4. Confirm old `api_key_gemini` is deleted after notice shown
5. Confirm saving an OpenCode Zen key works and `?fix` routes through it
6. Confirm removing the OpenCode Zen key shows the missing-key toast

---

## Step 5 — Tests

No tests exist in the project currently. The spec requires `AiClientTest.kt` per §8.1.

### 5.1 Create Test Infrastructure

**Add to `build.gradle.kts`:**
```kotlin
dependencies {
    // ... existing deps ...
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
```

**Create directory:** `app/src/test/java/com/musa/wordwise/network/`

### 5.2 Write `AiClientTest.kt`

Per spec §8.1, these tests verify `AiClient`'s public API without making real network calls:

**Test 1: `buildRequest_createsValidOpenAICompatiblePayload`**
- Call `AiClient.buildRequest("i has a apple", "test-key-123")`
- Verify the request URL is `https://opencode.ai/zen/v1/chat/completions`
- Verify `Authorization: Bearer test-key-123` header
- Parse the JSON body and verify:
  - `model` is `"big-pickle"`
  - `messages[0].role` is `"system"` and contains the grammar prompt
  - `messages[1].role` is `"user"` and content is `"i has a apple"`
  - `temperature` is `0.2`
  - `max_tokens` is `2048`

**Test 2: `parseResponse_extractsContentFromChoices`**
- Call `AiClient.parseResponse("""{"choices":[{"message":{"role":"assistant","content":"I have an apple."}}]}""", 200)`
- Verify result is `AiClient.Result.Success("I have an apple.")`

**Test 3: `parseResponse_handlesHttp401And429`**
- Call `AiClient.parseResponse("", 401)` → verify `Result.Failure` with "Invalid" message
- Call `AiClient.parseResponse("", 429)` → verify `Result.RateLimited`

**Test 4: `parseResponse_handles500`**
- Call `AiClient.parseResponse("", 500)` → verify `Result.Failure` with "OpenCode Zen issue"

**Test 5: `parseResponse_handlesEmptyContent`**
- Call `AiClient.parseResponse("""{"choices":[{"message":{"role":"assistant","content":""}}]}""", 200)`
- Verify result is `Result.Failure("No content returned")`

**Note:** `buildRequest()` and `parseResponse()` need to be `internal` or `public` for testability. Currently they're package-private (no modifier). In Kotlin, that's `public` by default for object functions, so they're already testable.

### 5.3 Verification Gate — Step 5

1. `./gradlew test` passes
2. All 5 tests pass
3. No Gemini-related test code exists (there are none to delete since none existed before)

---

## Step 6 — Docs and Cleanup Pass

### 6.1 Update `docs/ARCHITECTURE.md`

Rewrite to match the single-provider OpenCode Zen architecture. Key changes:

- **Overview:** Replace "using Google Gemini" with "using OpenCode Zen's `big-pickle` model"
- **Component Map:** Update AiClient description to reference OpenCode Zen, not Gemini. Update ApiKeyRepository to note single `api_key_opencode_zen` key. Remove model selection mention.
- **Flow Diagram:** Replace "Gemini API" node with "OpenCode Zen API". Replace `x-goog-api-key` with `Authorization: Bearer`. Replace `fixGrammar(text, apiKey)` signature with `fixGrammar(text, apiKey)` (same, but no `model` param).
- **Key Design Decisions:** Replace "Gemini only" with "OpenCode Zen only". Add note about the migration from Gemini in a "History" paragraph.
- **Date:** Update to current date.

### 6.2 `network_security_config.xml` — No Changes Needed

The config has no Gemini domain allow-list entries (confirmed in recon). The only entries are base config (cleartext blocked) and localhost (cleartext allowed for Ktor). No changes needed.

### 6.3 Final Grep Verification

```bash
grep -rn "gemini\|Gemini\|generativelanguage\|x-goog-api-key" --include="*.kt" --include="*.xml" --include="*.md"
```

Expected remaining hits:
- `docs/OPENCODE_ZEN_SPEC.md` — migration strategy section (§7) legitimately mentions "Gemini"
- `docs/ARCHITECTURE.md` — history note mentions Gemini
- `docs/PLAN-steps-0-1-opencode-zen.md` — historical plan document
- No hits in `app/src/` (active source code)

### 6.4 Version Bump

In `build.gradle.kts`:
- `versionCode = 4` (bump from 3)
- `versionName = "2.0"` (already correct — keep as is)

### 6.5 Changelog Entry

Add a CHANGELOG.md (or append to existing) with:
```
## 2.0 — OpenCode Zen Migration

- Replaced Google Gemini with OpenCode Zen's free `big-pickle` model
- No more rate limits — `big-pickle` is unmetered
- Single API key field in settings
- Model selection removed — `big-pickle` is the only model
- Existing Gemini users will see a one-time migration notice
```

### 6.6 Verification Gate — Step 6

1. `grep -rn "gemini\|Gemini" --include="*.kt" --include="*.xml"` in `app/src/` returns **zero hits**
2. `docs/ARCHITECTURE.md` describes OpenCode Zen architecture
3. Build compiles and installs
4. `?fix` works end-to-end
5. No UI element references Gemini

---

## Commit Strategy

Each step should be a separate commit for clean git history and easy rollback:

| Commit | Message | Rollback Risk |
|--------|---------|---------------|
| Step 2 | `refactor: remove Gemini entirely, collapse to single OpenCode Zen provider` | **High** — this is the point of no return for Gemini |
| Step 3 | `chore: finalize AiClient and settings UI to match spec §4.1/§5` | Low — cosmetic cleanup |
| Step 4 | `feat: add one-time migration notice for existing Gemini users` | Medium — affects upgrade path |
| Step 5 | `test: add AiClient unit tests for OpenCode Zen payload and parsing` | None — additive only |
| Step 6 | `docs: update architecture, changelog, and final cleanup` | None — docs only |

**Critical:** Do NOT squash Step 2 into Step 1. The Jules plan's rollback note applies here — if `big-pickle` turns out to be unreliable after all, reverting the Step 2 commit restores the dual-path state where both providers work.

---

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| Deleting Gemini before user tests OCZ in production | Users lose grammar correction if OCZ has issues | Step 2 only runs after Step 1 is verified on a real device with a real key |
| Migration notice doesn't show (EncryptedPrefs issue) | Users stuck without knowing why | Test with simulated upgrade state (seed old key, no new key) |
| `hasLegacyGeminiKey()` can't read old key after prefs migration | Dead code, no user impact | Test before adding the method — `EncryptedSharedPreferences` doesn't migrate across key rotations |
| `buildRequest()` not testable (private or inlined) | Tests can't verify payload | Verify visibility before writing tests; Kotlin object functions are public by default |
| Old `api_key_gemini` value lingers in prefs after cleanup | Minor security concern (stale key material) | Explicitly call `prefs.edit().remove("api_key_gemini").apply()` in cleanup |
