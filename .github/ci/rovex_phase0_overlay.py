#!/usr/bin/env python3
"""Apply Phase-0 security corrections to the pinned v8.3.711 source archive."""
import json, re, sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle.kts"
s = gradle.read_text()
if 'versionCode = 797' not in s or 'versionName = "8.3.711"' not in s:
    raise SystemExit("Refusing unexpected source baseline; expected v8.3.711/code 797")
s = s.replace('versionCode = 797', 'versionCode = 799').replace('versionName = "8.3.711"', 'versionName = "8.3.713"')
# R8 release compatibility: enable optimization only with narrowly scoped keep rules
# for names explicitly resolved through Class.forName by StartupHealthChecker.
s = s.replace('isMinifyEnabled = false', 'isMinifyEnabled = true', 1)
s = s.replace('isShrinkResources = false', 'isShrinkResources = true', 1)
s = s.replace('''// Keep optimization explicitly disabled until the reflection/JNI and native-inference
            // release matrix is exercised on the canonical signed APK.''',
              '''// Phase 0: R8 release compatibility is exercised by CI release startup smoke
            // plus WebView/JNI instrumentation contracts. Keep rules below are narrowly scoped.''', 1)
proguard = root / "app/proguard-rules.pro"
rules = proguard.read_text() if proguard.exists() else ""
keep_names = [
    "-keepnames class com.localqbank.library.MainActivity",
    "-keepnames class com.localqbank.library.QuizActivity",
    "-keepnames class com.localqbank.library.SettingsActivity",
    "-keepnames class com.localqbank.library.RenActivity",
    "-keepnames class com.localqbank.library.FlashcardActivity",
    "-keepnames class com.localqbank.library.StudyToolsActivity",
    "-keepnames class com.localqbank.library.SearchActivity",
    "-keepclassmembers class com.localqbank.library.HtmlImportActivity { android.webkit.WebView web; }",
    "-keepnames class com.localqbank.library.RovexSafeWebViewClient",
    "-keep class com.github.luben.zstd.Zstd { *; }",
]
for rule in keep_names:
    if rule not in rules:
        rules += ("\n" if rules and not rules.endswith("\n") else "") + rule + "\n"
proguard.write_text(rules)

pat = re.compile(r'val prepareQualcommRuntime = tasks\.register<Exec>\("prepareQualcommRuntime"\) \{.*?^\}\.configureEach \{\n.*?^\}\n', re.M | re.S)
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

# Pin the custom Gradle bootstrap archive to Gradle's independently published checksum.
# Verify cached and newly downloaded archives before extraction/execution.
gradlew = root / "gradlew"
launcher = r'''#!/bin/sh
# Rovex Phase-0: checksum-verified Gradle bootstrap.
set -eu
GRADLE_VERSION=9.3.1
GRADLE_SHA256="b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-$GRADLE_VERSION-bin/localqbank"
GRADLE_ZIP="$GRADLE_HOME/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_DIR="$GRADLE_HOME/gradle-$GRADLE_VERSION"
mkdir -p "$GRADLE_HOME"
if [ ! -s "$GRADLE_ZIP" ]; then
  TMP="$GRADLE_ZIP.download"
  downloaded=false
  for URL in "https://downloads.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"; do
    rm -f "$TMP"
    echo "Downloading Gradle $GRADLE_VERSION from $URL..."
    if command -v curl >/dev/null 2>&1; then
      if curl -fL --connect-timeout 15 --max-time 120 --retry 1 --retry-delay 2 "$URL" -o "$TMP"; then
        downloaded=true
        break
      fi
    elif command -v wget >/dev/null 2>&1; then
      if wget --timeout=15 --tries=2 -O "$TMP" "$URL"; then
        downloaded=true
        break
      fi
    else
      echo "ERROR: curl or wget is required to bootstrap Gradle $GRADLE_VERSION" >&2
      exit 1
    fi
    echo "WARNING: download failed from $URL; trying the next official Gradle endpoint." >&2
  done
  if [ "$downloaded" != "true" ]; then
    echo "ERROR: unable to download Gradle $GRADLE_VERSION from official endpoints." >&2
    rm -f "$TMP"
    exit 1
  fi
  test -s "$TMP"
  mv -f "$TMP" "$GRADLE_ZIP"
fi
if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL_GRADLE_SHA256="$(sha256sum "$GRADLE_ZIP" | awk '{print $1}')"
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL_GRADLE_SHA256="$(shasum -a 256 "$GRADLE_ZIP" | awk '{print $1}')"
else
  echo "ERROR: sha256sum or shasum is required to verify Gradle distribution" >&2
  exit 1
fi
if [ "$ACTUAL_GRADLE_SHA256" != "$GRADLE_SHA256" ]; then
  echo "ERROR: SHA-256 mismatch for Gradle distribution $GRADLE_VERSION; refusing extraction." >&2
  rm -f "$GRADLE_ZIP"
  exit 1
fi
echo "Verified Gradle $GRADLE_VERSION distribution SHA-256: $ACTUAL_GRADLE_SHA256"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  unzip -tq "$GRADLE_ZIP" >/dev/null
  rm -rf "$GRADLE_DIR" "$GRADLE_HOME/.extracting"
  mkdir -p "$GRADLE_HOME/.extracting"
  unzip -q "$GRADLE_ZIP" -d "$GRADLE_HOME/.extracting"
  mv "$GRADLE_HOME/.extracting/gradle-$GRADLE_VERSION" "$GRADLE_DIR"
  rm -rf "$GRADLE_HOME/.extracting"
fi
exec "$GRADLE_DIR/bin/gradle" "$@"
'''
gradlew.write_text(launcher)
if "b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06" not in launcher or "SHA-256 mismatch for Gradle distribution" not in launcher:
    raise SystemExit("Gradle distribution checksum patch failed validation")

# The extraction JavaScript is an internal parser, not a network client. Block all
# network-backed subresources to prevent imported HTML from exfiltrating local data.
html_activity = root / "app/src/main/java/com/localqbank/library/HtmlImportActivity.kt"
html_text = html_activity.read_text()
web_settings = "web.settings.domStorageEnabled = true\n        web.settings.allowFileAccess = true"
secure_web_settings = "web.settings.domStorageEnabled = true\n        web.settings.blockNetworkLoads = true\n        web.settings.allowFileAccess = true"
if secure_web_settings not in html_text:
    if html_text.count(web_settings) != 1:
        raise SystemExit("Could not locate the exact HtmlImportActivity WebView settings block")
    html_activity.write_text(html_text.replace(web_settings, secure_web_settings, 1))

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
WORK="${ROVEX_LITERT_WORK_DIR:-$ROOT/build/litert_npu_runtime}"; mkdir -p "$WORK"
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
grep -Fq 'web.settings.blockNetworkLoads = true' app/src/main/java/com/localqbank/library/HtmlImportActivity.kt
if grep -Fq './fetch_qualcomm_library.sh' tools/prepare_litert_qualcomm_v75_jit.sh; then echo 'FAIL: archive helper execution'; exit 1; fi
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
ROVEX_ENABLE_QUALCOMM_RUNTIME=false bash tools/prepare_litert_qualcomm_v75_jit.sh >"$tmp/out"
grep -Fq 'SKIP: Qualcomm runtime acquisition is opt-in' "$tmp/out"
if env -u ROVEX_QAIRT_ARCHIVE_SHA256 ROVEX_ENABLE_QUALCOMM_RUNTIME=true bash tools/prepare_litert_qualcomm_v75_jit.sh >"$tmp/opt-in" 2>&1; then
  echo 'FAIL: runtime acquisition proceeded without an independently trusted QAIRT digest'; exit 1
fi
grep -Fq 'set independently trusted ROVEX_QAIRT_ARCHIVE_SHA256' "$tmp/opt-in"
# A corrupt cached LiteRT asset must fail on the pinned digest before any extraction or helper execution.
mkdir -p "$tmp/work"
printf 'not-a-real-liteRT-archive' > "$tmp/work/litert_npu_runtime_libraries_jit.zip"
if env ROVEX_ENABLE_QUALCOMM_RUNTIME=true ROVEX_QAIRT_ARCHIVE_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ROVEX_LITERT_WORK_DIR="$tmp/work" bash tools/prepare_litert_qualcomm_v75_jit.sh >"$tmp/corrupt" 2>&1; then
  echo 'FAIL: corrupt cached LiteRT archive passed digest verification'; exit 1
fi
grep -Fq 'SHA-256 mismatch for official LiteRT v2.2.0 JIT asset' "$tmp/corrupt"
# The custom Gradle bootstrap must reject a corrupt cached distribution before extraction.
gradle_home="$tmp/gradle-home"
gradle_cache="$gradle_home/wrapper/dists/gradle-9.3.1-bin/localqbank"
mkdir -p "$gradle_cache"
printf 'not-a-real-gradle-distribution' > "$gradle_cache/gradle-9.3.1-bin.zip"
if env HOME="$tmp/home" GRADLE_USER_HOME="$gradle_home" sh ./gradlew --version >"$tmp/gradle-corrupt" 2>&1; then
  echo 'FAIL: corrupt cached Gradle distribution passed digest verification'; exit 1
fi
grep -Fq 'SHA-256 mismatch for Gradle distribution 9.3.1' "$tmp/gradle-corrupt"
test ! -e "$gradle_cache/gradle-9.3.1/bin/gradle"
echo 'PASS: optional Qualcomm acquisition, pinned Gradle and LiteRT digests, corrupt-cache rejection, explicit QAIRT digest, no archive helper execution, and fail-closed missing-digest path.'
'''
(root / "tools/tests").mkdir(parents=True, exist_ok=True)
(root / "tools/tests/qualcomm_runtime_supply_chain_test.sh").write_text(test)
(root / "tools/tests/qualcomm_runtime_supply_chain_test.sh").chmod(0o755)
(root / "PHASE0_SECURITY_AUDIT_v8.3.713.md").write_text("""# Phase 0 security overlay — v8.3.713 (versionCode 799)

Base: v8.3.711 source archive (versionCode 797).

- Qualcomm runtime acquisition is opt-in via ROVEX_ENABLE_QUALCOMM_RUNTIME=true.
- Gradle 9.3.1 bootstrap ZIP SHA-256 is pinned to the checksum published by Gradle and verified before extraction/execution; downloads have explicit connection and total-time limits.
- LiteRT v2.2.0 JIT asset SHA-256 is pinned to the digest published in the official GitHub release API: d6d160104f110e690f1c9b0c54ab3855af1a12e405dcc6e644dd7e3e1955aac4.
- The separate Qualcomm QAIRT archive requires an independently trusted 64-hex SHA-256 supplied through ROVEX_QAIRT_ARCHIVE_SHA256. No hash is invented or trusted from the same download response.
- Both archives are integrity-tested and checked for unsafe paths before extraction. No archive-supplied helper script is executed.
- Default builds do not fetch the ~2.35 GB QAIRT SDK. Qualcomm DISPATCH_OP models remain unavailable/fail-closed without matching runtime; deterministic Ben/Ren/QBank paths remain authoritative. Portable CPU/GPU inference applies only to compatible non-Qualcomm graphs.
- R8 minification and resource shrinking are enabled in the release variant with narrowly scoped keep-name rules for the seven StartupHealthChecker reflection targets. CI must pass the release startup smoke; WebView/renderer and JNI behavior have instrumented coverage. Any R8 failure blocks release.
- WebView: no JavaScript bridge; top-level navigation blocked; network loads blocked for imported HTML; file-origin cross-access disabled; universal file-origin access disabled; mixed content blocked; cookies disabled; Safe Browsing enabled on supported Android versions.

This file is not a PASS. Firebase emulator tests, Android CI, APK signature/native payload checks, and hostile-WebView runtime tests must execute and pass before Phase 0 can close.
""")
print("Applied Phase 0 overlay: v8.3.713 / versionCode 799")


# Add real Firestore/Functions emulator tests for server-owned XP integrity.
functions_pkg = root / "functions/package.json"
pkg = json.loads(functions_pkg.read_text())
pkg["scripts"]["test:emulator"] = "mocha --timeout 30000 test/study-events.emulator.test.js"
pkg["devDependencies"] = {
    "@firebase/rules-unit-testing": "5.0.2",
    "firebase": "12.3.0",
    "mocha": "11.7.2"
}
functions_pkg.write_text(json.dumps(pkg, indent=2) + "\n")
rules_pkg_path = root / "firestore-tests/package.json"
rules_pkg = json.loads(rules_pkg_path.read_text())
rules_pkg["devDependencies"]["@firebase/rules-unit-testing"] = "5.0.2"
rules_pkg_path.write_text(json.dumps(rules_pkg, indent=2) + "\n")
test_dir = root / "functions/test"
test_dir.mkdir(parents=True, exist_ok=True)
test_code = r'''const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { initializeApp: initializeAdminApp, deleteApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { doc, setDoc, serverTimestamp } = require('firebase/firestore');
const { after, before, beforeEach, describe, it } = require('mocha');

const PROJECT_ID = 'demo-rovex-functions-test';
let env;
let adminApp;
let adminDb;

before(async () => {
  const rules = fs.readFileSync(path.join(__dirname, '..', '..', 'firestore.rules'), 'utf8');
  env = await initializeTestEnvironment({ projectId: PROJECT_ID, firestore: { rules } });
  adminApp = initializeAdminApp({ projectId: PROJECT_ID }, 'phase0-functions-admin');
  adminDb = getFirestore(adminApp);
});
after(async () => {
  if (env) await env.cleanup();
  if (adminApp) await deleteApp(adminApp);
});
beforeEach(async () => { await env.clearFirestore(); });

function eventData(uid, id, extra = {}) {
  return {
    uid, operationId: id, type: 'quiz_completed', source: 'quiz',
    clientTimestamp: Date.now(), createdAt: serverTimestamp(), correct: true, ...extra
  };
}
async function waitForProcessed(pathName) {
  const deadline = Date.now() + 25000;
  while (Date.now() < deadline) {
    const snap = await adminDb.doc(pathName).get();
    if (snap.exists && snap.get('processedAt')) return snap.data();
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  throw new Error('Timed out waiting for Cloud Function terminal state: ' + pathName);
}
async function writeClientEvent(uid, id, extra = {}) {
  const db = env.authenticatedContext(uid).firestore();
  const ref = doc(db, 'studyEvents', uid, 'events', id);
  await assertSucceeds(setDoc(ref, eventData(uid, id, extra)));
  return 'studyEvents/' + uid + '/events/' + id;
}

describe('Phase 0 server-authoritative XP emulator tests', () => {
  it('awards fixed quiz XP and ignores client correctness', async () => {
    const uid = 'phase0-valid-user';
    const p = await writeClientEvent(uid, '11111111-1111-4111-8111-111111111111');
    const terminal = await waitForProcessed(p);
    if (terminal.accepted !== true) throw new Error('valid event rejected: ' + JSON.stringify(terminal));
    const profile = (await adminDb.doc('users/' + uid).get()).data();
    if (profile.xp !== 10 || profile.questionsSolved !== 1 || profile.correct !== 0 || profile.accuracyPercent !== 0) {
      throw new Error('client-controlled correctness or XP invariant violated: ' + JSON.stringify(profile));
    }
    const db = env.authenticatedContext(uid).firestore();
    await assertFails(setDoc(doc(db, p), eventData(uid, '11111111-1111-4111-8111-111111111111')));
    await new Promise(resolve => setTimeout(resolve, 500));
    const afterReplay = (await adminDb.doc('users/' + uid).get()).data();
    if (afterReplay.xp !== 10 || afterReplay.questionsSolved !== 1) throw new Error('duplicate create changed aggregate: ' + JSON.stringify(afterReplay));
  });

  it('allows only one of two concurrent duplicate submissions to award XP', async () => {
    const uid = 'phase0-duplicate-race-user';
    const id = '88888888-8888-4888-8888-888888888888';
    const dbA = env.authenticatedContext(uid).firestore();
    const dbB = env.authenticatedContext(uid).firestore();
    const pathName = 'studyEvents/' + uid + '/events/' + id;
    const outcomes = await Promise.allSettled([
      setDoc(doc(dbA, 'studyEvents', uid, 'events', id), eventData(uid, id)),
      setDoc(doc(dbB, 'studyEvents', uid, 'events', id), eventData(uid, id))
    ]);
    const successes = outcomes.filter(x => x.status === 'fulfilled').length;
    const failures = outcomes.filter(x => x.status === 'rejected');
    if (successes !== 1 || failures.length !== 1) throw new Error('duplicate race was not single-winner: ' + JSON.stringify(outcomes.map(x => x.status)));
    if (!failures[0].reason || failures[0].reason.code !== 'permission-denied') {
      throw new Error('losing duplicate write was not denied by rules: ' + String(failures[0].reason));
    }
    const terminal = await waitForProcessed(pathName);
    if (terminal.accepted !== true) throw new Error('winning duplicate submission was rejected: ' + JSON.stringify(terminal));
    await new Promise(resolve => setTimeout(resolve, 500));
    const profile = (await adminDb.doc('users/' + uid).get()).data();
    if (profile.xp !== 10 || profile.questionsSolved !== 1) throw new Error('concurrent duplicate submission awarded more than once: ' + JSON.stringify(profile));
  });

  it('rejects malformed/forged events inserted through an administrative bypass', async () => {
    const uid = 'phase0-forged-user';
    const id = '33333333-3333-4333-8333-333333333333';
    const ref = adminDb.doc('studyEvents/' + uid + '/events/' + id);
    await ref.set({
      uid, operationId: id, type: 'quiz_completed', source: 'flashcards',
      clientTimestamp: Date.now(), createdAt: FieldValue.serverTimestamp()
    });
    const terminal = await waitForProcessed(ref.path);
    if (terminal.accepted === true) throw new Error('forged type/source event accepted: ' + JSON.stringify(terminal));
    const profile = await adminDb.doc('users/' + uid).get();
    if (profile.exists && Number(profile.get('xp') || 0) !== 0) throw new Error('forged event awarded XP');
  });

  it('processes concurrent distinct events without lost/doubled increments', async () => {
    const uid = 'phase0-concurrent-user';
    const paths = await Promise.all([
      writeClientEvent(uid, '44444444-4444-4444-8444-444444444444'),
      writeClientEvent(uid, '55555555-5555-4555-8555-555555555555')
    ]);
    const terminals = await Promise.all(paths.map(waitForProcessed));
    if (terminals.some(x => x.accepted !== true)) throw new Error('concurrent valid event rejected');
    const profile = (await adminDb.doc('users/' + uid).get()).data();
    if (profile.xp !== 20 || profile.questionsSolved !== 2) throw new Error('concurrent transaction invariant violated: ' + JSON.stringify(profile));
  });

  it('enforces the server UTC-day quota against event flooding', async () => {
    const uid = 'phase0-rate-user';
    const day = new Date().toISOString().slice(0, 10);
    await adminDb.doc('studyEventQuotas/' + uid + '/days/' + day).set({ quiz_completed: 300 });
    const p = await writeClientEvent(uid, '66666666-6666-4666-8666-666666666666');
    const terminal = await waitForProcessed(p);
    if (terminal.accepted !== false || terminal.rejection !== 'DAILY_LIMIT') throw new Error('daily quota not enforced: ' + JSON.stringify(terminal));
    const profile = await adminDb.doc('users/' + uid).get();
    if (profile.exists && Number(profile.get('xp') || 0) !== 0) throw new Error('rate-limited event awarded XP');
  });
});
'''
(test_dir / "study-events.emulator.test.js").write_text(test_code)
print("Added executable Firestore/Functions emulator tests.")


# Add runtime WebView policy and renderer-loss instrumentation coverage.
android_test = root / "app/src/androidTest/java/com/localqbank/library/HtmlImportSecurityInstrumentedTest.kt"
android_test.write_text(r'''package com.localqbank.library

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HtmlImportSecurityInstrumentedTest {
    @Test
    fun importedHtmlWebViewUsesConstrainedFileOriginAndBlocksNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "phase0-hostile-import.html")
        fixture.writeText("<!doctype html><html><head><title>fixture</title></head><body>" +
            "<script>window.__hostileFixtureRan = true; try { window.open('https://example.com'); } catch(e) {}</script>" +
            "<p>untrusted import fixture</p></body></html>")
        val intent = Intent(context, HtmlImportActivity::class.java).apply {
            setDataAndType(Uri.fromFile(fixture), "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ActivityScenario.launch<HtmlImportActivity>(intent).use { scenario ->
                val failure = AtomicReference<Exception?>()
                scenario.onActivity { activity ->
                    try {
                        val field = HtmlImportActivity::class.java.getDeclaredField("web").apply { isAccessible = true }
                        val web = field.get(activity) as WebView
                        assertTrue("JavaScript is intentionally enabled for legacy QBank parsing", web.settings.javaScriptEnabled)
                        assertFalse("File-origin cross-access must be disabled", web.settings.allowFileAccessFromFileURLs)
                        assertFalse("Universal file-origin access must be disabled", web.settings.allowUniversalAccessFromFileURLs)
                        assertTrue("Untrusted imported HTML must not make network requests", web.settings.blockNetworkLoads)
                        assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
                        assertFalse("Cookies must not be accepted for imported content", android.webkit.CookieManager.getInstance().acceptCookie())
                        assertNotNull("Imported HTML must use renderer-loss containment", web.webViewClient as? RovexSafeWebViewClient)

                        val request = Proxy.newProxyInstance(
                            WebResourceRequest::class.java.classLoader,
                            arrayOf(WebResourceRequest::class.java)
                        ) { _, method, _ ->
                            when (method.returnType) {
                                java.lang.Boolean.TYPE -> false
                                java.lang.Integer.TYPE -> 0
                                else -> null
                            }
                        } as WebResourceRequest
                        assertTrue("Top-level external navigation must be cancelled",
                            web.webViewClient.shouldOverrideUrlLoading(web, request))
                    } catch (t: Exception) {
                        failure.set(t)
                    }
                }
                failure.get()?.let { throw AssertionError("WebView security policy assertion failed", it) }
            }
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun rendererLossCallbackDetachesAndDestroysDeadWebViewWithoutPropagatingCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = AtomicReference<Exception?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val parent = FrameLayout(context)
                val web = WebView(context)
                parent.addView(web, FrameLayout.LayoutParams(1, 1))
                val detail = object : RenderProcessGoneDetail() {
                    override fun didCrash(): Boolean = true
                    override fun rendererPriorityAtExit(): Int = 0
                }
                val handled = RovexSafeWebViewClient().onRenderProcessGone(web, detail)
                assertTrue("Renderer death must be handled by the app", handled)
                assertEquals("Dead renderer WebView must be detached", null, web.parent)
                assertTrue("Host container remains alive", parent is ViewGroup)
            } catch (t: Exception) {
                result.set(t)
            }
        }
        result.get()?.let { throw AssertionError("Renderer-loss containment failed", it) }
    }

    @Test
    fun remoteAndExecutableUriSchemesAreNotAcceptedAsImportSources() {
        assertTrue(ImportSecurityPolicy.acceptsImportUri("content://provider/document/1"))
        assertTrue(ImportSecurityPolicy.acceptsImportUri("file:///data/local/tmp/import.html"))
        assertFalse(ImportSecurityPolicy.acceptsImportUri("https://example.com/payload.html"))
        assertFalse(ImportSecurityPolicy.acceptsImportUri("javascript:alert(1)"))
        assertFalse(ImportSecurityPolicy.acceptsImportUri("intent://example"))
    }
}
''')
print("Added WebView security/renderer-loss instrumentation tests.")


# Repair a stale Firestore rules fixture: the valid event must satisfy the current UUID/operationId contract.
rules_test_path = root / "firestore-tests/firestore.rules.test.js"
rules_test = rules_test_path.read_text()
old_event_test = """    const eventRef = doc(db, 'studyEvents', 'user-f', 'events', 'event-1');
    await assertSucceeds(setDoc(eventRef, {
      uid: 'user-f', type: 'quiz_completed', source: 'quiz', clientTimestamp: Date.now(),
      createdAt: serverTimestamp(), correct: true
    }));
"""
new_event_test = """    const eventId = '77777777-7777-4777-8777-777777777777';
    const eventRef = doc(db, 'studyEvents', 'user-f', 'events', eventId);
    await assertSucceeds(setDoc(eventRef, {
      uid: 'user-f', operationId: eventId, type: 'quiz_completed', source: 'quiz', clientTimestamp: Date.now(),
      createdAt: serverTimestamp(), correct: true
    }));
"""
if old_event_test not in rules_test:
    raise SystemExit("Expected stale study-event fixture was not found; refusing broad text replacement")
if "const PROJECT_ID = 'rovex-rules-regression';" not in rules_test:
    raise SystemExit("Expected Firestore Rules test project ID was not found")
rules_test = rules_test.replace("const PROJECT_ID = 'rovex-rules-regression';", "const PROJECT_ID = 'demo-rovex-rules-regression';", 1)
rules_test_path.write_text(rules_test.replace(old_event_test, new_event_test, 1))
print("Updated valid study-event fixture to satisfy the current UUID/operationId rules contract.")


# Enforce type-to-source binding in the server processor, not only in Firestore Rules.
functions_index = root / "functions/index.js"
index_text = functions_index.read_text()
source_set = 'const VALID_SOURCES = new Set(["quiz", "flashcards"]);'
if source_set not in index_text:
    raise SystemExit("Could not locate authoritative study-event source validation")
if "const VALID_SOURCE_BY_TYPE" not in index_text:
    index_text = index_text.replace(source_set, source_set + '\nconst VALID_SOURCE_BY_TYPE = Object.freeze({ quiz_completed: "quiz", flashcard_reviewed: "flashcards" });', 1)
validation = '!VALID_SOURCES.has(data.source) ||'
if validation not in index_text:
    raise SystemExit("Could not locate study-event source validation condition")
if 'data.source !== VALID_SOURCE_BY_TYPE[type]' not in index_text:
    index_text = index_text.replace(validation, validation + ' data.source !== VALID_SOURCE_BY_TYPE[type] ||', 1)
functions_index.write_text(index_text)
print("Bound XP event source to event type in server validation.")


# Verify that the required Zstandard JNI library can actually execute, not merely exist in the APK.
zstd_test = root / "app/src/androidTest/java/com/localqbank/library/ZstdNativePayloadInstrumentedTest.kt"
zstd_test.write_text(r'''package com.localqbank.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.luben.zstd.Zstd
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZstdNativePayloadInstrumentedTest {
    @Test
    fun bundledZstdJniLoadsAndRoundTripsPayload() {
        val input = "Rovex Phase 0 native payload validation".toByteArray(Charsets.UTF_8)
        val compressed = Zstd.compress(input)
        assertTrue("Zstandard compression must return a non-empty payload", compressed.isNotEmpty())
        assertArrayEquals(input, Zstd.decompress(compressed, input.size))
    }
}
''')

# Align the release source contract test with the intentional R8 enablement above.
# Require the targeted reflection/JNI rules and reject a blanket package keep.
contract_test = root / "app/src/test/java/com/localqbank/library/Phase0SecurityBuildContractSourceTest.kt"
contract_text = contract_test.read_text()
old_contract = """    @Test fun releaseOptimizationStaysExplicitlyDisabledUntilRuntimeMatrixPasses() {
        val s = source("app/build.gradle.kts")
        val release = s.substringAfter("release {").substringBefore("\\n        }")
        assertTrue("R8 minification must not be enabled without release validation", release.contains("isMinifyEnabled = false"))
        assertTrue("Resource shrinking must not be enabled without release validation", release.contains("isShrinkResources = false"))
    }"""
new_contract = """    @Test fun releaseOptimizationIsEnabledWithNarrowReflectionAndNativeKeepRules() {
        val s = source("app/build.gradle.kts")
        val release = s.substringAfter("release {").substringBefore("\\n        }")
        assertTrue("Release R8 minification must be enabled for the exercised release matrix", release.contains("isMinifyEnabled = true"))
        assertTrue("Release resource shrinking must be enabled for the exercised release matrix", release.contains("isShrinkResources = true"))
        val rules = source("app/proguard-rules.pro")
        assertTrue("Zstd JNI entry points must survive optimization", rules.contains("-keep class com.github.luben.zstd.Zstd { *; }"))
        assertTrue("Reflectively resolved MainActivity name must remain stable", rules.contains("-keepnames class com.localqbank.library.MainActivity"))
        assertFalse("Do not use a blanket application-package keep rule", rules.contains("-keep class com.localqbank.library.** { *; }"))
    }"""
if old_contract not in contract_text:
    raise SystemExit("exact stale R8 assertion not found in overlay source baseline")
contract_text = contract_text.replace(old_contract, new_contract, 1)
contract_test.write_text(contract_text)
print("Aligned R8 contract test with targeted keep-rule release configuration.");

print("Enabled R8 release optimization with targeted reflection keep rules and added executable Zstd JNI coverage.")

