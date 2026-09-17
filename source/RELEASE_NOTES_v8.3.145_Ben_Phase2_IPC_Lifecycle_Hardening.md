# Rovex v8.3.145 — Ben Phase 2 IPC Lifecycle Hardening

Version code: 243
Application ID: com.localqbank.library

## Purpose

Harden the isolated Ben inference process against lifecycle/concurrency races before any new AI capability work.

## Changes

- Separate client terminal delivery from service-side native release state.
- Generation cancellation watchdog now observes native-release completion rather than terminal-message delivery.
- Serialize Gemma generation, EmbeddingGemma rerank/compare/diagnostic operations, and runtime trimming through the same service-level single-flight barrier.
- Prevent `onTrimMemory()` from closing native runtimes concurrently with an active inference operation.
- Track cancellable one-shot IPC operations so a client cancellation can suppress obsolete replies.
- Add thermal admission denial for new one-shot neural work at severe thermal status.
- Preserve latest-request-wins for generation while ensuring replacement work cannot start until the previous native operation has returned.

## Validation status

Static/source validation is required before release. Android Gradle/CI compilation remains unverified until an actual build succeeds. Physical SM8650 stress validation remains a mandatory device gate for Phase 2.
