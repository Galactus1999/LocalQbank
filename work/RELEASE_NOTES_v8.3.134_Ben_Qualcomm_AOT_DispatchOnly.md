# Rovex v8.3.134 — Ben Qualcomm AOT Dispatch-Only

Version: 8.3.134  
VersionCode: 232

## Purpose
Target the remaining EmbeddingGemma Qualcomm SM8650 failure after v8.3.133 established that the model contract and `CompiledModel.create()` succeed but invocation fails.

## Main change
The SM8650 EmbeddingGemma artifact is already AOT-compiled and contains a Qualcomm `DISPATCH_OP`. The runtime now receives an explicit LiteRT Environment containing only the Qualcomm DispatchLibraryDir instead of the convenience NPU-provider Environment that also supplies a CompilerPluginLibraryDir.

Qualcomm execution also receives explicit VERBOSE logging and BURST HTP performance mode for diagnostics.

## Diagnostic improvements
The report now inventories the critical Qualcomm runtime libraries with size and SHA-256 so a future failure can be distinguished from a missing/mismatched runtime binary.

## Safety
- No generic CPU fallback for the Qualcomm DISPATCH_OP model.
- Deterministic Ben/RAG remains authoritative if neural execution fails.
- Existing resource governor remains in force.
- No study data is modified by diagnostics.

## Build status
Local Android Gradle compilation is unverified because `downloads.gradle.org` DNS resolution is unavailable in the current environment. Do not treat this source as CI-green until CI passes.
