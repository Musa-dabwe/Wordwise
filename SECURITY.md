# Security & Privacy Policy — WordWise

## Threat Model

### What WordWise Protects Against

- **API Key at Rest**: API keys (one per provider) are stored using `EncryptedSharedPreferences` with AES-256-GCM encryption, preventing simple extraction from the device.
- **Sensitive Fields**: The `GrammarFixService` explicitly ignores fields marked as passwords (`TYPE_TEXT_VARIATION_PASSWORD`, etc.) and does not process or transmit their contents.
- **Unintended Transmission**: Data is only transmitted when the user explicitly types the `?fix` shortcut. No passive background logging occurs.

### What WordWise Does Not Protect Against

- **Root Access**: A user with root/superuser access to the device can bypass Android's sandbox and potentially access encrypted storage.
- **Malicious Accessibility Services**: If another malicious accessibility service is active on the device, it could theoretically "scrape" WordWise's UI or monitor the same input fields.

## Known Security Considerations

### API Key Transmission
- **Gemini**: The API key is transmitted as a URL query parameter (`?key=...`). This is the standard Google authentication mechanism. While encrypted via TLS, it remains visible in the full URL of the request.

### Text Transmission

## Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `androidx.security:security-crypto` | 1.0.0 | AES-256-GCM implementation for encrypted preferences |
| `okhttp3:okhttp` | 4.12.0 | TLS-secured network communication |
| `kotlinx.serialization` | 1.6.3 | Tree-based JSON building and parsing for both provider request/response formats |
