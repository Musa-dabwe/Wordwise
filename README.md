# WordWise

System-wide grammar correction for Android. Type `?fix` at the end of any text in any app and WordWise rewrites it using Google Gemini.

## How It Works

WordWise registers as an **Android Accessibility Service** and listens for `TYPE_VIEW_TEXT_CHANGED` events across all running applications. When you type `?fix` at the end of text in any input field, the service:

1. Strips the shortcut suffix and reads the surrounding text from the accessibility node.
2. Sends the text to **Google Gemini** (your selected model, default `gemini-3.1-flash-lite`) with a strict correction prompt.
3. Replaces the field content in-place via `ACTION_SET_TEXT` — no copy/paste required.

The service skips password fields (`TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`) for security.

## Setup

### Requirements

- Android device running **Android 6.0+** (API 23, minSdk = 23).
- A **Google Gemini API key** from [Google AI Studio](https://aistudio.google.com).

### 1. Install WordWise

Sideload the APK onto your device.

### 2. Enter Your Gemini API Key

1. Get a free API key at [aistudio.google.com](https://aistudio.google.com) — no credit card required on the free tier.
2. Open WordWise, paste your key into the **API Key** field, and tap **Save API Key**.
3. The key is encrypted and persisted on-device using `EncryptedSharedPreferences` (AES-256-GCM for values, AES-256-SIV for keys).

### 3. Enable the Accessibility Service

1. In WordWise, tap **Open Accessibility Settings**.
2. Find **WordWise** in the list of installed services.
3. Toggle the switch to **On**.
4. WordWise displays its live status (Enabled / Disabled) on the main screen via `checkAccessibilityStatus()`.

## Usage

In any text field (WhatsApp, Slack, Gmail, Telegram, etc.), type your text and append the trigger:

```
i dont no how to spel?fix
```

After a short delay (2–3 seconds), the field is replaced with:

```
I don't know how to spell.
```

### Notes

- The service ignores text under 1 character after stripping `?fix`.
- If the input exceeds **1,000 characters**, a toast warning is shown (the request proceeds regardless).
- If the API returns the same text unchanged, a "No corrections needed" message appears.
- If the free-tier **rate limit (HTTP 429)** is hit, a message is shown asking the user to try again later.

### Models

WordWise only lists models available on the Gemini API **free tier** (Flash / Flash-Lite families). Pick one in the app:

| Model | Notes |
|---|---|
| `gemini-3.1-flash-lite` | Default — fast, frontier-class quality, generous free limits |
| `gemini-3.5-flash` | Most capable free model |
| `gemini-2.5-flash-lite` | Fastest and most budget-friendly of the 2.5 family |
| `gemini-2.5-flash` | 2.5 workhorse (WordWise disables its thinking budget for speed) |

Free-tier limits vary per model (roughly 10–15 requests/minute and 1,000–1,500 requests/day); check your live quota at [aistudio.google.com](https://aistudio.google.com/rate-limit).

### Themes

WordWise uses the Poet pastel design system (shared with PoetMusic). In **Settings** you can pick:

- **Accent color** — four pastel swatches: lavender, mint, peach, and sky.
- **Canvas tint** — three background tints: **Lavender**, **Cream**, and **Sage**.

Both apply instantly via CSS variables (no restart) and are persisted in preferences. The Android status bar follows the chosen accent.

## Security & Privacy

| Concern | Implementation |
|---|---|
| **API key storage** | `EncryptedSharedPreferences` with a `MasterKey.Builder` AES-256-GCM master key. Key encryption uses AES-256-SIV; value encryption uses AES-256-GCM. |
| **Key in transit** | Sent via the `x-goog-api-key` request header (never in the URL, so it cannot leak into request logs) to `generativelanguage.googleapis.com` over TLS 1.3. |
| **Backups** | The encrypted key store is excluded from Auto Backup and device-to-device transfer (`backup_rules.xml` / `data_extraction_rules.xml`) — its master key lives in the device Keystore and cannot travel with a backup. |
| **Network policy** | `android:networkSecurityConfig` explicitly blocks cleartext traffic — only TLS connections are permitted. System certificate store is used for trust anchors. |
| **Data retention** | WordWise does not log, cache, or store any text sent for correction. Text is held in memory only for the duration of the network request and discarded immediately after. |
| **Sensitive fields** | Password, visible-password, and web-password input types are programmatically skipped — the service never reads their content. |

## Architecture

WordWise follows a minimal **Activity + Service** pattern (not MVVM — there are no `ViewModel` or `LiveData`/`StateFlow` classes).

The configuration UI is an **htmx web frontend** served by an **embedded Ktor server** (`127.0.0.1:8977`, CIO engine) into a fullscreen WebView — the same architecture and pastel design system as PoetMusic. See `docs/FRONTEND_MIGRATION.md` for the full map.

```
┌──────────────────────────────────────────────────────────┐
│  GrammarFixService (AccessibilityService)                 │
│  - Monitors TYPE_VIEW_TEXT_CHANGED events                 │
│  - Matches ?fix suffix with Regex("\\?fix$")             │
│  - Calls AiClient.fixGrammar(text, apiKey)                │
│  - Replaces field text via ACTION_SET_TEXT                │
│  - Runs on Dispatchers.Main with SupervisorJob scope      │
└──────────┬───────────────────────────────────────────────┘
           │ reads key
           ▼
┌──────────────────────┐    ┌──────────────────────────────┐
│  ApiKeyRepository     │    │  AiClient (singleton)        │
│  - EncryptedSharedPrefs│   │  - OkHttp 4.12.0 (pooled)    │
│  - getApiKey()         │    │  - Gemini endpoint           │
│  - saveApiKey()        │    │  - Timeouts: C 30s / R 60s  │
└──────────────────────┘    │  - kotlinx-serialization JSON │
                            └──────────────────────────────┘
                                    │ POST /v1beta/models/
                                    │ {selected-model}
                                    │ :generateContent
                                    │ (x-goog-api-key header)
                                    ▼
                         Google Gemini API
```

### Components

| Component | File | Role |
|---|---|---|
| `GrammarFixService` | `GrammarFixService.kt` | Core `AccessibilityService`. Listens for text-change events, detects `?fix`, orchestrates correction via coroutines (`CoroutineScope(Dispatchers.Main + SupervisorJob)`), and replaces text on the UI node. |
| `AiClient` | `AiClient.kt` | Singleton wrapping an `OkHttpClient`. Exposes `suspend fun fixGrammar(text, apiKey): Result` (dispatches to `Dispatchers.IO`). The `Result` sealed class has three variants: `Success`, `RateLimited`, `Failure`. Only the Gemini backend is implemented. |
| `ApiKeyRepository` | `ApiKeyRepository.kt` | Reads and writes the Gemini API key via `EncryptedSharedPreferences`. Single key (`api_key_gemini`) stored in a preferences file named `secret_keys`. |
| `MainActivity` | `MainActivity.kt` | Launcher activity. Fullscreen WebView hosting the htmx frontend; opens external links in the browser, drives the status-bar color from the accent, and forwards the hardware back button to `wwBack()` in JS. |
| `WordWiseApp` | `WordWiseApp.kt` | `Application` subclass; starts the embedded Ktor server before the WebView exists. |
| `WwServer` | `server/WwServer.kt` | Embedded Ktor (CIO) server on `127.0.0.1:8977`. Serves the shell, screens, assets, live status JSON, and the key/model/theming API. |
| `Shell` / `Views` | `server/Shell.kt`, `server/Views.kt` | Poet design-system CSS/JS shell (Outfit font, pastel accents, canvas tints, toasts) and the server-rendered Home/Settings screens. |
| `Prefs` | `data/Prefs.kt` | Plain SharedPreferences for the selected model, accent, and canvas tint. |

### Prompt

The system prompt sent to Gemini is:

```
You are a grammar and style correction assistant.
Return only the corrected text.
Preserve the original language and meaning exactly.
Do not add any explanations, commentary, or quotation marks.
```

Because the prompt instructs the model to preserve the original language, WordWise works with any language that Gemini supports — but this is a property of the model, not a language-detection feature in the app.

### Threading

- `GrammarFixService` launches correction on `Dispatchers.Main` with a `SupervisorJob`, so individual failures don't cancel the service scope.
- `AiClient.fixGrammar()` switches to `Dispatchers.IO` internally for the blocking OkHttp call.
- Toasts are posted to the main thread via `Handler(Looper.getMainLooper())`.

## Networking

- **Library**: OkHttp 4.12.0 with a shared (singleton) `OkHttpClient`.
- **Timeouts**: Connect 30s, Read 60s, Write 30s.
- **Serialization**: `kotlinx-serialization-json` 1.6.3 for building and parsing Gemini request/response bodies.
- **TLS only**: `network_security_config.xml` sets `cleartextTrafficPermitted="false"` and trusts only system certificates.

## Dependencies

```
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.7.1
com.squareup.okhttp3:okhttp:4.12.0
androidx.security:security-crypto:1.1.0
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3
io.ktor:ktor-server-core:2.3.13
io.ktor:ktor-server-cio:2.3.13
org.slf4j:slf4j-nop:2.0.13
```

Bundled web assets: `htmx.min.js`, `outfit-latin.woff2`, `outfit-latin-ext.woff2`.

## Limitations

- **Gemini only**: Ollama and other providers are not implemented. Only Google Gemini is supported.
- **Field support**: Some secure fields (passwords) and custom-drawn views (e.g., rich-text editors, code editors) may not support `ACTION_SET_TEXT` and cannot be corrected.
- **Large text**: Inputs exceeding 1,000 characters trigger a warning. Very long texts may consume significant free-tier quota or hit the response size limit.
- **No offline mode**: All corrections require internet access to the Gemini API.
- **Single API key**: Only one Gemini key is stored at a time — no per-app or per-provider key rotation.
- **No undo**: Text replacement is immediate. There is no "undo correction" functionality.

## Tech Stack

- **Language**: Kotlin 2.0.21
- **JVM target**: 17
- **Build system**: Gradle with Kotlin DSL (AGP 8.9.1)
- **Min SDK / Target SDK**: 26 / 35
- **Networking**: OkHttp 4.12.0 (Gemini), embedded Ktor 2.3.13 CIO server (frontend)
- **Serialization**: kotlinx-serialization-json 1.6.3
- **Coroutines**: kotlinx-coroutines-android 1.7.3
- **UI**: htmx frontend in a WebView, Poet pastel design system (Outfit font, 4 accents × 3 canvas tints)
- **Secure storage**: EncryptedSharedPreferences (AES-256-GCM + AES-256-SIV)
- **Architecture**: Activity + Service (not MVVM)
- **ProGuard**: Minification enabled for release builds
