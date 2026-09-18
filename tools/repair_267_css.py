from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else '.')
def one(n):
 h=[p for p in root.rglob(n) if 'app/src/main' in p.as_posix()]
 if len(h)!=1: raise SystemExit('ERROR: expected one '+n+', found '+str(len(h)))
 return h[0]
r=one('RenActivity.kt'); s=r.read_text()
if 'import android.app.AlertDialog' not in s:
 lines=s.splitlines(True); i=next(i for i,x in enumerate(lines) if x.startswith('package ')); lines.insert(i+1,'import android.app.AlertDialog\n'); s=''.join(lines)
a=s.index('    private fun cleanAiAnswer'); b=s.index('\n    private fun currentPromptForContext()',a); s=s[:a]+'    private fun cleanAiAnswer(raw: String): String = BenResponsePolicy.normalize(raw).orEmpty().trim()'+s[b:]; r.write_text(s)
p=one('BenResponsePolicy.kt'); p.write_text('''package com.localqbank.library
object BenResponsePolicy {
 const val MAX_RESPONSE_CHARS = 16_384
 fun normalize(raw: String?): String? {
  val value=raw?.replace("\\r\\n","\\n")?.replace("\\r","\\n") ?: return null
  var cleaned=value.replace(Regex("(?is)<style[^>]*>.*?</style>"),"").replace(Regex("(?is)<script[^>]*>.*?</script>"),"").replace(Regex("(?is)<iframe[^>]*>.*?</iframe>"),"").replace(Regex("(?is)<object[^>]*>.*?</object>"),"").replace(Regex("(?is)<embed[^>]*>.*?</embed>"),"")
  val decl=Regex("(?i)\\\\b(?:font-family|font-size|line-height|margin(?:-[a-z]+)?|padding(?:-[a-z]+)?|color|background(?:-[a-z]+)?|border(?:-[a-z]+)?|border-radius|display|width|height|overflow|text-align|vertical-align|letter-spacing|font-weight|text-transform|object-fit)\\\\s*:")
  val rule=Regex("(?s)(?:^|(?<=}))\\\\s*([^{}]{1,160})\\\\{([^{}]{1,3000})\\\\}")
  val sel=Regex("(?is)(?:[a-z][a-z0-9_-]*|[.#][a-z][a-z0-9_-]*)(?:\\\\s*(?:,|>|\\\\+|~)\\\\s*(?:[a-z][a-z0-9_-]*|[.#][a-z][a-z0-9_-]*))*")
  repeat(4){ cleaned=rule.replace(cleaned){m->if(sel.matches(m.groupValues[1].trim())&&decl.containsMatchIn(m.groupValues[2])) "" else m.value} }
  return BoundedTextPolicy.normalize(cleaned,MAX_RESPONSE_CHARS)
 }
}
''',encoding='utf-8')
m=one('RovexMarkdown.kt'); ms=m.read_text(); ms=ms.replace('fun rovexMarkdownToHtml(raw: String): String {\n    var s = raw.replace("\\r\\n", "\\n").replace("\\r", "\\n")','fun rovexMarkdownToHtml(raw: String): String {\n    var s = BenResponsePolicy.normalize(raw).orEmpty().replace("\\r\\n", "\\n").replace("\\r", "\\n")'); m.write_text(ms)
g=root/'app/build.gradle.kts'; g.write_text(g.read_text().replace('versionCode = 360','versionCode = 361').replace('versionName = "8.3.266"','versionName = "8.3.267"'))
t=root/'app/src/test/java/com/localqbank/library/BenResponsePolicyTest.kt'; t.parent.mkdir(parents=True,exist_ok=True); t.write_text('''package com.localqbank.library
import org.junit.Assert.*
import org.junit.Test
class BenResponsePolicyTest {
 @Test fun stripsRendererCss(){val x=BenResponsePolicy.normalize("body{font-size:16px;color:red}h1{margin:8px 0}.card{padding:12px}Answer: A").orEmpty();assertFalse(x.contains("body{"));assertFalse(x.contains("h1{"));assertFalse(x.contains(".card{"));assertTrue(x.contains("Answer: A"))}
 @Test fun stripsStyleScript(){val x=BenResponsePolicy.normalize("<style>body{color:red}</style><script>x()</script>Correct answer: beta-blocker").orEmpty();assertFalse(x.contains("<style"));assertFalse(x.contains("<script"));assertTrue(x.contains("Correct answer: beta-blocker"))}
}
''',encoding='utf-8')
