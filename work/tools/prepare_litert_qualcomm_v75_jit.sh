#!/usr/bin/env bash
set -euo pipefail

# Rovex / Ben: prepare the official LiteRT Qualcomm SM8650 (Hexagon v75)
# JIT runtime for the current LiteRT 2.2.0 CompiledModel path.
# This is deliberately a build-time preparation step; proprietary Qualcomm
# runtime binaries are never checked into Rovex source control.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LITERT_VERSION="${LITERT_VERSION:-2.2.0}"
WORK="${ROOT}/build/litert_npu_runtime"
ARCHIVE="${WORK}/litert_npu_runtime_libraries_jit.zip"
URL="https://github.com/google-ai-edge/LiteRT/releases/download/v${LITERT_VERSION}/litert_npu_runtime_libraries_jit.zip"
DEST="${ROOT}/app/src/main/jniLibs/arm64-v8a"

mkdir -p "$WORK" "$DEST"

# Preserve the 2.35 GB vendor archive when CI caches the preparation directory.
# Re-download only when the archive is absent or obviously empty/corrupt.
if [[ ! -s "$ARCHIVE" ]]; then
  rm -f "$ARCHIVE"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --retry-delay 2 "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
  else
    echo "ERROR: curl or wget is required to prepare LiteRT Qualcomm runtime." >&2
    exit 1
  fi
fi
rm -rf "$WORK/unpacked"

unzip -q "$ARCHIVE" -d "$WORK/unpacked"

ROOT_RUNTIME="$WORK/unpacked/qualcomm_runtime_v75"
if [[ ! -d "$ROOT_RUNTIME" ]]; then
  echo "ERROR: expected qualcomm_runtime_v75 was not found in the official LiteRT archive." >&2
  find "$WORK/unpacked" -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

# The official archive contains fetch_qualcomm_library.sh for the Qualcomm QAIRT
# runtime. Run it only if the required Qualcomm libraries are not already present.
FETCH="$WORK/unpacked/fetch_qualcomm_library.sh"
if [[ -f "$FETCH" ]]; then
  chmod +x "$FETCH"
  (cd "$WORK/unpacked" && ./fetch_qualcomm_library.sh)
fi

SRC="$ROOT_RUNTIME/src/main/jni/arm64-v8a"
if [[ ! -d "$SRC" ]]; then
  echo "ERROR: Qualcomm v75 arm64 runtime directory missing: $SRC" >&2
  exit 1
fi

# Copy only the v75 arm64 runtime. SM8650 is v75. Never mix v73/v75/v79/v81.
find "$SRC" -maxdepth 1 -type f -name '*.so' -exec cp -f {} "$DEST/" \;

required=(
  libLiteRtDispatch_Qualcomm.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libQnnHtp.so
  libQnnSystem.so
  libQnnHtpPrepare.so
  libQnnIr.so
  libQnnSaver.so
)
for f in "${required[@]}"; do
  if [[ ! -s "$DEST/$f" ]]; then
    echo "ERROR: required Qualcomm LiteRT library missing: $DEST/$f" >&2
    exit 1
  fi
done

# JIT execution on SM8650 also needs the matching HTP v75 stub/skel libraries.
shopt -s nullglob
v75=("$DEST"/libQnnHtpV75*.so)
if (( ${#v75[@]} < 1 )); then
  echo "ERROR: no QNN HTP v75 stub/skel library found in $DEST" >&2
  exit 1
fi

printf 'Prepared LiteRT %s Qualcomm SM8650/v75 JIT runtime:\n' "$LITERT_VERSION"
ls -lh "$DEST"/*.so
