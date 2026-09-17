# Rovex v8.3.57 Stability Audit

Bundled stability slice: fully-drawn startup signaling, debug JankStats leak hardening, and Macrobenchmark metric expansion.

## Required gates
- Same applicationId: `com.localqbank.library`
- versionName: `8.3.57`
- versionCode: `155`
- Persistent signing certificate gate retained in CI
- ARM64 zstd JNI gate retained
- XML/findViewById/runtime-risk audits retained
- JVM unit tests retained
- Benchmark instrumentation compilation retained

## Performance architecture
- MainActivity reports fully-drawn only after the dashboard UI has been published.
- Startup benchmark measures TTID/TTFD through `StartupTimingMetric`.
- Trace sections cover StartupCoordinator and first usable Home state.
- MemoryUsageMetric is collected alongside startup timing.
- Debug JankStats listener uses weak Activity references to avoid listener→Activity retention cycles.

Local full Android compilation remains CI-authoritative because the local environment cannot resolve services.gradle.org.
