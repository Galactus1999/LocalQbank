# Rovex update installation

Use **only** the artifact named `Rovex-UPDATE-v8.3.45`.

Do not install debug/profile APKs as user updates. The release APK is the canonical update artifact and is CI-gated for:

- applicationId `com.localqbank.library`
- persistent Rovex signing certificate
- strictly increasing versionCode
- ARM64 zstd native library

An Android update requires the same package/application ID and compatible signing key. If Android still reports “package conflicts with an existing package” for the canonical update APK, the currently installed Rovex was signed with a different certificate; no APK workflow can bypass that cryptographic requirement without access to the old signing key. In that case, the old signing certificate must be recovered before a data-preserving update can be guaranteed.
