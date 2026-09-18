# Rovex v8.3.46 Peak Stability Audit

## Release identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.46`
- versionCode: `144`

## Stability changes
1. Fixed the CI release identity gate so it cannot accidentally validate an older versionCode/versionName.
2. Added an Activity lifecycle resource-ownership CI audit for Activity-owned QBankDb instances and executors.
3. Fixed Activity-owned DB lifetime leaks in HtmlImportActivity and TestListActivity.
4. Explicitly destroy HtmlImportActivity's WebView on Activity destruction to release Chromium/renderer resources after large imports.
5. Changed the resilience watchdog from fixed-rate to fixed-delay scheduling so a delayed watchdog iteration cannot create a backlog of immediate executions.
6. Expanded debug StrictMode VM diagnostics to include leaked registrations and Activity leaks.
7. Added lightweight Android Trace sections around startup and AppManagers initialization for measurement without changing business logic.

## Research basis
- Android recommends Macrobenchmark for repeatable startup/runtime measurement and comparison of TTID/TTFD.
- Android recommends Baseline Profiles only after measurement and automated profile generation for critical user journeys.
- Android performance samples demonstrate Macrobenchmark + Baseline Profile generation and CI integration.
- Kotlin structured concurrency guidance emphasizes lifecycle-bound work; Rovex currently uses Java executors in several legacy Activities, so the release adds ownership auditing rather than a risky wholesale coroutine migration.

## Deliberate non-changes
- No new AI model/dependency was added to the stability build. Hugging Face/LiteRT research is reserved for Ben's separate on-device intelligence optimization track because model/runtime changes can materially affect RAM, startup and native-library stability.
- No Baseline Profile was guessed or hand-authored; the next performance step should be measured Macrobenchmark coverage first.

## Verification performed
- Whole-source static scan for prohibited blocking patterns.
- XML/source structure retained.
- Lifecycle audit executed locally against all Activity sources.
- ZIP/archive integrity will be checked after packaging.
- Android compilation remains CI-authoritative in this environment.
