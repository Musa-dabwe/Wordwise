# Security Policy

WordWise is committed to protecting user privacy and data security. As an accessibility service that handles text input, we implement multiple layers of protection to ensure sensitive information remains secure.

## Threat Model

### What WordWise Protects Against
- **API Key at Rest**: API keys are stored using `EncryptedSharedPreferences` with AES-256-GCM encryption, preventing simple extraction from the device.
- **Sensitive Field Bypass**: The service explicitly ignores fields marked as password, numeric password, or visible password.
- **Data in Transit**: Cleartext traffic is disabled globally in the app, forcing all network communication over TLS.
- **Information Disclosure**: User-provided text and API keys are never written to Logcat or persistent log files.

### What WordWise Does Not Protect Against
- **Device OS Compromise**: If the Android OS itself is compromised (e.g., root access), the security boundaries of the app can be bypassed.
- **Malicious Apps on Debug Builds**: On debug builds, or devices with `READ_LOGS` permissions, other apps might attempt to monitor activity, though WordWise minimizes what is logged.
- **Upstream Privacy**: Data handling once it reaches the Gemini API is subject to Google's privacy policy and terms of service.

## Known Security Considerations

- **Accessibility Service Scope**: By design, WordWise requires the `BIND_ACCESSIBILITY_SERVICE` permission. This is a broad permission that allows the app to see and interact with screen content. Users must trust the application to handle this responsibility.
- **API Key Transmission**: The Gemini API requires the API key to be passed as a URL query parameter (`?key=...`). This is the standard Google authentication mechanism for this API. While encrypted via TLS, it remains visible in the full URL of the request.
- **Text Transmission**: Text is processed off-device. When a shortcut is triggered, the text is sent to Google's servers for correction.
- **`recycle()` Deprecation**: The code uses `AccessibilityNodeInfo.recycle()`. While deprecated in API 33+, it remains safe as the framework handles it automatically in newer versions.

## Resolved Security Issues

| Feature | Description | Status |
|---------|-------------|--------|
| **Key Storage** | Switched from plain SharedPreferences to `EncryptedSharedPreferences`. | Fixed |
| **Cleartext Traffic** | Disabled via `network_security_config.xml`. | Fixed |
| **Logging** | Removed user text and API keys from Logcat. | Fixed |
| **Input Filtering** | Added checks for `inputType` to skip password fields. | Fixed |
| **Dependency Hardening** | Added ProGuard rules to obfuscate and minify the release build. | Fixed |
| **API Endpoint** | Migrated to latest Gemini API with optimized prompts. | Fixed |

## Security Audit Findings

The following items were identified during the recent security audit:

| Finding | Severity | Status | Recommendation |
|---------|----------|--------|----------------|
| **Alpha Dependency** | Low | Open | Upgrade `androidx.security:security-crypto` from `1.1.0-alpha06` to `1.0.0` stable when possible. |
| **URL API Key** | Informational | Accepted Risk | Standard Gemini API mechanism; no action needed. |
| **Broad Permissions** | Informational | Accepted Risk | Required for app functionality; documented in README. |

## Reporting a Vulnerability

If you discover a security vulnerability, please report it:
1. Open a GitHub issue with `[SECURITY]` in the title.
2. For sensitive issues, please contact the maintainer privately at:
<!-- TODO: Fill this in before publishing -->
`[MAINTAINER_EMAIL or GitHub profile URL]`

We do not currently have a bug bounty program.

## Dependencies

| Library | Version | Role |
|---------|---------|------|
| `androidx.security:security-crypto` | `1.1.0-alpha06` | AES-256-GCM encryption for keys |
| `okhttp3:okhttp` | `4.12.0` | Secure TLS networking |
| `kotlinx.coroutines` | `1.7.3` | Async task management |
