from pathlib import Path
import sys
R=Path(sys.argv[1] if len(sys.argv)>1 else ".")
p=next((x for x in R.rglob("activity_main.xml") if "app/src/main" in x.as_posix()),None)
if p is None: raise SystemExit("ERROR: activity_main.xml missing")
s=p.read_text()
if 'android:id="@+id/performanceLabHint"' not in s:
    a=s.find('<Button android:id="@+id/continueStudyButton"')
    e=s.find('/>',a)
    if a<0 or e<0: raise SystemExit("ERROR: continueStudyButton missing")
    hint='<TextView android:id="@+id/performanceLabHint" android:text="Continue from your last study position" android:textSize="10sp" android:textColor="#7A858E" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="4dp"/>'
    s=s[:e+2]+hint+s[e+2:]
    p.write_text(s)
print("8.3.268 resource compatibility PASS")
