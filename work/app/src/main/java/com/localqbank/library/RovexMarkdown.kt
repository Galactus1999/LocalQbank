package com.localqbank.library

import android.text.Html
import android.text.Spanned

/** Dependency-free presentation helpers. WebView is used only for rich, local chat layout. */
fun rovexMarkdownToSpanned(raw: String): Spanned {
    return Html.fromHtml(rovexMarkdownToHtml(raw), Html.FROM_HTML_MODE_LEGACY)
}

fun rovexMarkdownToHtml(raw: String): String {
    var s = raw.replace("\r\n", "\n").replace("\r", "\n")
        .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
        .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
        .replace(Regex("(?is)<iframe[^>]*>.*?</iframe>"), "")
        .replace(Regex("(?is)<object[^>]*>.*?</object>"), "")
        .replace(Regex("(?is)<embed[^>]*>.*?</embed>"), "")
        .replace(Regex("(?i)on[a-z]+\\s*=\\s*(['\"]).*?\\1"), "")

    s = android.text.TextUtils.htmlEncode(s)
    val codeBlocks = ArrayList<String>()
    s = s.replace(Regex("(?s)```(?:[a-zA-Z0-9_+.-]+)?\\n(.*?)```")) { m ->
        codeBlocks += m.groupValues[1]
        "<pre>${m.groupValues[1]}</pre>"
    }
    s = s.replace(Regex("(?m)^######\\s+(.+)$"), "<h6>$1</h6>")
        .replace(Regex("(?m)^#####\\s+(.+)$"), "<h5>$1</h5>")
        .replace(Regex("(?m)^####\\s+(.+)$"), "<h4>$1</h4>")
        .replace(Regex("(?m)^###\\s+(.+)$"), "<h3>$1</h3>")
        .replace(Regex("(?m)^##\\s+(.+)$"), "<h2>$1</h2>")
        .replace(Regex("(?m)^#\\s+(.+)$"), "<h1>$1</h1>")
        .replace(Regex("(?m)^>\\s?(.+)$"), "<blockquote>$1</blockquote>")
        .replace(Regex("(?m)^[-*+]\\s+"), "• ")
        .replace(Regex("(?m)^(\\d+)[.)]\\s+"), "$1. ")
        .replace(Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL), "<b>$1</b>")
        .replace(Regex("__(.+?)__", RegexOption.DOT_MATCHES_ALL), "<b>$1</b>")
        .replace(Regex("~~(.+?)~~", RegexOption.DOT_MATCHES_ALL), "<del>$1</del>")
        .replace(Regex("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)"), "<i>$1</i>")
        .replace(Regex("`([^`\\n]+?)`"), "<code>$1</code>")
        .replace(Regex("\\[([^\\]]+)]\\((https://[^\\s)]+)\\)"), "<a href=\"$2\">$1</a>")

    // Markdown tables: render them as readable HTML tables rather than a wall of pipes.
    val lines = s.split("\n").toMutableList()
    val out = StringBuilder()
    var i = 0
    while (i < lines.size) {
        if (i + 1 < lines.size && lines[i].contains('|') && lines[i + 1].matches(Regex("\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)+\\|?\\s*"))) {
            val header = lines[i].trim().trim('|').split('|').map { it.trim() }
            i += 2
            out.append("<table><tr>"); header.forEach { out.append("<th>").append(it).append("</th>") }; out.append("</tr>")
            while (i < lines.size && lines[i].contains('|')) {
                val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                out.append("<tr>"); cells.forEach { out.append("<td>").append(it).append("</td>") }; out.append("</tr>"); i++
            }
            out.append("</table>")
        } else { out.append(lines[i]).append('\n'); i++ }
    }
    s = out.toString().replace(Regex("\\n{2,}"), "</p><p>").replace("\n", "<br>")
    return """<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>
<style>body{font-family:sans-serif;font-size:16px;line-height:1.52;margin:0;padding:2px;color:#EAF1F7}h1{font-size:25px;margin:8px 0}h2{font-size:21px;margin:16px 0 7px}h3{font-size:18px;margin:14px 0 6px}h4{font-size:16px;margin:12px 0 5px}p{margin:8px 0}blockquote{margin:10px 0;padding:9px 12px;border-left:4px solid #61B7FF;background:#182936;border-radius:8px}code,pre{background:#17232D;border-radius:8px;padding:2px 5px}pre{padding:10px;overflow:auto}table{width:100%;border-collapse:collapse;margin:12px 0;background:#13212B;border-radius:10px;overflow:hidden}th,td{border:1px solid #334A5A;padding:8px;text-align:left;vertical-align:top}th{background:#1C3445;color:#A9DCFF}mark{background:#6B5520;color:#FFF2B0;padding:1px 4px;border-radius:4px}.card{background:#13212B;border:1px solid #2C4352;border-radius:14px;padding:12px;margin:10px 0}.label{font-size:12px;color:#8FCBFF;text-transform:uppercase;letter-spacing:.08em;font-weight:700}.answer{font-size:19px;font-weight:700;color:#62D7A1}img{max-width:100%;height:auto;border-radius:10px;margin:8px 0}a{color:#7FCBFF}</style></head><body><p>$s</p></body></html>"""
}
