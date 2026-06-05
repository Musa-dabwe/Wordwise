# WordWise — Architecture Audit Report

**Audit date:** 2026-06-05  
**App version:** 1.0 (based on `app/build.gradle.kts`)  
**Codebase:** 3 source files, 457 lines (Kotlin)  

---

## 1. Component Map

| Class / Function | File | Role |
|---|---|---|
| `MainActivity` | `app/src/main/kotlin/com/musa/wordwise/MainActivity.kt` | Launcher activity — API key entry, persistent storage via `EncryptedSharedPreferences`, service enabled/disabled status display |
| `GrammarFixService` | `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt` | `AccessibilityService` — intercepts `TYPE_VIEW_TEXT_CHANGED` events, detects `?fixs`/`?fixp`/`?fixo` shortcuts, fires Gemini API grammar correction, replaces field text |
| `fixGrammar()` (top-level suspend) | `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt` | Builds mode-specific prompt, sends HTTP POST to Gemini API, parses JSON response, returns corrected text (or original on failure) |
| `FixMode` enum | `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt` | `SENTENCE` / `PARAGRAPH` / `ALL` — maps to shortcut modes |

**Supporting files:**
- `AndroidManifest.xml` — declares `INTERNET` permission, `MainActivity` (exported), `GrammarFixService` (BIND_ACCESSIBILITY_SERVICE, exported=false)
- `accessibility_service_config.xml` — event types `typeViewTextChanged|typeViewFocused|typeViewClicked`, flags including `flagRequestTouchExplorationMode`
- `app/build.gradle.kts` — dependencies: OkHttp 4.12.0, coroutines 1.7.3, kotlinx-serialization 1.6.3, security-crypto 1.1.0-alpha06, Material 1.12.0, AppCompat 1.7.1

---

## 2. Critical Bugs

### CRIT-1 — `event.text` contains only the *diff*, not the full field text — `?fixs` and `?fixp` always fail to extract text

**File:** `GrammarFixService.kt:50`  
**Code:** `val currentText = event.text.joinToString("")`

For `TYPE_VIEW_TEXT_CHANGED`, `AccessibilityEvent.getText()` returns a list of only the **added/changed characters**, not the complete field content. When a user types `hello world?fixs`, the event's `.text` list typically contains only `"?fixs"`.

- Line 53: `"?fixs".endsWith("?fixs")` → true (detection works)
- Line 63: `"?fixs".dropLast(5).trim()` → `""` (text to fix is empty)
- Line 66–70: returns early with `"No text to fix"`

**Impact:** `?fixs` and `?fixp` are **completely non-functional** on most devices. `?fixo` works because it uses `getAllTextFromField()` which reads `node.text` (the full field text).

**Fix:** Use `source.text?.toString() ?: ""` to get the full field text, detect the shortcut on that, and strip the shortcut suffix before sending to the API.

---

### CRIT-2 — `AccessibilityNodeInfo` objects never recycled — progressive memory leak

**File:** `GrammarFixService.kt:49`, `GrammarFixService.kt:60`, `GrammarFixService.kt:113`

`event.source` returns an `AccessibilityNodeInfo` that **must** be recycled via `.recycle()` after use. Android docs state:

> "Releases the resources associated with an AccessibilityNodeInfo. It's important to recycle nodes when they are no longer needed to prevent memory leaks."

The `source` node (line 49) and the node in `getAllTextFromField()` (line 113) are discarded without ever calling `recycle()`. Each invocation leaks at least one `AccessibilityNodeInfo` object. In heavy usage this can cause `OutOfMemoryError`.

**Fix:** Call `source.recycle()` in `replaceText()` after `performAction`, or use a `try/finally` block.

---

### CRIT-3 — No input-type filtering — password/credit-card fields are processed

**File:** `GrammarFixService.kt:45-111`

The service never checks `AccessibilityNodeInfo.getInputType()` or node flags. It processes `TYPE_VIEW_TEXT_CHANGED` from **all** text fields, including password, PIN, OTP, and credit card fields. This sends sensitive text to Google's Gemini API — a **privacy violation**.

**Fix:** Check `source.inputType` against `InputType.TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_EDIT_TEXT` (etc.) and skip the event for sensitive input types.

---

### CRIT-4 — Fire-and-forget coroutine with no cancellation scope

**File:** `GrammarFixService.kt:89`

```kotlin
CoroutineScope(Dispatchers.Main).launch { ... }
```

A new `CoroutineScope` is created per invocation with no reference stored:
- The coroutine **cannot be cancelled** if the user types new text or navigates away
- If the service reconnects, old API calls continue running
- Orphaned coroutines execute against potentially recycled `AccessibilityNodeInfo` objects (use-after-free risk at line 98)

**Fix:** Store a `var pendingJob: Job? = null` and call `pendingJob?.cancel()` before launching a new request.

---

## 3. Security Issues

### HIGH-SEC-1 — `EncryptedSharedPreferences` version is a deprecated alpha

**File:** `app/build.gradle.kts:55`

```
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

1. This is an **alpha** release — not production-safe.
2. It is **deprecated** upstream (`@Deprecated` annotation instructs to use `android.content.SharedPreferences`).

On devices without hardware-backed keystore, `MasterKey.Builder(...).setKeyScheme(AES256_GCM)` will throw `GeneralSecurityException`. The empty `catch (e: Exception) { }` in `loadExistingApiKey()` (`MainActivity.kt:107-109`) silently swallows this, leaving the API key unrecoverable with zero user feedback.

**Fix:** Use a stable release (`1.0.0`) and add user-visible error handling for `MasterKey` / `EncryptedSharedPreferences` initialization failures.

---

### HIGH-SEC-2 — API key exposed in URL query parameter

**File:** `AiClient.kt:95`

```kotlin
val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
```

The API key is passed as a URL query parameter. This means:
- Any HTTP proxy or transparent proxy logs the full URL with the key
- Google's servers log the URL including the query parameter
- Any `OkHttp` interceptor or logging library captures the full URL

This is the standard Gemini auth mechanism, so it's accepted practice, but worth flagging given the app's accessibility-level access.

---

### MED-SEC-3 — API key cached in memory for entire service lifetime

**File:** `GrammarFixService.kt:21,35`

```kotlin
private var apiKey: String = ""
...
apiKey = loadApiKey()
```

Loaded **once** in `onServiceConnected()`, stored as a plain `String` in the process heap indefinitely. If the user updates the key in the app, the service doesn't pick it up until process restart. A `String`'s backing `char[]` remains in memory until GC.

**Fix:** Read from `EncryptedSharedPreferences` on each API call instead of caching.

---

### MED-SEC-4 — User text content logged to Logcat

**File:** `GrammarFixService.kt:84`, `GrammarFixService.kt:95`, `AiClient.kt:92`, `AiClient.kt:130`

```kotlin
Log.d("GrammarFix", "Text to fix: '$textToFix' ...")
Log.d("GrammarFix", "Got corrected text: '$correctedText'")
```

Full user text is written to Logcat. Any app with `READ_LOGS` or via `adb logcat` can read all text the user types and corrects — including messages in WhatsApp, Notes, email drafts, etc.

**Fix:** Remove all content logging. At most log character counts and success/failure, never the text itself.

---

### LOW-SEC-5 — No `network_security_config.xml`

**File:** `AndroidManifest.xml`

No `android:networkSecurityConfig` attribute is present. No `network_security_config.xml` exists in `res/xml/`. While the Gemini endpoint is HTTPS, there is no explicit cleartext-traffic policy and no certificate pinning.

**Fix:** Create `res/xml/network_security_config.xml` with `cleartextTrafficPermitted="false"` and reference it in the manifest.

---

## 4. Architecture Issues

### ARCH-1 — God class: `GrammarFixService` does everything

`GrammarFixService` is responsible for:
- Accessibility event filtering and handling
- Shortcut detection and mode routing
- API key loading from encrypted storage
- Network call orchestration
- Text replacement
- Toast display

No delegation. The class is untestable and hard to maintain.

---

### ARCH-2 — Top-level function for network, not a class

**File:** `AiClient.kt:19`

`fixGrammar()` is a top-level `suspend` function:
- No dependency injection possible
- Cannot be mocked or stubbed for unit tests
- `OkHttpClient` created on **every invocation** (line 25–29) — wasteful, no connection pooling
- All configuration (timeouts, endpoint, model) is hardcoded

---

### ARCH-3 — ViewBinding enabled but unused

**File:** `app/build.gradle.kts:39`, `MainActivity.kt:28-31`

`viewBinding = true` set, but `MainActivity` uses `findViewById()`. Type safety and null safety benefits are unused.

---

### ARCH-4 — `EncryptedSharedPreferences` setup duplicated 3 times

The `MasterKey.Builder` + `EncryptedSharedPreferences.create` boilerplate appears in:
1. `MainActivity.loadExistingApiKey()` (lines 88–99)
2. `MainActivity.saveApiKey()` (lines 112–124)
3. `GrammarFixService.loadApiKey()` (lines 152–171)

---

## 5. Edge Case Risks

### EDGE-1 — Infinite loop risk: service triggers its own `TYPE_VIEW_TEXT_CHANGED` event

`replaceText()` calls `performAction(ACTION_SET_TEXT, ...)`, which fires a new `TYPE_VIEW_TEXT_CHANGED` event. The `isProcessing` flag prevents re-entry during an active request. A **race window** exists between `finally { isProcessing = false }` and the new event delivery.

### EDGE-2 — Text replacement failure on read-only fields

`ACTION_SET_TEXT` is not supported on read-only/disabled fields, `WebView` inputs, or custom views. `replaceText()` logs failures but never informs the user or falls back (e.g., clipboard copy). The "alternative method" (line 139–141: `ACTION_FOCUS` then `ACTION_SET_TEXT` again) won't help.

### EDGE-3 — Silent exception swallowing in `loadExistingApiKey()`

`MainActivity.kt:107-109`: empty `catch (e: Exception) { }`. If `MasterKey.Builder` or `EncryptedSharedPreferences.create` throws, the user gets no error feedback and cannot save/recover their key.

### EDGE-4 — Fragile shortcut detection with `endsWith`

`shortcuts.keys.find { currentText.endsWith(it) }` — if text ends with `"?fixs?fixp"`, it matches `"?fixp"` but the extracted text still contains `"?fixs"`. Shortcut text remains in the field on API error (EDGE-5).

### EDGE-5 — Shortcut text left in field on API error

On network/HTTP/parse failure, `fixGrammar` returns the original text unchanged. `correctedText != textToFix` evaluates to `false`, so `replaceText` is never called. The user sees `?fixs` still in the field with no feedback.

### EDGE-6 — Race condition: user types while API request in-flight

`isProcessing` blocks ALL shortcut detection during the API call (it's a simple boolean, not per-field). If the user switches apps and types `?fixs` in another field, it's silently ignored.

### EDGE-7 — Unused event types in accessibility config

`accessibility_service_config.xml:4` registers for `typeViewTextChanged|typeViewFocused|typeViewClicked`, but `onAccessibilityEvent` filters to only `TYPE_VIEW_TEXT_CHANGED`. The other events still fire the callback, wasting CPU.

### EDGE-8 — `flagRequestTouchExplorationMode` set unnecessarily

This flag is for screen readers (TalkBack). Setting it may interfere with other accessibility services. `flagRequestFilterKeyEvents` is also unused.

### EDGE-9 — No ProGuard keep rules for serialization or service

`isMinifyEnabled = true` but `proguard-rules.pro` has all comments — no rules. `kotlinx.serialization` and the `GrammarFixService` class name could be obfuscated/broken in release builds.

---

## 6. Recommendations

### P0 — Must fix immediately

| # | Issue | File | Fix |
|---|---|---|---|
| 1 | CRIT-1: `?fixs`/`?fixp` text extraction broken | `GrammarFixService.kt:50,63` | Use `source.text?.toString() ?: ""` (full field text) instead of `event.text.joinToString("")` |
| 2 | CRIT-2: `AccessibilityNodeInfo` memory leak | `GrammarFixService.kt:49,128` | Call `source.recycle()` in `finally` block after use |
| 3 | CRIT-3: No password-field filtering | `GrammarFixService.kt:45` | Check `source.inputType` and skip sensitive input types |
| 4 | CRIT-4: Orphaned coroutines, no cancellation | `GrammarFixService.kt:89` | Track `Job` and cancel before launching new request |
| 5 | HIGH-SEC-1: Alpha/deprecated crypto library | `app/build.gradle.kts:55` | Upgrade to `security-crypto:1.0.0`, handle init failures with user-visible errors |

### P1 — Fix soon

| # | Issue | File | Fix |
|---|---|---|---|
| 6 | ARCH-4: Duplicated `EncryptedSharedPreferences` code | 3 locations | Extract to shared `ApiKeyRepository` class |
| 7 | MED-SEC-3, MED-SEC-4: Logging user text and API key | Multiple | Remove content logging; at most log char counts |
| 8 | LOW-SEC-5: No network security config | `AndroidManifest.xml` | Add `res/xml/network_security_config.xml` with cleartext disabled |
| 9 | EDGE-5: Shortcut left in field on error | `GrammarFixService.kt:97-101` | Strip shortcut from field even on API failure |
| 10 | EDGE-9: No ProGuard rules | `app/proguard-rules.pro` | Add keep rules for `GrammarFixService` and `kotlinx.serialization` |

### P2 — Improvement

| # | Issue | File | Fix |
|---|---|---|---|
| 11 | ARCH-2: `OkHttpClient` created per call | `AiClient.kt:25-29` | Use a shared singleton or DI-provided instance |
| 12 | EDGE-7, EDGE-8: Unused event types and flags | `accessibility_service_config.xml` | Keep only `typeViewTextChanged`, remove `flagRequestTouchExplorationMode` |
| 13 | ARCH-3: `viewBinding` unusued | `MainActivity.kt` | Migrate from `findViewById` to view binding |
| 14 | EDGE-6: Single `isProcessing` flag blocks all fields | `GrammarFixService.kt:31` | Use per-field tracking or `mutableSetOf<String>` for view IDs |
| 15 | No debounce | `GrammarFixService.kt:45` | Add ~150ms debounce to coalesce rapid keystrokes into one API call |
