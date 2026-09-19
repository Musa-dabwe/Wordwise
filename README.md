# WordWise

System-wide grammar correction and AI assistant for Android. Type `?fix` at the end of any text to correct it, or `?ask` to ask a question. Powered by OpenRouter.

## How It Works

WordWise registers as an **Android Accessibility Service** and listens for `TYPE_VIEW_TEXT_CHANGED` events across all running applications. It recognizes two commands:

- **`?fix`** -- corrects grammar and style in the surrounding text.
- **`?ask`** -- sends the preceding text as a prompt to an AI assistant.

When you type one of these triggers, the service:

1. Calls `detectCommand()` to match `?fix` or `?ask` against the current text.
2. Routes the result through a `Command` sealed class (`Command.Fix` or `Command.Ask`).
3. Sends the text to **OpenRouter** (free models router) via the appropriate endpoint, with a dedicated system prompt for each command.
4. Replaces the field content in-place via `ACTION_SET_TEXT` -- no copy/paste required.

The service skips password fields (`TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`) for security.

## Setup

### Requirements

- Android device running **Android 8.0+** (API 26, minSdk = 26).
- An **OpenRouter API key** from [openrouter.ai](https://openrouter.ai/keys).

### 1. Install WordWise

Sideload the APK onto your device.

### 2. Enter Your OpenRouter API Key

1. Get a free API key at [openrouter.ai/keys](https://openrouter.ai/keys) -- no credit card required.
2. Open WordWise, paste your key into the **API Key** field, and tap **Save API Key**.
3. The key is encrypted and persisted on-device using `EncryptedSharedPreferences` (AES-256-GCM for values, AES-256-SIV for keys).

### 3. Enable the Accessibility Service

1. In WordWise, tap **Open Accessibility Settings**.
2. Find **WordWise** in the list of installed services.
3. Toggle the switch to **On**.
4. WordWise displays its live status (Enabled / Disabled) on the main screen via `checkAccessibilityStatus()`.

## Usage

### Grammar Correction (`?fix`)

In any text field (WhatsApp, Slack, Gmail, Telegram, etc.), type your text and append the trigger:

```
i dont no how to spel?fix
```

After a short delay (2-3 seconds), the field is replaced with:

```
I don't know how to spell.
```

### AI Assistant (`?ask`)

Type a question or instruction followed by `?ask`:

```
explain quantum entanglement in one sentence?ask
```

WordWise sends the text to OpenRouter and replaces the field with the AI response:

```
Quantum entanglement is a phenomenon where two particles become linked so that measuring one instantly determines the state of the other, regardless of distance.
```

### Notes

- The service ignores text under 1 character after stripping the trigger.
- For `?fix`: if the input exceeds **1,000 characters**, a toast warning is shown (the request proceeds regardless).
- For `?ask`: if the input exceeds **10,000 words**, a toast warning is shown. `?ask` is single-turn only -- there is no conversation history.
- If the API returns the same text unchanged, a "No corrections needed" message appears.
- If the free-tier **rate limit (HTTP 429)** is hit, WordWise retries once after 3 seconds, then shows a "try again shortly" message.

### Themes

WordWise uses the Poet pastel design system. In **Settings** you can pick:

- **Accent color** -- four pastel swatches: lavender, mint, peach, and sky.
- **Canvas tint** -- three background tints: **Lavender**, **Cream**, and **Sage**.

Both apply instantly via CSS variables (no restart) and are persisted in preferences. The Android status bar follows the chosen accent.

## Security & Privacy

| Concern | Implementation |
|---|---|
| **API key storage** | `EncryptedSharedPreferences` with a `MasterKey.Builder` AES-256-GCM master key. Key encryption uses AES-256-SIV; value encryption uses AES-256-GCM. |
| **Key in transit** | Sent via the `Authorization: Bearer` request header (never in the URL, so it cannot leak into request logs) to `openrouter.ai` over TLS 1.3. |
| **Backups** | The encrypted key store is excluded from Auto Backup and device-to-device transfer (`backup_rules.xml` / `data_extraction_rules.xml`) -- its master key lives in the device Keystore and cannot travel with a backup. |
| **Network policy** | `android:networkSecurityConfig` explicitly blocks cleartext traffic -- only TLS connections are permitted. System certificate store is used for trust anchors. |
| **Data retention** | WordWise does not log, cache, or store any text sent for correction. Text is held in memory only for the duration of the network request and discarded immediately after. |
| **Sensitive fields** | Password, visible-password, and web-password input types are programmatically skipped -- the service never reads their content. |

## Architecture

WordWise follows a minimal **Activity + Service** pattern (not MVVM -- there are no `ViewModel` or `LiveData`/`StateFlow` classes).

The configuration UI is an **htmx web frontend** served by an **embedded Ktor server** (`127.0.0.1:8977`, CIO engine) into a fullscreen WebView -- the same architecture and pastel design system as PoetMusic. See `docs/FRONTEND_MIGRATION.md` for the full map.

```
+-----------------------------------------------------------+
|  GrammarFixService (AccessibilityService)                  |
|  - Monitors TYPE_VIEW_TEXT_CHANGED events                  |
|  - detectCommand() matches ?fix / ?ask                     |
|  - Command sealed class: Fix(text) | Ask(prompt)           |
|  - Calls AiClient.fixGrammar() or AiClient.ask()           |
|  - Replaces field text via ACTION_SET_TEXT                 |
|  - Runs on Dispatchers.Main with SupervisorJob scope       |
+----------+-------------------------+----------------------+
           | reads key               |
           v                         v
+---------------------+   +----------------------------------+
| ApiKeyRepository     |   | AiClient (singleton)            |
| - EncryptedSharedPrefs|  | - OkHttp 4.12.0 (pooled)        |
| - getApiKey()         |   | - OpenRouter endpoint            |
| - saveApiKey()        |   | - Timeouts: C 15s / R 60s / W 30s|
+---------------------+   | - kotlinx-serialization JSON     |
                          +----------------------------------+
                                    | POST /api/v1/chat/completions
                                    | Authorization: Bearer <key>
                                    | (HTTP-Referer + X-Title)
                                    v
                          OpenRouter API
                          (free models router)
```

### Components

| Component | File | Role |
|---|---|---|
| `GrammarFixService` | `GrammarFixService.kt` | Core `AccessibilityService`. Listens for text-change events, calls `detectCommand()` to route `?fix` / `?ask` through the `Command` sealed class, orchestrates correction via coroutines (`CoroutineScope(Dispatchers.Main + SupervisorJob)`), and replaces text on the UI node. |
| `AiClient` | `AiClient.kt` | Singleton wrapping an `OkHttpClient`. Exposes `suspend fun fixGrammar(text, apiKey): Result` for grammar correction and `suspend fun ask(prompt, apiKey): Result` for general questions (both dispatch to `Dispatchers.IO`). The `Result` sealed class has three variants: `Success`, `RateLimited`, `Failure`. |
| `ApiKeyRepository` | `ApiKeyRepository.kt` | Reads and writes the OpenRouter API key via `EncryptedSharedPreferences`. Single key (`api_key_openrouter`) stored in a preferences file named `secret_keys`. |
| `MainActivity` | `MainActivity.kt` | Launcher activity. Fullscreen WebView hosting the htmx frontend; opens external links in the browser, drives the status-bar color from the accent, and forwards the hardware back button to `wwBack()` in JS. |
| `WordWiseApp` | `WordWiseApp.kt` | `Application` subclass; starts the embedded Ktor server before the WebView exists. |
| `WwServer` | `server/WwServer.kt` | Embedded Ktor (CIO) server on `127.0.0.1:8977`. Serves the shell, screens, assets, live status JSON, and the key/theming API. |
| `Shell` / `Views` | `server/Shell.kt`, `server/Views.kt` | Poet design-system CSS/JS shell (Outfit font, pastel accents, canvas tints, toasts) and the server-rendered Home/Settings screens. |
| `Prefs` | `data/Prefs.kt` | Plain SharedPreferences for accent and canvas tint. |

### Prompts

WordWise sends two different system prompts to OpenRouter depending on the command:

**Grammar correction** (`?fix`):

```
You are a grammar and style correction assistant.
Return only the corrected text.
Preserve the original language and meaning exactly.
Do not add any explanations, commentary, or quotation marks.
Never use em-dashes; use a comma, colon, or restructure the sentence instead.
```

**AI assistant** (`?ask`):

```
You are a helpful, knowledgeable AI assistant.
Follow the user's instructions precisely.
Return only the result with no commentary, explanations, or quotation marks.
Do not use Markdown or any other formatting -- output must be plain text suitable for direct insertion into a text field.
If the request is ambiguous, give your best interpretation.
```

Because the grammar prompt instructs the model to preserve the original language, WordWise works with any language that the model supports -- but this is a property of the model, not a language-detection feature in the app.

### Threading

- `GrammarFixService` launches correction on `Dispatchers.Main` with a `SupervisorJob`, so individual failures don't cancel the service scope.
- `AiClient.fixGrammar()` and `AiClient.ask()` switch to `Dispatchers.IO` internally for the blocking OkHttp call.
- Toasts are posted to the main thread via `Handler(Looper.getMainLooper())`.

## Networking

- **Library**: OkHttp 4.12.0 with a shared (singleton) `OkHttpClient`.
- **Endpoint**: `https://openrouter.ai/api/v1/chat/completions` (OpenAI-compatible).
- **Model**: `openrouter/free` (free models router, no model selection).
- **Timeouts**: Connect 15s, Read 60s, Write 30s.
- **Serialization**: `kotlinx-serialization-json` 1.6.3 for building and parsing request/response bodies.
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

- **OpenRouter only**: Other providers are not implemented. Only OpenRouter is supported.
- **No model selection**: WordWise uses the OpenRouter free models router (`openrouter/free`). You cannot pick a specific model.
- **Field support**: Some secure fields (passwords) and custom-drawn views (e.g., rich-text editors, code editors) may not support `ACTION_SET_TEXT` and cannot be corrected.
- **Large text**: For `?fix`, inputs exceeding 1,000 characters trigger a warning. For `?ask`, inputs exceeding 10,000 words trigger a warning.
- **No multi-turn**: `?ask` is single-turn only. There is no conversation history or context carried between requests.
- **No offline mode**: All corrections require internet access to the OpenRouter API.
- **Single API key**: Only one OpenRouter key is stored at a time -- no per-app or per-provider key rotation.
- **No undo**: Text replacement is immediate. There is no "undo correction" functionality.

## Tech Stack

- **Language**: Kotlin 2.0.21
- **JVM target**: 17
- **Build system**: Gradle with Kotlin DSL (AGP 8.9.1)
- **Min SDK / Target SDK**: 26 / 35
- **Networking**: OkHttp 4.12.0 (OpenRouter), embedded Ktor 2.3.13 CIO server (frontend)
- **Serialization**: kotlinx-serialization-json 1.6.3
- **Coroutines**: kotlinx-coroutines-android 1.7.3
- **UI**: htmx frontend in a WebView, Poet pastel design system (Outfit font, 4 accents x 3 canvas tints)
- **Secure storage**: EncryptedSharedPreferences (AES-256-GCM + AES-256-SIV)
- **Architecture**: Activity + Service (not MVVM)
- **ProGuard**: Minification enabled for release builds
