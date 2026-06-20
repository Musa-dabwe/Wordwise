# Architecture — WordWise

**Date**: 2026-06-07 (Multi-provider update)

## Overview

WordWise is a system-wide accessibility-based utility that provides grammar correction across all Android applications. It operates by monitoring text changes via an `AccessibilityService`, detecting a specific trigger shortcut (`?fix`), and using a remote LLM to perform corrections.

## Component Map

| Component | File | Description |
|---|---|---|
| **GrammarFixService** | `GrammarFixService.kt` | The core AccessibilityService. Monitors window state and text changes. Dispatches correction requests to AiClient. |
| **AiClient** | `AiClient.kt` | Singleton managing OkHttpClient, Ollama Cloud, and Gemini backends. fixGrammar(text, apiKey, provider) routes to the correct backend. Returns a sealed Result type. |
| **ApiKeyRepository** | `ApiKeyRepository.kt` | Secure storage of per-provider API keys and active provider selection using EncryptedSharedPreferences. Single source of truth. |
| **MainActivity** | `MainActivity.kt` | Configuration UI for API keys, provider selection, and service status monitoring. |

## Flow Diagram

```mermaid
graph TD
    User((User)) -- types shortcut --> GFS[GrammarFixService]

    subgraph App Logic
        GFS -- reads provider + key --> AKR[ApiKeyRepository]
        GFS -- requests fix --> AIC[AiClient]
        MA[MainActivity] -- saves provider + key --> AKR
    end

    AIC -- TLS · Bearer token [Ollama Cloud API]
    AIC -- TLS · ?key= param --> Gemini[Gemini API]
    Ollama -- Correction --> AIC
    Gemini -- Correction --> AIC
    AIC -- Result.Success/Failure/RateLimited --> GFS
    GFS -- replaceText --> Field((Input Field))
```

## Key Design Decisions

- **Accessibility vs. IME**: WordWise uses an AccessibilityService instead of a custom Input Method Editor (IME) to remain keyboard-agnostic. Users can keep using Gboard, SwiftKey, or any other keyboard.
- **Unified Prompt (v2)**: The system uses a strict "Return only corrected text" instruction set, which handles both more reliably than explicit scope instructions. The same unified prompt is used for both Ollama and Gemini backends.
- **OkHttp Singleton**: A shared `OkHttpClient` is used in `AiClient` to take advantage of connection pooling and keep the app's memory footprint low.
- **No Local DB**: To minimize complexity and security surface area, WordWise uses only `EncryptedSharedPreferences`. No SQLite/Room database is present.
