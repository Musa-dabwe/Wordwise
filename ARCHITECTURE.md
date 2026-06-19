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
| **Provider enum** | `Provider.kt` | Enumerates supported AI backends (OLLAMA, GEMINI) with display metadata. Drives the UI dropdown and AiClient routing. |
| **MainActivity** | `MainActivity.kt` | Configuration UI for API keys, provider selection, and service status monitoring. |

## Flow Diagram

```mermaid
graph TD
    User((User)) -- types shortcut --> GFS[GrammarFixService]

    subgraph App Logic
        GFS -- reads provider + key --> AKR[ApiKeyRepository]
        GFS -- requests fix --> AIC[AiClient]
        MA[MainActivity] -- saves provider + key --> AKR
        MA -- reads provider --> PRV[Provider enum]
    end

    AIC -- TLS · Bearer token --> Ollama[Ollama Cloud API]
    AIC -- TLS · ?key= param --> Gemini[Gemini API]
    Ollama -- Correction --> AIC
    Gemini -- Correction --> AIC
    AIC -- Result.Success/Failure/RateLimited --> GFS
    GFS -- replaceText --> Field((Input Field))
```

## Key Design Decisions

- **Dual-Provider Design**: AiClient routes to Ollama Cloud or Google Gemini based on the Provider enum value stored in ApiKeyRepository. The active provider is persisted alongside its API key, so the service always uses the user's last configured choice without requiring MainActivity to be open. The sealed Result type (Success / Failure / RateLimited) gives GrammarFixService typed, exhaustive handling of all outcomes, including Ollama's free-tier quota errors.
- **Accessibility vs. IME**: WordWise uses an AccessibilityService instead of a custom Input Method Editor (IME) to remain keyboard-agnostic. Users can keep using Gboard, SwiftKey, or any other keyboard.
- **Unified Prompt (v2)**: The system uses a strict "Return only corrected text" instruction set, which handles both more reliably than explicit scope instructions. The same unified prompt is used for both Ollama and Gemini backends.
- **OkHttp Singleton**: A shared `OkHttpClient` is used in `AiClient` to take advantage of connection pooling and keep the app's memory footprint low.
- **No Local DB**: To minimize complexity and security surface area, WordWise uses only `EncryptedSharedPreferences`. No SQLite/Room database is present.
