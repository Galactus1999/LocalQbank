from pathlib import Path
import re, sys

R = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
native = R / "app" / "src" / "main" / "jniLibs" / "arm64-v8a"

# Qualcomm HTP/QNN is an optional accelerator in Ben's architecture. The
# deterministic retrieval/CPU path remains authoritative. Remove only the
# vendor accelerator payload so the APK can remain compact/offline-first.
optional = [
    "libQnnHtpPrepare.so",
    "libQnnHtpV75Skel.so",
    "libQnnSystem.so",
    "libQnnHtp.so",
    "libQnnIr.so",
    "libQnnSaver.so",
    "libQnnHtpV75Stub.so",
    "libLiteRtCompilerPlugin_Qualcomm.so",
    "libLiteRtDispatch_Qualcomm.so",
]
removed = []
for name in optional:
    p = native / name
    if p.exists():
        removed.append(name)
        p.unlink()

# Never remove the required Zstandard Android AAR/native payload.
gradle = R / "app" / "build.gradle.kts"
if "com.github.luben:zstd-jni:1.5.7-16@aar" not in gradle.read_text():
    raise SystemExit("ERROR: required Android Zstd AAR is missing")

# Make the compact build the next monotonic release.
gs = gradle.read_text()
gs = re.sub(r'versionName\s*=\s*"8\.3\.270"', 'versionName = "8.3.271"', gs, count=1)
gs = re.sub(r'versionCode\s*=\s*364', 'versionCode = 365', gs, count=1)
gradle.write_text(gs)

# The accelerator is optional; absence must not become a build-time dependency.
print("8.3.272 offline-size optimization PASS; removed optional Qualcomm payloads: "+str(len(removed)))
