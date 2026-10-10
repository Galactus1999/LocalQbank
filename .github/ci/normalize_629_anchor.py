from pathlib import Path
p=Path(__file__).resolve().with_name("rovex_629_runtime_repair2.py")
if not p.is_file():
    raise SystemExit("normalize_629_anchor: runtime repair overlay missing beside helper")
s=p.read_text()
s=s.replace("anchor='''    override fun onResume() {","anchor='''override fun onResume() {")
p.write_text(s)
print("overlay anchor normalized")
