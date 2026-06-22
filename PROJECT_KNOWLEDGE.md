# PROJECT_KNOWLEDGE.md — WordWise

## 1. PROJECT OVERVIEW
WordWise is a system-wide accessibility-based utility for Android that provides real-time grammar and style correction across all applications. It allows users to fix their text by simply typing a trigger shortcut (`?fix`) at the end of their input, leveraging Google Gemini for high-quality linguistic processing.

- **Platform:** Android
- **Language:** Kotlin (2.0.21)
- **Minimum SDK:** 23 (Android 6.0)
- **Target SDK:** 35 (Android 15)
- **Status:** Beta (Recently migrated to gemini-2.5-flash-lite; UI redesigned via Google Stitch)
- **Build Requirements:** Gradle 8.9.1, JDK 17, Google Gemini API Key

---

## 2. REPOSITORY STRUCTURE
```
.
├── app/
│   ├── build.gradle.kts           # Module-level build configuration and dependencies
│   ├── proguard-rules.pro         # ProGuard/R8 obfuscation and keep rules
│   └── src/main/
│       ├── AndroidManifest.xml    # App manifest, permissions, and service declarations
│       ├── kotlin/com/musa/wordwise/
│       │   ├── MainActivity.kt        # Launcher UI; manages API key and service status
│       │   ├── GrammarFixService.kt   # Core AccessibilityService; intercepts text and triggers fixes
│       │   ├── network/AiClient.kt    # Singleton Gemini API client using OkHttp
│       │   └── data/ApiKeyRepository.kt # Secure storage for API keys using EncryptedSharedPreferences
│       └── res/
│           ├── layout/activity_main.xml # Material3 UI layout for the main screen
│           ├── xml/
│           │   ├── accessibility_service_config.xml # Metadata for the Accessibility Service
│           │   └── network_security_config.xml     # Strict TLS and cleartext traffic policy
│           └── values/
│               ├── strings.xml    # Localized strings, command labels, and AI prompts
│               ├── themes.xml     # Material3 Dark theme configuration
│               └── colors.xml     # Custom WordWise (ww_) color tokens
├── docs/
│   ├── ARCHITECTURE.md            # High-level design documentation and component map
│   └── AUDIT.md                   # Technical debt log and dead code removal history
├── build.gradle.kts               # Project-level build configuration
└── settings.gradle.kts            # Project structure and root project naming
```

---

## 3. ARCHITECTURE & DESIGN PATTERNS
- **Overall Pattern:** Minimalist Activity + Service architecture. Does not use MVVM/ViewModel to keep the memory footprint low for a persistent background service.
- **Layer Breakdown:**
    - **UI Layer:** `MainActivity` handles user configuration and status monitoring via ViewBinding.
    - **Service Layer:** `GrammarFixService` acts as the system-wide orchestrator, monitoring accessibility events.
    - **Network Layer:** `AiClient` encapsulates Gemini API logic using a shared OkHttp singleton.
    - **Data Layer:** `ApiKeyRepository` manages persistent secure storage.
- **Dependency Injection:** Manual "Lazy" injection. Components are instantiated on-demand or as singletons.
- **Concurrency Model:** Kotlin Coroutines.
    - `GrammarFixService` uses a `serviceScope` with `SupervisorJob` to ensure individual correction failures don't kill the service.
    - `AiClient` switches to `Dispatchers.IO` internally for network calls.
- **Key Decisions:**
    - **Accessibility Service:** Chosen over IME (keyboard) to remain keyboard-agnostic.
    - **EncryptedSharedPreferences:** Used instead of Room/SQLite to minimize complexity and security surface area.
    - **Gemini-only:** Hardcoded to Google Gemini (`gemini-2.5-flash-lite`) for optimized performance on mobile.

---

## 4. CORE COMPONENTS

**GrammarFixService**
- **Path:** `app/src/main/kotlin/com/musa/wordwise/GrammarFixService.kt`
- **Role:** The system-wide orchestrator that listens for text changes and dispatches correction requests.
- **Key methods:**
    - `onAccessibilityEvent`: Primary entry point; filters for `TYPE_VIEW_TEXT_CHANGED`, performs sensitive field checks (passwords), matches shortcuts (`?fix`), and launches correction jobs.
    - `replaceText`: Performs `ACTION_SET_TEXT` on an `AccessibilityNodeInfo`.
    - `showToast`: Posts a `Toast` to the main thread via `mainHandler`.
    - `onServiceConnected`: Initializes the service and displays a welcome toast.
    - `onDestroy`: Cancels the `serviceScope`.
    - `safeRecycle` (Extension): Extension function to safely recycle `AccessibilityNodeInfo` objects.
- **Inputs:** `AccessibilityEvent` (Text changes across the OS).
- **Outputs:** Updated text in the active input field via Accessibility actions.
- **Dependencies:** `AiClient`, `ApiKeyRepository`, `Handler`.
- **State it owns:** `pendingJob` (currently running correction), `serviceScope`.
- **Gotchas:** [INFERRED] Highly dependent on target apps correctly implementing Accessibility APIs. Some apps (like custom code editors) may ignore `ACTION_SET_TEXT`.

**AiClient**
- **Path:** `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt`
- **Role:** Singleton client for interacting with the Google Gemini API.
- **Key methods:**
    - `fixGrammar`: Public suspending API that wraps the network request and returns a `Result` sealed class.
    - `fixGrammarGemini` (Private): Constructs the Gemini request body and URL.
    - `executeRequest` (Private/Inline): Performs the OkHttp call and maps HTTP status codes to `Result` variants.
- **Inputs:** Raw text string and API key.
- **Outputs:** `Result.Success(text)`, `Result.RateLimited`, or `Result.Failure`.
- **Dependencies:** OkHttp, kotlinx-serialization.
- **State it owns:** Shared `OkHttpClient` instance with connection pool.
- **Gotchas:** Currently contains stub-like logic for URL construction and body building (see Tech Debt).

**ApiKeyRepository**
- **Path:** `app/src/main/kotlin/com/musa/wordwise/data/ApiKeyRepository.kt`
- **Role:** Secure wrapper for API key persistence.
- **Key methods:**
    - `saveApiKey`: Persists a key to encrypted storage.
    - `getApiKey`: Retrieves the saved key.
- **Inputs:** String keys.
- **Outputs:** Retrieved keys.
- **Dependencies:** `EncryptedSharedPreferences`.
- **State it owns:** `prefs` (Lazy-loaded `SharedPreferences`).
- **Gotchas:** Uses deprecated `MasterKeys` API (should migrate to `MasterKey.Builder`).

**MainActivity**
- **Path:** `app/src/main/kotlin/com/musa/wordwise/MainActivity.kt`
- **Role:** Entry point for users to configure the app and check status.
- **Key methods:**
    - `checkAccessibilityStatus`: Updates the UI to show if the service is running.
    - `setupApiKeySection`: Pre-fills UI and sets up the Google AI Studio hyperlink.
    - `setupSaveButton`: Saves the key and shows confirmation toast.
- **Inputs:** User interaction (Button clicks, Text input).
- **Outputs:** Updates to `ApiKeyRepository` and system Accessibility settings intent.
- **Dependencies:** `ApiKeyRepository`, ViewBinding.
- **State it owns:** None (Stateless Activity, state is in the repository).
- **Gotchas:** Relies on `onResume` to update status, which may not trigger if settings change in split-screen mode on some Android versions.

---

## 5. DATA FLOW DIAGRAMS (text-based)

**Primary Flow: ?fix Shortcut Trigger**
```
User types "?fix" in WhatsApp/Slack/Gmail
  → AccessibilityEvent (TYPE_VIEW_TEXT_CHANGED) triggered
  → GrammarFixService.onAccessibilityEvent()
  → Regex check matches "\?fix$"
  → GrammarFixService reads API Key from ApiKeyRepository
  → GrammarFixService.showToast("Fixing...")
  → AiClient.fixGrammar(originalText)
  → HTTP POST to Gemini API
  → Gemini returns corrected JSON
  → AiClient parses result
  → GrammarFixService.replaceText(node, correctedText)
  → UI updates; User sees corrected text
```

**Error Path: API Failure / Rate Limit**
```
AiClient receives HTTP 429 (Rate Limit) or 401 (Auth)
  → AiClient maps to Result.RateLimited or Result.Failure
  → GrammarFixService receives result
  → GrammarFixService.showToast(errorMessage)
  → Text in field remains unchanged (or only shortcut is stripped)
```

---

## 6. EXTERNAL DEPENDENCIES & INTEGRATIONS

| Name | Version | Purpose | Where Used | Notes |
|------|---------|---------|-----------|-------|
| OkHttp | 4.12.0 | HTTP client | AiClient.kt | Shared singleton client |
| kotlinx-serialization | 1.6.3 | JSON parsing | AiClient.kt | Tree-based parsing |
| security-crypto | 1.0.0 | Encryption | ApiKeyRepository.kt | AES-256-GCM / SIV |
| Material Components | 1.12.0 | UI Components | activity_main.xml | Material3 Theme |
| Gemini API | gemini-2.5-flash-lite | AI Backend | AiClient.kt | Free tier (~1.5k req/day) |

---

## 7. CONFIGURATION & ENVIRONMENT
- **Environment Variables:** None (managed via UI).
- **BuildConfig:** Standard Android build config.
- **local.properties:** Used for Android SDK path (`sdk.dir`).
- **Secrets:** The Gemini API key is entered by the user in `MainActivity` and saved in `EncryptedSharedPreferences` (`secret_keys.xml`).
- **Feature Flags:** None.

---

## 8. DATABASE & PERSISTENCE
- **Storage Mechanism:** `EncryptedSharedPreferences` (Jetpack Security).
- **File Name:** `secret_keys`
- **Schema:**
    - `api_key_gemini` (String): The encrypted Google Gemini API key.
- **Migration Strategy:** Manual; currently no versioning for preferences.

---

## 9. KNOWN ISSUES, TODOS & TECH DEBT

**Critical Tech Debt**
- [ ] `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt:73` — **BUG:** `fixGrammarGemini` URL is relative (`/:generateContent`) and missing placeholders. It should be fully qualified.
- [ ] `app/src/main/kotlin/com/musa/wordwise/network/AiClient.kt:66` — **STUB:** Body construction uses hardcoded `\n\n` and ignores the `GRAMMAR_SYSTEM_PROMPT`.
- [ ] `app/src/main/kotlin/com/musa/wordwise/data/ApiKeyRepository.kt:10` — **DEPRECATED:** Uses `MasterKeys` API which is deprecated in favor of `MasterKey.Builder`.

**General Issues**
- [ ] No "Undo" functionality after text replacement.
- [ ] `GrammarFixService.kt:32` — [INFERRED] Large text threshold (1000 chars) is arbitrary and might need adjustment for the "lite" model.
- [ ] No support for multi-part text nodes (only reads `event.text` or `source.text`).

---

## 10. CRITICAL RULES & CONSTRAINTS
- **Sensitive Field Filtering:** `GrammarFixService` MUST ignore any field with password input types. (Verified in `onAccessibilityEvent`).
- **Main Thread Safety:** `AiClient` MUST handle its own context switching to `Dispatchers.IO`. Do not wrap its calls in another `withContext` at the call site.
- **Memory Management:** Always call `recycle()` on `AccessibilityNodeInfo` to avoid memory leaks. Use the `safeRecycle()` helper.
- **Prompt Integrity:** The prompt MUST instruct Gemini to return *only* the corrected text with no commentary.
- **Literal Escaping:** In `strings.xml`, the `?fix` label must be escaped as `\?fix` to avoid being treated as a theme attribute.

---

## 11. TESTING
- **Frameworks:** None currently implemented. No `test` or `androidTest` folders contain source code.
- **Coverage Gaps:** 100% (No unit or integration tests present).
- **Manual Testing Procedure:**
    1. Enter valid Gemini API Key in WordWise.
    2. Enable Accessibility Service in System Settings.
    3. Open any text app (e.g., Keep).
    4. Type "i has a apple?fix".
    5. Verify replacement with "I have an apple."

---

## 12. BUILD & RELEASE
- **Build Command:** `./gradlew assembleDebug` or `./gradlew assembleRelease`.
- **Minification:** Enabled for release builds (`isMinifyEnabled = true`).
- **ProGuard:** Custom rules in `proguard-rules.pro` preserve `GrammarFixService` and serialization classes.
- **Signing:** Release signing requires a keystore configuration (not present in repo).

---

## 13. RECENT CHANGES & EVOLUTION
- **Gemini Migration:** Recently moved from `gemini-2.5-flash` to `gemini-2.5-flash-lite` for cost/speed efficiency.
- **Security Hardening:** Completed a full security audit; 5 critical bugs related to key leakage and sensitive field handling were fixed.
- **UI Refresh:** Redesigned using Google Stitch to align with Material3 guidelines.
- **Ongoing Migration:** Currently migrating from basic `AiClient.Result` to a more robust, typed `GrammarResult` sealed class for better error granularity.

---

## 14. GLOSSARY

| Term | Meaning |
|------|---------|
| ?fix | The primary trigger shortcut for grammar correction. |
| ACTION_SET_TEXT | The accessibility action used to replace text in external apps. |
| GEMINI_MODEL | Hardcoded to "gemini-2.5-flash-lite". |
| safeRecycle | Extension function to safely recycle AccessibilityNodeInfo objects. |
