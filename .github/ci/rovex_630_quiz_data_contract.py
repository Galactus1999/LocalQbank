#!/usr/bin/env python3
from pathlib import Path
import sys

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_630_quiz_data_contract.py <project>")
    root=Path(sys.argv[1]).resolve()
    p=root/"app/build.gradle.kts"
    s=p.read_text()
    if 'versionName = "8.3.629"' not in s or 'versionCode = 715' not in s:
        raise SystemExit("v8.3.630 requires v8.3.629/715")
    # v8.3.629 keeps the single QuizSessionRepository.questionAt() contract and changes
    # its implementation to ordinal lookup. Do not introduce parallel data-access APIs.
    p.write_text(s.replace('versionName = "8.3.629"','versionName = "8.3.630"',1)
                   .replace('versionCode = 715','versionCode = 716',1))
    print("v8.3.630 quiz data contract repair: APPLIED")
if __name__=="__main__": main()
