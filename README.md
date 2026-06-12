# WordWise

System-wide grammar correction for Android using the Gemini API.

WordWise is an Android accessibility service that intercepts text input and sends it to the Google Gemini API for professional grammar and style correction. It operates within any app, triggered by a simple text shortcut.

<!-- TODO: Add screenshot of MainActivity -->

## How It Works

WordWise leverages Android's AccessibilityService to monitor `typeViewTextChanged` events. It intelligently detects a specific shortcut typed at the end of your text and replaces the original text with a corrected version.

- **Intelligent Monitoring**: The service only processes text when the trigger shortcut is detected.
- **Multilingual Support**: Automatically detects and corrects over 100 languages.
- **Privacy First**: Sensitive fields (passwords, PINs, etc.) are automatically skipped to ensure your credentials are never processed.
- **Powered by Gemini**: Uses the `gemini-2.5-flash-lite` model for fast, high-quality corrections.

## Setup

1. **Get a Gemini API Key**: Visit [Google AI Studio](https://aistudio.google.com) to generate a free API key.
2. **Configure WordWise**: Open the WordWise app, enter your API key, and tap **Save API Key**. Your key is stored securely using `EncryptedSharedPreferences`.
3. **Enable the Service**: Tap **Enable Accessibility Service** to open your device settings, find "WordWise" in the list of services, and turn it on.

## Usage

Simply type your text in any app, followed by the shortcut.

### Example

- **Text Correction (`?fix`)**
  - Type: `The meeting start at 9am?fix`
  - Result: `The meeting starts at 9:00 AM.`

- **Paragraph Correction (`?fix`)**
  - Type: `i went to the store today. i bought some milk and bread. it was very crowded?fix`
  - Result: `I went to the store today and bought some milk and bread. It was very crowded.`

<!-- TODO: Add demo gif of ?fix in action -->

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
- **App Compatibility**: Text replacement may not function correctly in some apps, such as those using WebViews or highly custom UI frameworks. WordWise will now notify you with a toast if replacement fails.
- **Connectivity**: Requires an active internet connection to communicate with the Gemini API. No offline mode is available.

## Tech Stack

- **Language**: Kotlin
- **Networking**: OkHttp
- **Serialization**: kotlinx.serialization
- **Storage**: EncryptedSharedPreferences (Android Jetpack Security)
- **AI**: Google Gemini API (`gemini-2.5-flash-lite`)
