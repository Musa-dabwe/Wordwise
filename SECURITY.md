# Security & Privacy Policy — WordWise

## Threat Model

### What WordWise Protects Against

- **API Key at Rest**: The single Gemini API key is stored using `EncryptedSharedPreferences` with AES-256-SIV for key encryption and AES-256-GCM for value encryption, preventing simple extraction from the device.
- **Sensitive Fields**: The `GrammarFixService` explicitly ignores fields marked as passwords (`TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`) and does not process or transmit their contents.
- **Unintended Transmission**: Data is only transmitted when the user explicitly types the `?fix` shortcut. No passive background logging or periodic scanning occurs. The service only processes `TYPE_VIEW_TEXT_CHANGED` events and only acts when the shortcut regex matches.

### What WordWise Does Not Protect Against

- **Root Access**: A user with root/superuser access to the device can bypass Android's sandbox and potentially access encrypted storage.
- **Malicious Accessibility Services**: If anothera malicious accessibility service is active on the device, it could theoretically "scrape" WordWise's UI or monitor the same input fields.

## Known Security Considerations

### API Key Transmission
The API key is transmitted as a URL query parameter (`?key=...`) appended to the Gemini API endpoint. This is the standard Google authentication mechanism. While encrypted via TLS 1.3, the full URL (including the key) is visible in the TLS handshake's Server Name Indication (SNI) and may appear in server access logs.

### Text Transmission
User text is sent to `generativelanguage.googleapis.com` over TLS 1.3 as the request body in a JSON payload. WordWise does not log, cache, or persist any text server-side or on-device. Text is held in memory only for the duration of the coroutine that processes the correction and is discarded immediately after the network response is handled.

### Network Policy
The app's `network_security_config.xml` blocks cleartext traffic (`cleartextTrafficPermitted="false"`) and trusts only system certificates. All outbound connections are forced over TLS.

## Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `androidx.security:security-crypto` | 1.0.0 | AES-256-GCM + AES-256-SIV implementation for encrypted preferences |
| `okhttp3:okhttp` | 4.12.0 | TLS-secured network communication with connection pooling |
| `kotlinx.serialization` | 1.6.3 | JSON building and parsing for Gemini request/response bodies |
