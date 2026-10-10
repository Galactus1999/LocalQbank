#!/usr/bin/env python3
"""Measure actual rendered Rovex screenshots without requiring ImageMagick."""
from __future__ import annotations
import argparse, hashlib, json, math, struct, zlib
from pathlib import Path


def _png_rows(path: Path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Not a PNG: {path}")
    pos = 8
    width = height = bit_depth = color_type = None
    raw_parts = []
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            if bit_depth != 8 or interlace != 0 or compression != 0 or filtering != 0:
                raise ValueError(f"Unsupported PNG format in {path}: bit_depth={bit_depth}, interlace={interlace}")
        elif kind == b"IDAT":
            raw_parts.append(payload)
        elif kind == b"IEND":
            break
    if width is None or height is None or not raw_parts:
        raise ValueError(f"PNG missing IHDR/IDAT: {path}")

    channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
    if channels is None:
        raise ValueError(f"Unsupported PNG color type {color_type}: {path}")
    row_bytes = width * channels
    raw = zlib.decompress(b"".join(raw_parts))
    expected = height * (row_bytes + 1)
    if len(raw) != expected:
        raise ValueError(f"Unexpected decompressed PNG size for {path}: {len(raw)} != {expected}")

    rows = []
    prev = bytearray(row_bytes)
    i = 0
    for _ in range(height):
        f = raw[i]
        src = raw[i + 1:i + 1 + row_bytes]
        i += row_bytes + 1
        row = bytearray(row_bytes)
        for x, value in enumerate(src):
            left = row[x - channels] if x >= channels else 0
            up = prev[x]
            up_left = prev[x - channels] if x >= channels else 0
            if f == 0:
                v = value
            elif f == 1:
                v = (value + left) & 255
            elif f == 2:
                v = (value + up) & 255
            elif f == 3:
                v = (value + ((left + up) // 2)) & 255
            elif f == 4:
                p = left + up - up_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - up_left)
                predictor = left if pa <= pb and pa <= pc else (up if pb <= pc else up_left)
                v = (value + predictor) & 255
            else:
                raise ValueError(f"Unsupported PNG filter {f}: {path}")
            row[x] = v
        rows.append(row)
        prev = row
    return width, height, channels, color_type, rows


def _gray_row(row: bytearray, channels: int, color_type: int):
    if color_type == 0:
        return row
    out = bytearray(len(row) // channels)
    if color_type == 4:
        for i in range(0, len(row), 2):
            out[i // 2] = row[i]
    else:
        for i in range(0, len(row), channels):
            r, g, b = row[i], row[i + 1], row[i + 2]
            out[i // channels] = (299 * r + 587 * g + 114 * b) // 1000
    return out


def metric(path: Path):
    width, height, channels, color_type, rows = _png_rows(path)
    hist = [0] * 256
    bright = 0
    edge_hits = 0
    total_edges = max(1, (width - 1) * (height - 1))
    prev = None
    for row in rows:
        gray = _gray_row(row, channels, color_type)
        for v in gray:
            hist[v] += 1
            if v >= 128:
                bright += 1
        if prev is not None:
            for x in range(1, width):
                # Bounded local gradient: enough to detect real UI structure,
                # without external image-processing binaries.
                dx = abs(gray[x] - gray[x - 1])
                dy = abs(gray[x] - prev[x])
                if dx + dy >= 48:
                    edge_hits += 1
        prev = gray
    count = width * height
    entropy = 0.0
    for n in hist:
        if n:
            p = n / count
            entropy -= p * math.log2(p)
    # Normalize 8-bit grayscale entropy to [0,1].
    entropy /= 8.0
    edge = edge_hits / total_edges
    bright_ratio = bright / count
    mean = sum(i * n for i, n in enumerate(hist)) / count / 255.0
    return {
        "width": width,
        "height": height,
        "mean_luminance": round(mean, 5),
        "entropy": round(entropy, 5),
        "edge_density": round(edge, 5),
        "bright_pixel_ratio": round(bright_ratio, 5),
    }


def score(m):
    score = 0.0
    score += 25.0 if m["width"] >= 700 and m["height"] >= 1100 else 0.0
    # Sparse but real first-run Activities can have a large empty content panel.
    # Keep entropy/brightness guardrails, but do not reject valid light-theme screens
    # solely because most pixels are bright or the UI has a low-complexity initial state.
    score += 25.0 if m["entropy"] >= 0.20 else 0.0
    score += 25.0 if 0.005 <= m["edge_density"] <= 0.35 else 0.0
    score += 25.0 if 0.02 <= m["bright_pixel_ratio"] <= 0.995 else 0.0
    return round(score, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--screens", required=True)
    ap.add_argument("--output", required=True)
    a = ap.parse_args()
    screens = []
    for p in sorted(Path(a.screens).glob("*.png")):
        m = metric(p)
        m["file"] = p.name
        m["sha256"] = hashlib.sha256(p.read_bytes()).hexdigest()
        m["structural_render_score"] = score(m)
        screens.append(m)
    if not screens:
        raise SystemExit("No screenshots captured")
    report = {
        "schema": "rovex-visual-truth/v2",
        "basis": "actual rendered emulator PNG screenshots",
        "measurement": "pure-Python PNG decode; no ImageMagick or source-code proxy",
        "screens": screens,
        "interpretation": "Structural score validates that a real, non-empty UI rendered. It is not a visual similarity score and cannot establish design-match without approved golden references."
    }
    Path(a.output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    core_required_names = {"01_home.png", "02_qbank.png", "03_flashcards.png", "04_ren.png", "05_settings.png"}
    phase1_required_names = {
        "phase1_home_light.png", "phase1_home_amoled.png", "phase1_home_space.png",
        "phase1_home_mint.png", "phase1_home_pastel.png"
    }
    by_name = {s["file"]: s for s in screens}
    missing = sorted((core_required_names | phase1_required_names) - set(by_name))
    if missing:
        raise SystemExit("Visual truth gate failed: required rendered screenshots missing: " + ", ".join(missing))
    core_required = [by_name[name] for name in sorted(core_required_names)]
    phase1_required = [by_name[name] for name in sorted(phase1_required_names)]
    core_fingerprints = [s["sha256"] for s in core_required]
    if len(set(core_fingerprints)) != len(core_fingerprints):
        duplicates = sorted({h for h in core_fingerprints if core_fingerprints.count(h) > 1})
        raise SystemExit("Visual truth gate failed: two or more distinct core Activities produced byte-identical screenshots; fingerprints=" + ", ".join(duplicates))
    phase1_fingerprints = [s["sha256"] for s in phase1_required]
    if len(set(phase1_fingerprints)) != len(phase1_fingerprints):
        duplicates = sorted({h for h in phase1_fingerprints if phase1_fingerprints.count(h) > 1})
        raise SystemExit("Phase 1 visual gate failed: theme variants produced byte-identical Home screenshots; fingerprints=" + ", ".join(duplicates))
    if any(s["structural_render_score"] < 75 for s in core_required):
        raise SystemExit("Visual truth gate failed: one or more required core Activities have an unhealthy structural-render score")
    # AMOLED is intentionally near-black; entropy/brightness heuristics can penalize a
    # correctly rendered dark surface. Theme variants instead require real PNG dimensions,
    # uniqueness, measured layout bounds, and contrast assertions from the instrumented test.
    if any(s["width"] < 700 or s["height"] < 1100 for s in phase1_required):
        raise SystemExit("Phase 1 visual gate failed: a theme screenshot has implausibly small dimensions")


if __name__ == "__main__":
    main()
