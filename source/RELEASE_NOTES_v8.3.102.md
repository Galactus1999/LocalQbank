# Rovex v8.3.103

Deep code correction of the direct EmbeddingGemma path.

- Corrected invalid Java NIO buffer construction (`LongBuffer.allocateDirect` / `IntBuffer.allocateDirect`) by using native-order direct `ByteBuffer` backed INT64 tensors.
- Enforced the documented 512-token EmbeddingGemma tensor contract: two `[1,512]` INT64 inputs (`input_ids`, `attention_mask`) and one `[1,768]` FLOAT32 embedding output.
- Removed host-side pooling/normalization assumptions for the official 512 LiteRT graph; the model output is consumed as the final 768-D embedding.
- Added strict runtime contract diagnostics so incompatible artifacts fail with their actual tensor contract instead of a generic runtime error.
- Corrected CI release identity checks that still referenced v8.3.92 despite the v8.3.101 source claiming v8.3.101.
- Added CI guards against reintroducing unsupported direct-buffer APIs or the removed Gecko/LocalAgents embedding route.
- Version 8.3.103, versionCode 200.
