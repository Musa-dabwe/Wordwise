# Learnings from WordWise Task

## Inline Spinner Implementation in Accessibility Service
- **Pattern:** Using a `Handler` with a `Runnable` to periodically update the text field via `ACTION_SET_TEXT`.
- **Node Lifecycle:** Crucial to use `AccessibilityNodeInfo.obtain(node)` for a dedicated copy used by the spinner, and ensuring it is recycled in a `stopSpinner` function to avoid leaks and `IllegalStateException`.
- **User Feedback:** Appending a spinner glyph to the existing text provides localized feedback without needing a separate UI element or toast.

## Dynamic Model Selection
- **Wiring:** Threading a `model` string from the UI (stored in `SharedPreferences`) through the `AccessibilityService` to the `AiClient` allows for real-time model switching without service restarts.
- **REST URL:** Constructing the Gemini API URL dynamically with the model name: `https://generativelanguage.googleapis.com/v1beta/models/{modelName}:generateContent?key={apiKey}`.
