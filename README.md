# WordWise

System-wide grammar correction for Android using the Gemini API.

WordWise is an Android accessibility service that intercepts text input and sends it to the Google Gemini API for professional grammar and style correction. It operates within any app, triggered by simple text shortcuts.

<!-- TODO: Add screenshot of MainActivity -->

## How It Works

WordWise leverages Android's AccessibilityService to monitor `typeViewTextChanged` events. It intelligently detects specific shortcuts typed at the end of your text and replaces the original text with a corrected version.

- **Intelligent Monitoring**: The service only processes text when one of the three trigger shortcuts is detected.
- **Three Shortcut Modes**:
  - `?fixs`: Corrects the preceding sentence.
  - `?fixp`: Corrects the preceding paragraph.
  - `?fixo`: Corrects all text within the current input field.
- **Privacy First**: Sensitive fields (passwords, PINs, etc.) are automatically skipped to ensure your credentials are never processed.
- **Powered by Gemini**: Uses the `gemini-2.5-flash-lite` model for fast, high-quality corrections.

## Setup

1. **Get a Gemini API Key**: Visit [Google AI Studio](https://aistudio.google.com) to generate a free API key.
2. **Configure WordWise**: Open the WordWise app, enter your API key, and tap **Save API Key**. Your key is stored securely using `EncryptedSharedPreferences`.
3. **Enable the Service**: Tap **Enable Accessibility Service** to open your device settings, find "WordWise" in the list of services, and turn it on.

## Usage

Simply type your text in any app, followed by one of the shortcuts.

### Examples

- **Sentence Correction (`?fixs`)**
  - Type: `The meeting start at 9am?fixs`
  - Result: `The meeting starts at 9:00 AM.`

- **Paragraph Correction (`?fixp`)**
  - Type: `i went to the store today. i bought some milk and bread. it was very crowded?fixp`
  - Result: `I went to the store today and bought some milk and bread. It was very crowded.`

- **Full Field Correction (`?fixo`)**
  - Type: `[Entire block of text]?fixo`
  - Result: `[Grammatically corrected version of the entire block]`

<!-- TODO: Add demo gif of ?fixs in action -->

## Security & Privacy

WordWise is designed with security as a priority:

- **Secure Key Storage**: API keys are stored using `EncryptedSharedPreferences` (AES-256-GCM).
- **Sensitive Field Filtering**: The app explicitly skips fields marked as passwords or sensitive numeric inputs.
- **Zero Logging**: User text and API keys are never logged to Logcat or any external server (other than the Gemini API).
- **Network Security**: Cleartext traffic is disabled via `network_security_config.xml`, ensuring all API communication is encrypted.
- **Minimal Transmission**: Text is only sent to Google Gemini when a shortcut is explicitly typed.
- **API Authentication**: The API key is transmitted as a URL query parameter, which is the standard mechanism for the Gemini API.

## Limitations

- **Accessibility Permission**: Requires broad accessibility permissions to monitor and replace text, which is inherent to how the app functions.
- **App Compatibility**: Text replacement may not function correctly in some apps, such as those using WebViews or highly custom UI frameworks.
- **Connectivity**: Requires an active internet connection to communicate with the Gemini API. No offline mode is available.

## Tech Stack

- **Language**: Kotlin
- **Networking**: OkHttp
- **Serialization**: kotlinx.serialization
- **Storage**: EncryptedSharedPreferences (Android Jetpack Security)
- **AI**: Google Gemini API (`gemini-2.5-flash-lite`)
