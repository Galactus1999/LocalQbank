# Rovex v8.3.189 — IPC Transport Diagnostic Rewrite

## Why
The v8.3.188 durable journal worked and captured the real failure boundary, but the diagnostic stopped after `WORKER_DIAGNOSTIC_ACCEPTED`. The test was still coupled to a coroutine/model-runtime path, so it could not cleanly distinguish Binder/process transport from coroutine/native/model lifecycle.

## Correction
The `CMD_DIAGNOSTIC` path is now a pure transport diagnostic:
- no EmbeddingGemma initialization
- no native runtime initialization
- no coroutine dispatch required for the diagnostic response
- immediate handler-thread response after worker acceptance
- explicit handler-entry and transport-reply journal events
- bounded transport-only report with worker PID/request/generation
- client records receipt of the remote diagnostic reply

This deliberately isolates the question: can the main process start/protect the worker, bind Binder, send a command, and receive a terminal response?

## Build gate
Android Gradle compilation is not claimed here. CI must compile this exact source before installation/device testing.
