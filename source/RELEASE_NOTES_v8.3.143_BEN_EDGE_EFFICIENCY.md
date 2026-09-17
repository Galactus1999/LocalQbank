# Rovex v8.3.143 — Ben Edge Efficiency Foundation

## Implemented
- Added bounded prompt-level sliding-window policy for neural context. LiteRT-LM remains the owner of its internal KV cache.
- Added bounded two-hop local graph retrieval boost. Graph edges are retrieval signals only, never clinical truth.
- Added a conservative contextual-retention bandit advisor as a pure local component; adaptive SRS remains behavior-preserving and disabled until explicitly enabled/validated.
- Added a central inference-efficiency capability policy documenting which optimizations are actually enabled.
- Preserved isolated inference-process architecture and deterministic fallback.

## Deliberately not enabled
- Speculative/MTP decoding: LiteRT-LM supports it only for compatible model artifacts; the current Gemma 3 270M artifact has not been proven to contain a supported drafter. Current upstream reports also show device/runtime-specific regressions, so no speculative flag is forced.
- INT4/INT8 KV-cache manipulation: LiteRT-LM owns the KV cache and no supported public API in the current integration permits safe application-level quantization. Prompt bounding is used instead.
- AHardwareBuffer tensor IPC: 768-float embeddings are only ~3 KB, so native shared-buffer complexity would not be justified on this hot path. Keep Binder IPC for now.
- mmap model management: model paths are already handed to the runtime; application-level mmap would duplicate/compete with runtime memory management.
