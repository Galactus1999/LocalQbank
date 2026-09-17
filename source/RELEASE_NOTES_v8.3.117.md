# Rovex v8.3.117

## Stage 3 + Stage 4 stabilization

- MainActivity keeps presentation/navigation responsibility and now uses the Activity Result API for HTML document selection.
- MainActivity retains ViewModel/repository ownership for Home state and persistence; no direct QBankDb/ProgressStore/SharedPreferences access was reintroduced.
- QuizActivity remains presentation-first with QuizViewModel/use-case ownership of quiz state and operations; no direct persistence ownership was reintroduced.
- HtmlImportActivity no longer owns QBankDb directly.
- Added HtmlImportRepository as an operation-scoped persistence seam.
- Existing ImportPipelineCoordinator is now the authoritative serial scheduler for HTML import work.
- Coordinator completion releases its serialization gate before invoking callbacks, allowing multi-file imports to continue without a race/stall.
- Large-file streaming path remains incremental and operation-scoped; it does not materialize the full QBank in memory.
- WebView cleanup hardened using stopLoading, clearHistory, parent removal, and destroy.
- Hidden extraction WebView explicitly disallows popups/multiple windows and keeps mixed-content disabled.
- No importer parser rewrite was performed in this stabilization stage; the existing parsing/streaming algorithms remain intact for the next redesign stage.

## Verification

- versionName: 8.3.117
- versionCode: 215
- applicationId: com.localqbank.library
- Direct persistence ownership in MainActivity/QuizActivity/HtmlImportActivity: none
- Prohibited-pattern scan: PASS
- XML parse / per-layout duplicate-ID audit: PASS
- Local Android Gradle compilation: NOT VERIFIED because services.gradle.org DNS is unavailable in the current environment.
- CI remains the authoritative Android build gate.
