# Rovex v8.3.252 — Free AI Keystore / SAVE & TEST Hardening

Baseline: v8.3.251 Keystore IV correction.

## Fixes
- Keeps Android Keystore AES/GCM encryption on the provider-generated randomized IV path.
- Persists the generated 12-byte GCM IV together with ciphertext.
- Uses synchronous SharedPreferences commit for the API-key persistence boundary so SAVE cannot report success before persistence is accepted.
- Performs encrypted-key read-back/decryption verification immediately after saving.
- Converts decryption/corruption failures into a safe user-facing re-save instruction without exposing key material.
- Keeps API keys out of logs and retains password-safe API-key fields.
- Changes failed connection-test messaging to distinguish a successfully persisted key from a failed network/provider test.
- Does not weaken encryption or store plaintext keys.

Version: 8.3.252
VersionCode: 346
Package: com.localqbank.library
