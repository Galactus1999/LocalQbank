#!/usr/bin/env python3
"""Measure actual rendered Rovex screenshots. No source-code proxies are used."""
from __future__ import annotations
import argparse, json, subprocess
from pathlib import Path

def run(*args: str) -> str:
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT).strip()

def metric(path: Path):
    info = run("identify", "-format", "%w %h", str(path)).split()
    width, height = int(info[0]), int(info[1])
    mean = float(run("convert", str(path), "-colorspace", "Gray", "-format", "%[fx:mean]", "info:"))
    entropy = float(run("convert", str(path), "-colorspace", "Gray", "-format", "%[entropy]", "info:"))
    edge = float(run("convert", str(path), "-colorspace", "Gray", "-edge", "1", "-threshold", "12%", "-format", "%[fx:mean]", "info:"))
    bright = float(run("convert", str(path), "-colorspace", "Gray", "-threshold", "50%", "-format", "%[fx:mean]", "info:"))
    return {"width": width, "height": height, "mean_luminance": round(mean, 5), "entropy": round(entropy, 5), "edge_density": round(edge, 5), "bright_pixel_ratio": round(bright, 5)}

def score(m):
    score = 0.0
    score += 25.0 if m["width"] >= 700 and m["height"] >= 1100 else 0.0
    score += 25.0 if m["entropy"] >= 0.35 else 0.0
    score += 25.0 if 0.005 <= m["edge_density"] <= 0.35 else 0.0
    score += 25.0 if 0.02 <= m["bright_pixel_ratio"] <= 0.98 else 0.0
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
        m["structural_render_score"] = score(m)
        screens.append(m)
    if not screens:
        raise SystemExit("No screenshots captured")
    report = {
        "schema": "rovex-visual-truth/v1",
        "basis": "actual rendered emulator screenshots",
        "screens": screens,
        "interpretation": "Structural score validates that a real, non-empty UI rendered. It is not a visual similarity score and cannot establish design-match without approved golden references."
    }
    Path(a.output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    required = [s for s in screens if s["file"] != "06_visual_lab.png"]
    if any(s["structural_render_score"] < 75 for s in required):
        raise SystemExit("Visual truth gate failed: one or more required screens have an unhealthy rendered fingerprint")

if __name__ == "__main__":
    main()
