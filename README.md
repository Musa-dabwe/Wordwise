# WordWise

System-wide grammar correction for Android. Type `?fix` at the end of any text in any app and WordWise rewrites it using Google Gemini.

## How It Works

WordWise registers as an **Android Accessibility Service** and listens for `TYPE_VIEW_TEXT_CHANGED` events across all running applications. When you type `?fix` at the end of text in any input field, the service:

1. Strips the shortcut suffix and reads the surrounding text from the accessibility node.
2. Sends the text to **Google Gemini** (`gemini-2.5-flash-lite`) with a strict correction prompt.
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
- The Gemini free tier allows approximately **1,500 requests/day**.

## Security & Privacy

| Concern | Implementation |
|---|---|
| **API key storage** | `EncryptedSharedPreferences` with `MasterKeys.AES256_GCM_SPEC`. Key encryption uses AES-256-SIV; value encryption uses AES-256-GCM. |
| **Key in transit** | Transmitted as a URL query parameter (`?key=`) to `generativelanguage.googleapis.com` over TLS 1.3. |
| **Network policy** | `android:networkSecurityConfig` explicitly blocks cleartext traffic — only TLS connections are permitted. System certificate store is used for trust anchors. |
| **Data retention** | WordWise does not log, cache, or store any text sent for correction. Text is held in memory only for the duration of the network request and discarded immediately after. |
| **Sensitive fields** | Password, visible-password, and web-password input types are programmatically skipped — the service never reads their content. |

## Architecture

WordWise follows a minimal **Activity + Service** pattern (not MVVM — there are no `ViewModel` or `LiveData`/`StateFlow` classes).

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
                                    │ gemini-2.5-flash-lite
                                    │ :generateContent?key=
                                    ▼
                         Google Gemini API
```

### Components

| Component | File | Role |
|---|---|---|
| `GrammarFixService` | `GrammarFixService.kt` | Core `AccessibilityService`. Listens for text-change events, detects `?fix`, orchestrates correction via coroutines (`CoroutineScope(Dispatchers.Main + SupervisorJob)`), and replaces text on the UI node. |
| `AiClient` | `AiClient.kt` | Singleton wrapping an `OkHttpClient`. Exposes `suspend fun fixGrammar(text, apiKey): Result` (dispatches to `Dispatchers.IO`). The `Result` sealed class has three variants: `Success`, `RateLimited`, `Failure`. Only the Gemini backend is implemented. |
| `ApiKeyRepository` | `ApiKeyRepository.kt` | Reads and writes the Gemini API key via `EncryptedSharedPreferences`. Single key (`api_key_gemini`) stored in a preferences file named `secret_keys`. |
| `MainActivity` | `MainActivity.kt` | Launcher activity. Provides the API key input field, a clickable link to AI Studio, a Save button, and the accessibility service status indicator. Uses `ViewBinding` (`ActivityMainBinding`). |

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
com.squareup.okhttp3:okhttp:4.12.0
androidx.security:security-crypto:1.0.0
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3
com.google.android.material:material:1.12.0
androidx.appcompat:appcompat:1.7.1
```

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
- **Min SDK / Target SDK**: 23 / 35
- **Networking**: OkHttp 4.12.0
- **Serialization**: kotlinx-serialization-json 1.6.3
- **Coroutines**: kotlinx-coroutines-android 1.7.3
- **UI**: ViewBinding, Material 3 (Dark theme, `Theme.Material3.Dark.NoActionBar`)
- **Secure storage**: EncryptedSharedPreferences (AES-256-GCM + AES-256-SIV)
- **Architecture**: Activity + Service (not MVVM)
- **ProGuard**: Minification enabled for release builds
