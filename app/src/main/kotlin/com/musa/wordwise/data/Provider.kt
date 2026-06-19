package com.musa.wordwise.data

/**
 * Represents a supported AI backend provider.
 *
 * @param displayName  Human-readable name shown in the UI dropdown.
 * @param apiKeyLabel  Label text shown above the API key input field.
 * @param apiKeyHint   Placeholder hint shown inside the API key input field.
 * @param apiKeyUrl    URL the user visits to obtain a key for this provider.
 */
enum class Provider(
    val displayName: String,
    val apiKeyLabel: String,
    val apiKeyHint: String,
    val apiKeyUrl: String,
) {
    OLLAMA(
        displayName = "Ollama Cloud (free)",
        apiKeyLabel = "Ollama API Key",
        apiKeyHint  = "ollama_...",
        apiKeyUrl   = "https://ollama.com/settings/keys",
    ),
    GEMINI(
        displayName = "Google Gemini",
        apiKeyLabel = "Gemini API Key",
        apiKeyHint  = "AIza...",
        apiKeyUrl   = "https://aistudio.google.com",
    ),
}
