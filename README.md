# WordWise - AI-Powered Autocorrect

WordWise is an Android application that provides system-wide grammar correction using the power of AI. It leverages an Accessibility Service to monitor text input and a custom shortcut to trigger grammar and spelling fixes in any app.

## How it Works

The app uses an Accessibility Service to watch for text changes. When you type the shortcut `?fixg` at the end of a sentence or phrase, the app will:

1.  Detect the shortcut and remove it from the text.
2.  Send the remaining text to the OpenAI API for grammar correction.
3.  Replace the original text with the corrected version provided by the AI.

## Setup

To use WordWise, you need to perform two setup steps:

1.  **Enter your OpenAI API Key**:
    *   Open the WordWise app.
    *   Enter your OpenAI API key in the text field.
    *   Tap the "Save API Key" button. The key is stored securely on your device using `EncryptedSharedPreferences`.

2.  **Enable the Accessibility Service**:
    *   After saving your API key, tap the "Enable Accessibility Service" button.
    *   This will take you to your device's Accessibility settings.
    *   Find "WordWise" in the list of services and turn it on.

## Usage

Once the setup is complete, you can use WordWise in any app that has a text field (e.g., messaging apps, note-taking apps, email clients).

1.  Type the text you want to correct.
2.  At the end of your text, type the shortcut `?fixg`.
3.  The app will automatically correct the text and replace it in the text field.

**Example:**

If you type: `helo world?fixg`

The app will replace it with: `Hello world`

## Security and Privacy

*   **API Key Storage**: Your OpenAI API key is stored securely using Android's `EncryptedSharedPreferences`.
*   **Text Processing**: The text you write is only sent to the OpenAI API when you use the `?fixg` shortcut. The app does not log or store your text.
*   **Permissions**: The app only requires the "Bind Accessibility Service" permission to function.

## Building the Project

To build this project, you will need to have the Android SDK installed and configured. Create a `local.properties` file in the root of the project with the following content:

```
sdk.dir=<path_to_your_android_sdk>
```

Then, you can build the project using the following command:

```bash
./gradlew build
```