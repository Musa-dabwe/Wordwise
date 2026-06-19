# Security & Privacy Policy — WordWise

## Threat Model

### What WordWise Protects Against

- **API Key at Rest**: API keys (one per provider) are stored using `EncryptedSharedPreferences` with AES-256-GCM encryption, preventing simple extraction from the device.
- **Sensitive Fields**: The `GrammarFixService` explicitly ignores fields marked as passwords (`TYPE_TEXT_VARIATION_PASSWORD`, etc.) and does not process or transmit their contents.
- **Unintended Transmission**: Data is only transmitted when the user explicitly types the `?fix` shortcut. No passive background logging occurs.

### What WordWise Does Not Protect Against

- **Root Access**: A user with root/superuser access to the device can bypass Android's sandbox and potentially access encrypted storage.
- **Upstream Privacy**: Data handling once it reaches the Ollama or Gemini API is subject to the respective provider's privacy policy and terms of service. Ollama's cloud inference partners are contractually bound to zero-data-retention policies.
- **Malicious Accessibility Services**: If another malicious accessibility service is active on the device, it could theoretically "scrape" WordWise's UI or monitor the same input fields.

## Known Security Considerations

### API Key Transmission
- **Ollama Cloud**: The API key is transmitted as an HTTP `Authorization: Bearer` header. This is more secure than a query parameter because it is not recorded in server access logs or browser history.
- **Gemini**: The API key is transmitted as a URL query parameter (`?key=...`). This is the standard Google authentication mechanism. While encrypted via TLS, it remains visible in the full URL of the request.

### Text Transmission
All text to be corrected is sent to Ollama's or Google's servers, depending on the active provider. All connections use TLS 1.2 or 1.3. Cleartext (HTTP) traffic is strictly disabled in the app's `network_security_config.xml`.

## Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `androidx.security:security-crypto` | 1.0.0 | AES-256-GCM implementation for encrypted preferences |
| `okhttp3:okhttp` | 4.12.0 | TLS-secured network communication |
| `kotlinx.serialization` | 1.6.3 | Tree-based JSON building and parsing for both provider request/response formats |
