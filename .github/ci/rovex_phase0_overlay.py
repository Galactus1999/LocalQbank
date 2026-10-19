#!/usr/bin/env python3
"""Apply Phase-0 security corrections to the pinned v8.3.711 source archive."""
import re, sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle.kts"
s = gradle.read_text()
if 'versionCode = 797' not in s or 'versionName = "8.3.711"' not in s:
    raise SystemExit("Refusing unexpected source baseline; expected v8.3.711/code 797")
s = s.replace('versionCode = 797', 'versionCode = 799').replace('versionName = "8.3.711"', 'versionName = "8.3.713"')
pat = re.compile(r'val prepareQualcommRuntime = tasks\.register<Exec>\("prepareQualcommRuntime"\) \{.*?^\}\.configureEach\n', re.M | re.S)
rep = '''val qualcommRuntimeEnabled = providers.environmentVariable("ROVEX_ENABLE_QUALCOMM_RUNTIME")
    .map { it.equals("true", ignoreCase = true) }.orElse(false)
val prepareQualcommRuntime = tasks.register<Exec>("prepareQualcommRuntime") {
    description = "Optionally stages checksum-verified LiteRT Qualcomm SM8650/v75 runtime."
    workingDir = rootProject.projectDir
    commandLine("bash", "tools/prepare_litert_qualcomm_v75_jit.sh")
    onlyIf {
        val dir = qualcommRuntimeDir.asFile
        val hasV75 = dir.listFiles()?.any { it.name.startsWith("libQnnHtpV75") && it.length() > 0L } == true
        val hasAllRequired = qualcommRequiredLibs.all { name -> File(dir, name).let { it.isFile && it.length() > 0L } }
        if (dir.isDirectory && hasV75 && hasAllRequired) false
        else if (qualcommRuntimeEnabled.get()) true
        else {
            dir.listFiles()?.filter { it.name.startsWith("libQnn") || it.name.startsWith("libLiteRtDispatch_Qualcomm") || it.name.startsWith("libLiteRtCompilerPlugin_Qualcomm") }?.forEach { it.delete() }
            logger.lifecycle("Qualcomm runtime acquisition is opt-in; skipping proprietary NPU payload for this build.")
            false
        }
    }
}
tasks.matching { task ->
    task.name.matches(Regex("merge.*JniLibFolders")) || task.name.matches(Regex("merge.*NativeLibs"))
}.configureEach {
    dependsOn(prepareQualcommRuntime)
}
'''
s, n = pat.subn(rep, s, count=1)
if n != 1: raise SystemExit("Could not replace Qualcomm Gradle task exactly once")
gradle.write_text(s)

script = r'''#!/usr/bin/env bash
set -euo pipefail
# LiteRT v2.2.0 JIT asset SHA-256 published by the official GitHub release API:
# d6d160104f110e690f1c9b0c54ab3855af1a12e405dcc6e644dd7e3e1955aac4
# QAIRT is a separate ~2.35 GB archive. It requires a digest independently
# sourced from Qualcomm; never execute a helper script from a downloaded archive.
if [[ "$(printenv ROVEX_ENABLE_QUALCOMM_RUNTIME || true)" != "true" ]]; then
  echo "SKIP: Qualcomm runtime acquisition is opt-in (ROVEX_ENABLE_QUALCOMM_RUNTIME=true)."
  exit 0
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(printenv LITERT_VERSION || echo 2.2.0)"
[[ "$VERSION" == "2.2.0" ]] || { echo "ERROR: no pinned LiteRT digest for $VERSION" >&2; exit 1; }
LITERT_SHA="d6d160104f110e690f1c9b0c54ab3855af1a12e405dcc6e644dd7e3e1955aac4"
QAIRT_SHA="$(printenv ROVEX_QAIRT_ARCHIVE_SHA256 || true)"
[[ "$QAIRT_SHA" =~ ^[0-9a-fA-F]{64}$ ]] || { echo "ERROR: set independently trusted ROVEX_QAIRT_ARCHIVE_SHA256" >&2; exit 1; }
WORK="$ROOT/build/litert_npu_runtime"; mkdir -p "$WORK"
LITERT="$WORK/litert_npu_runtime_libraries_jit.zip"; QAIRT="$WORK/qairt_sdk.zip"
download() { local tmp="$2.download"; rm -f "$tmp"; curl --fail --location --retry 3 --retry-delay 2 --output "$tmp" "$1"; test -s "$tmp"; mv -f "$tmp" "$2"; }
verify() {
  local actual expected
  actual="$(sha256sum "$1" | awk '{print tolower($1)}')"; expected="$(printf '%s' "$2" | tr '[:upper:]' '[:lower:]')"
  [[ "$actual" == "$expected" ]] || { echo "ERROR: SHA-256 mismatch for $3: expected=$expected actual=$actual" >&2; rm -f "$1"; exit 1; }
  echo "Verified $3 SHA-256: $actual"
}
safe_zip() {
  python3 - "$1" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    for i in z.infolist():
        p=i.filename.replace("\\","/")
        if p.startswith("/") or any(x==".." for x in p.split("/")): raise SystemExit("Unsafe archive path: "+i.filename)
PY
}
if [[ ! -s "$LITERT" ]]; then download "https://github.com/google-ai-edge/LiteRT/releases/download/v2.2.0/litert_npu_runtime_libraries_jit.zip" "$LITERT"; fi
verify "$LITERT" "$LITERT_SHA" "official LiteRT v2.2.0 JIT asset"; safe_zip "$LITERT"; unzip -tq "$LITERT" >/dev/null
LOCAL_QAIRT="$(printenv ROVEX_QAIRT_ARCHIVE || true)"
if [[ -n "$LOCAL_QAIRT" ]]; then test -s "$LOCAL_QAIRT"; cp -f "$LOCAL_QAIRT" "$QAIRT"
elif [[ ! -s "$QAIRT" ]]; then download "https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.47.0.260601/v2.47.0.260601.zip" "$QAIRT"; fi
verify "$QAIRT" "$QAIRT_SHA" "Qualcomm QAIRT SDK"; safe_zip "$QAIRT"; unzip -tq "$QAIRT" >/dev/null
rm -rf "$WORK/unpacked"; mkdir -p "$WORK/unpacked" "$ROOT/app/src/main/jniLibs/arm64-v8a"
unzip -q "$LITERT" -d "$WORK/unpacked"; unzip -q "$QAIRT" -d "$WORK/unpacked"
SRC="$WORK/unpacked/qualcomm_runtime_v75/src/main/jni/arm64-v8a"
SDK="$WORK/unpacked/qairt/2.47.0.260601"; DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
[[ -d "$SRC" && -d "$SDK/lib/aarch64-android" ]] || { echo "ERROR: verified archives lack expected runtime layout" >&2; exit 1; }
find "$SRC" -maxdepth 1 -type f -name '*.so' -exec cp -f {} "$DEST/" \;
for name in libQnnHtp.so libQnnSystem.so libQnnHtpPrepare.so libQnnIr.so libQnnSaver.so libQnnHtpV75Stub.so; do
  [[ -s "$SDK/lib/aarch64-android/$name" ]] || { echo "ERROR: verified QAIRT SDK missing $name" >&2; exit 1; }
  cp -f "$SDK/lib/aarch64-android/$name" "$DEST/$name"
done
SKEL="$SDK/lib/hexagon-v75/unsigned/libQnnHtpV75Skel.so"
[[ -s "$SKEL" ]] || { echo "ERROR: verified QAIRT SDK missing v75 skel" >&2; exit 1; }
cp -f "$SKEL" "$DEST/libQnnHtpV75Skel.so"
for name in libLiteRtDispatch_Qualcomm.so libLiteRtCompilerPlugin_Qualcomm.so libQnnHtp.so libQnnSystem.so libQnnHtpPrepare.so libQnnIr.so libQnnSaver.so libQnnHtpV75Stub.so libQnnHtpV75Skel.so; do
  [[ -s "$DEST/$name" ]] || { echo "ERROR: missing required runtime library $name" >&2; exit 1; }
done
echo "Prepared verified LiteRT $VERSION Qualcomm v75 runtime"; sha256sum "$DEST"/*.so
'''
(root / "tools/prepare_litert_qualcomm_v75_jit.sh").write_text(script)
(root / "tools/prepare_litert_qualcomm_v75_jit.sh").chmod(0o755)
test = r'''#!/usr/bin/env bash
set -euo pipefail
grep -Fq 'd6d160104f110e690f1c9b0c54ab3855af1a12e405dcc6e644dd7e3e1955aac4' tools/prepare_litert_qualcomm_v75_jit.sh
grep -Fq 'ROVEX_QAIRT_ARCHIVE_SHA256' tools/prepare_litert_qualcomm_v75_jit.sh
grep -Fq 'safe_zip' tools/prepare_litert_qualcomm_v75_jit.sh
grep -Fq 'ROVEX_ENABLE_QUALCOMM_RUNTIME' app/build.gradle.kts
if grep -Fq './fetch_qualcomm_library.sh' tools/prepare_litert_qualcomm_v75_jit.sh; then echo 'FAIL: archive helper execution'; exit 1; fi
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
ROVEX_ENABLE_QUALCOMM_RUNTIME=false bash tools/prepare_litert_qualcomm_v75_jit.sh >"$tmp/out"
grep -Fq 'SKIP: Qualcomm runtime acquisition is opt-in' "$tmp/out"
echo 'PASS: optional Qualcomm acquisition, pinned LiteRT digest, explicit QAIRT digest, no archive helper execution.'
'''
(root / "tools/tests").mkdir(parents=True, exist_ok=True)
(root / "tools/tests/qualcomm_runtime_supply_chain_test.sh").write_text(test)
(root / "tools/tests/qualcomm_runtime_supply_chain_test.sh").chmod(0o755)
(root / "PHASE0_SECURITY_AUDIT_v8.3.713.md").write_text("""# Phase 0 security overlay — v8.3.713 (versionCode 799)

Base: v8.3.711 source archive (versionCode 797).

- Qualcomm runtime acquisition is opt-in via ROVEX_ENABLE_QUALCOMM_RUNTIME=true.
- LiteRT v2.2.0 JIT asset SHA-256 is pinned to the digest published in the official GitHub release API: d6d160104f110e690f1c9b0c54ab3855af1a12e405dcc6e644dd7e3e1955aac4.
- The separate Qualcomm QAIRT archive requires an independently trusted 64-hex SHA-256 supplied through ROVEX_QAIRT_ARCHIVE_SHA256. No hash is invented or trusted from the same download response.
- Both archives are integrity-tested and checked for unsafe paths before extraction. No archive-supplied helper script is executed.
- Default builds do not fetch the ~2.35 GB QAIRT SDK. Qualcomm DISPATCH_OP models remain unavailable/fail-closed without matching runtime; deterministic Ben/Ren/QBank paths remain authoritative. Portable CPU/GPU inference applies only to compatible non-Qualcomm graphs.
- R8 minification/resource shrinking remain disabled pending the release runtime matrix.
- Existing WebView source contracts remain: no JavaScript bridge, navigation blocked, file-origin cross-access disabled, universal file-origin access disabled, mixed content blocked, cookies disabled, Safe Browsing enabled on supported Android versions.

This file is not a PASS. Firebase emulator tests, Android CI, APK signature/native payload checks, and hostile-WebView runtime tests must execute and pass before Phase 0 can close.
""")
print("Applied Phase 0 overlay: v8.3.713 / versionCode 799")
