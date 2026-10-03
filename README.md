# WordWise

System-wide grammar correction and AI assistant for Android. Type `?fix` at the end of any text to correct it, or `?ask` to ask a question. Powered by OpenRouter -- on the free models router by default, or on any model you pick in Settings.

## How It Works

WordWise registers as an **Android Accessibility Service** and listens for `TYPE_VIEW_TEXT_CHANGED` events across all running applications. It recognizes two commands:

- **`?fix`** -- corrects grammar and style in the surrounding text.
- **`?ask`** -- sends the preceding text as a prompt to an AI assistant.

When you type one of these triggers, the service:

1. Calls `detectCommand()` to match `?fix` or `?ask` against the current text.
2. Routes the result through a `Command` sealed class (`Command.Fix` or `Command.Ask`).
3. Sends the text to **OpenRouter** (your chosen model) via the appropriate endpoint, with a dedicated system prompt for each command.
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

### 4. Pick a Model (Optional)

Out of the box WordWise runs on `openrouter/free`, OpenRouter's free models router, which costs nothing. In **Settings** you can switch to any other model:

- **Searchable dropdown** -- the catalog is fetched from OpenRouter's model list and filtered by what you type. Tapping a row fills the field below with that `vendor/model` path.
- **Paste field** -- any `vendor/model` path can be typed or pasted directly, including a `:free` or `:variant` suffix. **SAVE MODEL** validates and persists it; an invalid path is rejected instead of silently falling back.
- **Clearing the field and saving** returns you to the free router.

The catalog is cached in memory for an hour, so browsing themes does not re-hit OpenRouter every time. It is fetched without your API key, because the endpoint is public -- see [Security & Privacy](#security--privacy).

> **Most models on OpenRouter are paid.** Anything you select bills your own OpenRouter account at that provider's rate -- WordWise adds no cost of its own. If a model needs a subscription or is otherwise unavailable to your key, OpenRouter returns an error and WordWise leaves your text untouched.

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
| **Key at rest** | Your OpenRouter key is stored with `EncryptedSharedPreferences` (AES-256-GCM for values, AES-256-SIV for keys). The master key lives in the device Keystore and never leaves it. |
| **Key is write-only** | The key is never rendered back into the settings page HTML -- there is no way to read it out of the app. Once saved, the settings screen reports only that a key exists (`WwNative.hasApiKey()`), and the API key field starts empty. |
| **Local server is display-only** | The embedded Ktor server on `127.0.0.1:8977` exposes no mutating routes and no secrets -- it serves the shell, the settings/about screens, status JSON, and the public model catalog. Reading the key and saving the key, model or theme all go through the app's own WebView bridge (`WwNative`), never over the local HTTP socket. |
| **Key in transit** | Sent via the `Authorization: Bearer` request header (never in the URL, so it cannot leak into request logs) to `openrouter.ai` over TLS 1.3. |
| **Backups** | The encrypted key store is excluded from Auto Backup and device-to-device transfer (`backup_rules.xml` / `data_extraction_rules.xml`) -- its master key lives in the device Keystore and cannot travel with a backup. |
| **Network policy** | `android:networkSecurityConfig` explicitly blocks cleartext traffic -- only TLS connections are permitted. System certificate store is used for trust anchors. |
| **Data retention** | WordWise does not log, cache, or store any text sent for correction. Text is held in memory only for the duration of the network request and discarded immediately after. |
| **Sensitive fields** | Password, visible-password, and web-password input types are programmatically skipped -- the service never reads their content. |
| **Local server hardening** | Responses carry `Cache-Control: no-store`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, and a CSP with `frame-ancestors 'none'` and `form-action 'none'`. The WebView also runs with `LOAD_NO_CACHE`. Requests carrying a foreign `Origin` are refused with `403`. |
| **Known limit** | Android's loopback interface is a **single shared namespace**, so any other app installed on the device can request `127.0.0.1:8977` directly. That is why the server serves no secrets and accepts no writes. It does mean the settings *pages* and the model catalog are readable by other apps -- both are display-only, and neither grants access to your key. |

## Architecture

WordWise follows a minimal **Activity + Service** pattern (not MVVM -- there are no `ViewModel` or `LiveData`/`StateFlow` classes).

The configuration UI is an **htmx web frontend** served by an **embedded Ktor server** (`127.0.0.1:8977`, CIO engine) into a fullscreen WebView -- the same architecture and pastel design system as PoetMusic. The server is display-only: it renders pages and status JSON, while every settings read and write goes through the `WwNative` WebView bridge. See `docs/FRONTEND_MIGRATION.md` for the full map.

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
           | reads key + model       |
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
                          (your chosen model)
```

### Components

| Component | File | Role |
|---|---|---|
| `GrammarFixService` | `GrammarFixService.kt` | Core `AccessibilityService`. Listens for text-change events, calls `detectCommand()` to route `?fix` / `?ask` through the `Command` sealed class, orchestrates correction via coroutines (`CoroutineScope(Dispatchers.Main + SupervisorJob)`), and replaces text on the UI node. |
| `AiClient` | `AiClient.kt` | Singleton wrapping an `OkHttpClient`. Exposes `suspend fun fixGrammar(text, apiKey, model): Result` for grammar correction and `suspend fun ask(prompt, apiKey, model): Result` for general questions (both dispatch to `Dispatchers.IO`). The selected model is resolved per request, blank resolving to `openrouter/free`. The `Result` sealed class has three variants: `Success`, `RateLimited`, `Failure`. |
| `ModelId` | `network/ModelId.kt` | Pure-Kotlin validation for `vendor/model[:variant]` paths. A blank entry is valid and resolves to `openrouter/free` -- that is how a cleared field returns to the free router. Android-free so it is unit-testable on the JVM. |
| `ModelCatalog` | `network/ModelCatalog.kt` | Fetches the public `GET /api/v1/models` (unauthenticated) and keeps only text-output models, trimming the catalog from ~464 entries to ~156. Results are cached for an hour behind a single-flight lock. Used by the model picker. |
| `ApiKeyRepository` | `ApiKeyRepository.kt` | Reads and writes the OpenRouter API key via `EncryptedSharedPreferences`. Single key (`api_key_openrouter`) stored in a preferences file named `secret_keys`. |
| `MainActivity` | `MainActivity.kt` | Launcher activity. Fullscreen WebView hosting the htmx frontend; opens external links in the browser, drives the status-bar color from the accent, and forwards the hardware back button to `wwBack()` in JS. Also hosts `WwNativeBridge`, the one JS-to-native trust boundary (`hasApiKey`, `saveApiKey`, `getModel`, `setModel`, `getTheme`, `setTheme`). |
| `WordWiseApp` | `WordWiseApp.kt` | `Application` subclass; starts the embedded Ktor server before the WebView exists. |
| `WwServer` | `server/WwServer.kt` | Embedded Ktor (CIO) server on `127.0.0.1:8977`. Read-only: serves the shell, screens, assets, status JSON, and the cached public `/api/models` catalog. It exposes no mutating routes and no secrets. |
| `Shell` / `Views` | `server/Shell.kt`, `server/Views.kt` | Poet design-system CSS/JS shell (Outfit font, pastel accents, canvas tints, toasts, model search) and the server-rendered Home/Settings screens. |
| `Prefs` | `data/Prefs.kt` | Plain SharedPreferences for accent, canvas tint, and the selected model. |

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
- **Model**: chosen in Settings. Defaults to `openrouter/free`; any OpenRouter `vendor/model` path is accepted. Most models are paid and bill your own account.
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
- **Model quality varies**: The free router picks models per request, so output quality and speed can change between requests. Picking a specific model in Settings is the way to get consistent behaviour.
- **Paid models cost money**: Any model you select is billed to your own OpenRouter account. WordWise adds no cost, but it also cannot tell you what a model will charge before you select it.
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
