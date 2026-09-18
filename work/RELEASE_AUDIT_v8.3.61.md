# Rovex v8.3.61 Stability Audit

- Bundled stability slice based on v8.3.58.
- Macrobenchmark configured as a self-instrumenting test target, following current Android performance-sample structure.
- Benchmark uses explicit Java 17 compatibility and MemoryUsageMetric.Mode.Max.
- Ben local inference now has a narrow offline backend interface; no model runtime or network transport is included.
- UnavailableBenInferenceBackend is deterministic and covered by a JVM unit test.
- Production APK remains free of the benchmark module and debug-only JankStats dependency.
- CI verifies benchmark self-instrumentation, backend boundary files, version identity, signing, zstd payload, and existing runtime/source audits.
- Full Android build remains CI-authoritative because local Gradle distribution access is unavailable in this environment.
