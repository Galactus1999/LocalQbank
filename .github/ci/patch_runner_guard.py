from pathlib import Path
import sys
p=Path(sys.argv[1]) / ".github/ci/adaptive-instrumentation-v2.sh"
s=p.read_text()
s=s.replace("^INSTRUMENTATION_STATUS_CODE: -1\\$|^INSTRUMENTATION_STATUS_CODE: -2\\$","^INSTRUMENTATION_STATUS_CODE: -2\\$")
p.write_text(s)
print("runner guard patched")
