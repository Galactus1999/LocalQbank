# Rovex v8.3.91

## Focus
EmbeddingGemma model-import compatibility fix.

## Fix
The neural model manager previously treated a TFLite `.tflite` artifact as invalid unless the first four bytes were `TFL3`. TFLite models are FlatBuffers: the first four bytes contain the root-table offset and the `TFL3` file identifier is stored at byte offset 4. The validator now checks the identifier at the correct offset while retaining the size sanity check.

This specifically fixes valid EmbeddingGemma 300M `.tflite` artifacts being rejected by Model Lab as an invalid file format.

## Version
- versionName: 8.3.91
- versionCode: 189
