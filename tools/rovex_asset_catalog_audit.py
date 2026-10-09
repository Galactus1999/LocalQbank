#!/usr/bin/env python3
"""Validate Rovex's bundled visual asset manifest without modifying the Android project.

Checks provenance, redistribution-license metadata, file existence and SHA-256 integrity.
Lottie JSON receives a small structural sanity check. This is not a renderer or visual-quality
test; screenshot/device tests remain required for visual fidelity and runtime performance.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

MANIFEST = Path("app/src/main/assets/rovex/RovexVisualAssetManifest.json")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
Lottie_REQUIRED = ("v", "fr", "ip", "op", "w", "h", "layers")
KNOWN_REDISTRIBUTABLE = {
    "MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause",
    "CC0-1.0", "CC-BY-4.0", "CC-BY-3.0", "PUBLIC-DOMAIN",
    # Asset license, not an open-source software license; use only with its full
    # terms shipped alongside the bundled animation and never as a standalone asset.
    "LOTTIE SIMPLE LICENSE",
}


def fail(message: str) -> None:
    raise SystemExit(f"[asset-catalog] ERROR: {message}")


def resolve_asset(project: Path, rel: str) -> Path:
    # Manifest paths are relative to app/src/main: assets/... or res/...
    pure = Path(rel)
    if pure.is_absolute() or ".." in pure.parts:
        fail(f"unsafe asset path: {rel}")
    if not (rel.startswith("assets/") or rel.startswith("res/")):
        fail(f"asset path must be relative to app/src/main (assets/... or res/...): {rel}")
    result = (project / "app/src/main" / pure).resolve()
    base = (project / "app/src/main").resolve()
    if result != base and base not in result.parents:
        fail(f"asset path escapes app/src/main: {rel}")
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True, type=Path)
    args = parser.parse_args()
    project = args.project.resolve()
    manifest_path = project / MANIFEST
    if not manifest_path.is_file():
        # Older/non-visual source archives may not ship bundled motion assets.
        print("[asset-catalog] No Rovex visual asset manifest in this source; skipped.")
        return 0

    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"manifest is unreadable/invalid JSON: {exc}")
    if not isinstance(manifest, dict) or not isinstance(manifest.get("assets"), list):
        fail("manifest must be an object with an assets array")
    if not manifest["assets"]:
        fail("asset manifest is empty")

    seen: set[str] = set()
    for index, entry in enumerate(manifest["assets"]):
        if not isinstance(entry, dict):
            fail(f"assets[{index}] must be an object")
        rel = entry.get("file")
        source = entry.get("source")
        license_name = entry.get("license")
        expected_hash = entry.get("sha256")
        if not all(isinstance(x, str) and x.strip() for x in (rel, source, license_name, expected_hash)):
            fail(f"assets[{index}] needs non-empty file, source, license, and sha256 fields")
        if rel in seen:
            fail(f"duplicate manifest path: {rel}")
        seen.add(rel)
        parsed = urlparse(source)
        if parsed.scheme != "https" or not parsed.netloc:
            fail(f"{rel}: source must be an HTTPS URL")
        normalized_license = license_name.strip().upper()
        if normalized_license not in KNOWN_REDISTRIBUTABLE:
            fail(f"{rel}: license '{license_name}' is not on the reviewed redistribution allowlist")
        if normalized_license == "LOTTIE SIMPLE LICENSE":
            terms_path = project / "app/src/main/assets/rovex/motion/THIRD_PARTY_ASSETS.md"
            if not terms_path.is_file():
                fail(f"{rel}: Lottie Simple License terms must ship in THIRD_PARTY_ASSETS.md")
            terms = terms_path.read_text(encoding="utf-8")
            required_terms = (
                "Permission is hereby granted", "same terms and conditions of this license",
                "does not include the right to collect or compile",
                "FILES ARE PROVIDED 'AS IS'",
            )
            missing_terms = [clause for clause in required_terms if clause not in terms]
            if missing_terms:
                fail(f"{rel}: bundled Lottie Simple License terms are incomplete: {', '.join(missing_terms)}")
        if not SHA256.fullmatch(expected_hash.lower()):
            fail(f"{rel}: sha256 must be exactly 64 hexadecimal characters")
        path = resolve_asset(project, rel)
        if not path.is_file():
            fail(f"{rel}: manifest points to a missing file")
        data = path.read_bytes()
        actual_hash = hashlib.sha256(data).hexdigest()
        if actual_hash != expected_hash.lower():
            fail(f"{rel}: SHA-256 mismatch (manifest={expected_hash}, actual={actual_hash})")
        kind = str(entry.get("type", "")).lower()
        if "lottie" in kind or path.suffix.lower() == ".json":
            try:
                doc = json.loads(data.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                fail(f"{rel}: invalid Lottie/JSON asset: {exc}")
            missing = [key for key in Lottie_REQUIRED if key not in doc]
            if missing:
                fail(f"{rel}: missing Lottie structural keys: {', '.join(missing)}")
            if not isinstance(doc["layers"], list) or not doc["layers"]:
                fail(f"{rel}: Lottie layers must be a non-empty array")
            if not all(isinstance(doc[k], (int, float)) for k in ("fr", "ip", "op", "w", "h")):
                fail(f"{rel}: invalid numeric frame/dimension metadata")
            if doc["fr"] <= 0 or doc["op"] <= doc["ip"] or doc["w"] <= 0 or doc["h"] <= 0:
                fail(f"{rel}: invalid frame range or canvas dimensions")
        elif kind == "wallpaper" or path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}:
            valid = (
                data.startswith(b"\xff\xd8\xff")
                or data.startswith(b"\x89PNG\r\n\x1a\n")
                or (len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP")
            )
            if not valid:
                fail(f"{rel}: image signature is not JPEG, PNG, or WebP")
            if len(data) < 16_384:
                fail(f"{rel}: wallpaper/image payload is suspiciously small ({len(data)} bytes)")
        print(f"[asset-catalog] OK {rel} | {license_name} | sha256={actual_hash}")

    print(f"[asset-catalog] PASS: {len(seen)} asset(s) verified; this does not replace device visual/performance tests.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
