from pathlib import Path
import sys
p=Path(sys.argv[1]) / ".github/ci/rovex_629_runtime_repair2.py"
s=p.read_text()
s=s.replace("anchor='''    override fun onResume() {","anchor='''override fun onResume() {")
p.write_text(s)
print("overlay anchor normalized")
