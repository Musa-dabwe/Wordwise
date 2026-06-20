# WordWise


## How It Works

- **Seamless Integration**: Works in any Android app using a system-wide Accessibility Service.
- **Shortcuts**: Simply type `?fix` at the end of any text to trigger correction.
- **Privacy-First**: Your text is processed using your own API keys. No data is stored or logged by WordWise.
- **Multilingual**: Corrects grammar, spelling, and style in over 100 languages.

## Setup

### 1. Install WordWise
Sideload the APK onto your Android device (requires Android 6.0+).

### 2. Configure AI Provider

1. Create a free account at [ollama.com](https://ollama.com).
2. Generate an API key at [ollama.com/settings/keys](https://ollama.com/settings/keys) — no credit card required.

#### Option B — Google Gemini
1. Generate a key at [Google AI Studio](https://aistudio.google.com).
2. Open WordWise, select **Google Gemini** from the provider dropdown, paste your key, and tap **Save API Key**.

### 3. Enable Accessibility Service
1. In WordWise, tap **Open Accessibility Settings**.
2. Find **WordWise** in the list of installed services.
3. Toggle the switch to **On**.

## Usage

In any text field (WhatsApp, Slack, Gmail, etc.), type your text followed by the shortcut:

> i dont no how to spel?fix

Wait 2–3 seconds, and it will be replaced with:

> I don't know how to spell.

## Security & Privacy

- **Local Encryption**: API keys are stored using `EncryptedSharedPreferences` (AES-256-GCM).
- **API Authentication**:
  - **Gemini**: Key transmitted as a URL query parameter (standard Google mechanism, encrypted via TLS).
- **No Data Retention**: Text is sent directly to the selected AI provider. WordWise does not log or store any text.

## Limitations

- **Field Support**: Some highly secure fields (passwords) or custom-drawn views may not support text replacement.

## Tech Stack

- **Language**: Kotlin
- **Networking**: OkHttp 4
- **Architecture**: MVVM (ViewBinding)
