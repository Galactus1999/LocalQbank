# Release audit v8.3.177

- Application ID: `com.localqbank.library`
- versionName: `8.3.177`
- versionCode: `274`
- Baseline: v8.3.176 / versionCode 273
- ZIP SHA-256: `0de95fcfce6834bc7ff1ae2890cf5c7cd6b0a1b665c71d96e04a095b8b8cc5b5`
- XML files: 28
- XML parse errors: 0
- Duplicate IDs within individual layouts: 0
- Changed Kotlin bracket-balance mismatches: 0
- Android/CI compile: **NOT VERIFIED**.
- Device runtime: **NOT VERIFIED**.

The hard one-shot timeout now resets the transport with `trim()` after emitting its failure, preventing reuse of a potentially wedged isolated process. Diagnostics UI preserves the last remote stage and explains the distinction between IPC failure and direct model failure.
