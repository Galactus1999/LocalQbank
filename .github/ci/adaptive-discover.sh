#!/usr/bin/env bash
set -euo pipefail
ROOT="$GITHUB_WORKSPACE"
WORK="$RUNNER_TEMP/adaptive-android"
rm -rf "$WORK" "$ROOT/.adaptive-source"
mkdir -p "$WORK/source" "$WORK/reports"
log() { printf '[adaptive] %s\n' "$*"; }
fail() { log "FATAL: $*"; exit 1; }
candidate="$SOURCE_FILE"
if [[ -n "$candidate" && -f "$ROOT/$candidate" ]]; then candidate="$ROOT/$candidate"; elif [[ -n "$candidate" && ! -f "$candidate" ]]; then fail "Requested SOURCE_FILE does not exist: $candidate"; fi
if [[ -z "$candidate" && -f "$ROOT/phase0-selected-source.txt" ]]; then
  p="$(head -n1 "$ROOT/phase0-selected-source.txt" | tr -d '\r')"
  if [[ -f "$p" ]]; then
    candidate="$p"
  elif [[ -f "$ROOT/$p" ]]; then
    candidate="$ROOT/$p"
  fi
fi
if [[ -z "$candidate" ]]; then candidate="$(python3 - "$ROOT" <<'PY'
import os,re,sys,zipfile
root=sys.argv[1]; items=[]
for dp,dn,fn in os.walk(root):
    dn[:] = [d for d in dn if d != '.git']
    for n in fn:
        if n.lower().endswith('.zip'):
            p=os.path.join(dp,n); code=-1
            try:
                with zipfile.ZipFile(p) as z:
                    for name in z.namelist():
                        if not name.endswith(('build.gradle','build.gradle.kts')): continue
                        try: text=z.read(name).decode('utf-8','ignore')
                        except Exception: continue
                        for m in re.finditer(r'\bversionCode\s*(?:=|\s)\s*(\d+)',text): code=max(code,int(m.group(1)))
            except Exception: pass
            items.append((code,os.path.getmtime(p),p))
if items:
    # Prefer an explicit compile-repair archive over the original move archive when
# multiple archives expose the same highest versionCode. This prevents CI from
# silently rebuilding a known-broken move archive after a compile repair.
items.sort(key=lambda x: (x[0], x[1], 1 if "CompileRepair_Source.zip" in os.path.basename(x[2]) else 0, x[2]), reverse=True)
print(items[0][2])
PY
)"; fi
project="$ROOT"
if [[ -n "$candidate" && "$candidate" == *.zip ]]; then
  log "Selected source archive: $candidate"
  sha256sum "$candidate" | tee "$WORK/reports/source.sha256"
  unzip -q "$candidate" -d "$WORK/source/unpacked"
  project=""
  while IFS= read -r settings_file; do
    root="$(dirname "$settings_file")"; found=false
    while IFS= read -r build_file; do
      if grep -Eq 'com\.android\.application|com\.android\.application\.kotlin|com\.android\.library|com\.android\.library\.kotlin' "$build_file"; then found=true; break; fi
    done < <(find "$root" -maxdepth 2 -type f \( -name build.gradle -o -name build.gradle.kts \) -print)
    if [[ "$found" == true ]]; then project="$root"; break; fi
  done < <(find "$WORK/source/unpacked" -type f \( -name settings.gradle -o -name settings.gradle.kts \) -print)
  [[ -n "$project" ]] || fail "ZIP contains Gradle settings but no Android application/library module"
  # v8.3.446 is an auditable CI overlay over the repository-stored v8.3.445
  # source archive. It is intentionally version-specific and is applied only to
  # that exact baseline; future source archives must carry their own repairs.
  if [[ "$(basename "$candidate")" == "Rovex_v8.3.445_CI_RuntimeRegression_RootRepair_Source.zip" ]]; then
    python3 "$ROOT/.github/ci/rovex_446_overlay2.py" "$project"
  fi
fi
[[ -d "$project" ]] || fail "Resolved project directory does not exist: $project"
[[ -f "$project/gradlew" ]] || fail "Gradle wrapper not found: $project/gradlew"
[[ -f "$project/settings.gradle" || -f "$project/settings.gradle.kts" ]] || fail "Gradle settings not found: $project"
app_module=""; library_module=""
while IFS= read -r f; do
  if grep -Eq 'com\.android\.application|com\.android\.application\.kotlin' "$f"; then app_module="$(dirname "$f")"; break; fi
  if [[ -z "$library_module" ]] && grep -Eq 'com\.android\.library|com\.android\.library\.kotlin' "$f"; then library_module="$(dirname "$f")"; fi
done < <(find "$project" -type f \( -name build.gradle -o -name build.gradle.kts \) ! -path '*/build/*' -print)
module="$app_module"; [[ -n "$module" ]] || module="$library_module"; [[ -n "$module" ]] || fail "No Android Gradle application/library module discovered"
if [[ "$module" == "$project" ]]; then module_path=":"; else rel="$(realpath --relative-to="$project" "$module")"; module_path=":$(printf '%s' "$rel" | tr '/' ':')"; fi
is_application=false
[[ -n "$app_module" ]] && is_application=true
{
  printf 'ADAPTIVE_PROJECT=%s\n' "$project"
  printf 'ADAPTIVE_MODULE=%s\n' "$module"
  printf 'ADAPTIVE_MODULE_PATH=%s\n' "$module_path"
  printf 'ADAPTIVE_IS_APPLICATION=%s\n' "$is_application"
} >> "$GITHUB_ENV"
{
  printf 'project=%s\n' "$project"
  printf 'module=%s\n' "$module"
  printf 'module_path=%s\n' "$module_path"
  printf 'is_application=%s\n' "$is_application"
} >> "$GITHUB_OUTPUT"
mkdir -p "$ROOT/.adaptive-source"; cp -a "$project/." "$ROOT/.adaptive-source/"
log "Project: $project"; log "Android module: $module ($module_path)"
