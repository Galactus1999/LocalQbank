package com.localqbank.library

import java.util.LinkedHashMap
import java.util.regex.Pattern

/** Pure parsing/normalization layer for HTML QBank imports. No Activity, WebView, DB or UI state. */
object HtmlImportParser {
    fun detectProvider(tests: List<ImportedTest>): String {
        val corpus = tests.asSequence()
            .flatMap { sequenceOf(it.title, it.path ?: "") }
            .joinToString(" ")
            .lowercase()
        return when {
            "marrow" in corpus -> "Marrow"
            "dams" in corpus -> "DAMS"
            "prepladder" in corpus || "prep ladder" in corpus -> "PrepLadder"
            "cerebellum" in corpus -> "Cerebellum"
            "dbmci" in corpus || "dnb" in corpus -> "DBMCI"
            "egurukul" in corpus || "e-gurukul" in corpus -> "eGurukul"
            else -> "Imported"
        }
    }

    fun parseCombinedHtmlNative(html: String, baseUri: String? = null): List<ImportedTest> {
        val marrow = parseMarrowSrcdocs(html, baseUri)
        if (marrow.isNotEmpty()) return marrow

        val out = mutableListOf<ImportedTest>()
        val iframeRe = Pattern.compile("srcdoc\\s*=\\s*([\\\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = iframeRe.matcher(html)
        var sectionNo = 0

        fun parseDocument(doc: String, fallbackTitle: String) {
            val title = Regex("<title\\s*>\\s*(.*?)\\s*</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(doc)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle

            // Only inspect actual data-looking assignments. Avoid walking every
            // assignment in vendor/minified JavaScript; that was the main source of
            // long Marrow imports and partial results.
            val assignment = Regex("(?:(?:const|let|var)\\s+)?(?:window\\.)?([A-Za-z_$][\\w$]*)\\s*=\\s*([\\[\\{])", RegexOption.IGNORE_CASE)
            for (m in assignment.findAll(doc)) {
                val variable = m.groupValues[1]
                if (!isLikelyDataVariable(variable)) continue
                val openPos = doc.indexOf(m.groupValues[2], m.range.first)
                val raw = extractBalancedAny(doc, openPos) ?: continue
                val candidate = try {
                    when (raw.firstOrNull()) {
                        '[' -> org.json.JSONArray(raw)
                        '{' -> org.json.JSONObject(raw)
                        else -> null
                    }
                } catch (_: Exception) { null }
                if (candidate != null) {
                    val parsed = walkStrictJson(candidate, listOf(title))
                    if (parsed.isNotEmpty()) out.addAll(parsed)
                }
            }
        }

        while (matcher.find()) {
            sectionNo++
            val raw = matcher.group(2) ?: continue
            parseDocument(decodeHtmlEntitiesPreserveMarkup(raw), "Section $sectionNo")
        }
        if (out.isEmpty()) parseDocument(html, "Imported Section")
        return dedupeImportedTests(out)
    }

    fun isLikelyDataVariable(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("question") || n.contains("quiz") || n.contains("test") ||
            n.contains("exam") || n.contains("data") || n.contains("items") ||
            n.contains("sections") || n.contains("questions")
    }

    /** Parse Marrow's iframe[srcdoc] format without loading the entire outer HTML into a second representation. */
    fun parseMarrowSrcdocs(html: String, baseUri: String? = null): List<ImportedTest> {
        val out = mutableListOf<ImportedTest>()
        val iframeRe = Pattern.compile("srcdoc\\s*=\\s*([\\\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = iframeRe.matcher(html)
        while (matcher.find()) {
            val raw = matcher.group(2) ?: continue
            // A genuine Marrow child has the questions_json/questions assignment and
            // normally carries the Marrow study-bot marker. Do not force the marker;
            // some edited/offline exports remove it.
            val decoded = decodeHtmlEntitiesPreserveMarkup(raw)
            val title = Regex("<title\\s*>\\s*(.*?)\\s*</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(decoded)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
                ?.takeIf { it.isNotBlank() } ?: "Marrow Section ${out.size + 1}"
            val tests = parseNamedQuestionsAssignments(decoded, title)
            if (tests.isNotEmpty()) out.addAll(tests)
        }
        val resolvedBase = baseUri?.takeIf { it.isNotBlank() }
        return dedupeImportedTests(out).map { test ->
            if (resolvedBase == null) test else test.copy(questions = test.questions.map { q -> resolveQuestionMedia(q, resolvedBase) })
        }
    }

    fun parseNamedQuestionsAssignments(doc: String, title: String): List<ImportedTest> {
        val out = mutableListOf<ImportedTest>()
        val re = Regex("\\bquestions\\s*=\\s*\\[", setOf(RegexOption.IGNORE_CASE))
        for (m in re.findAll(doc)) {
            val start = doc.indexOf('[', m.range.first)
            val raw = extractBalancedAny(doc, start) ?: continue
            if (raw.length <= 2) continue
            try {
                val qa = org.json.JSONArray(raw)
                if (qa.length() == 0) continue
                val wrapper = org.json.JSONArray().put(
                    org.json.JSONObject().put("title", title).put("questions", qa)
                )
                val parsed = parseTests(wrapper)
                if (parsed.isNotEmpty()) {
                    out.addAll(parsed.map { it.copy(title = title) })
                    break
                }
            } catch (_: Exception) {
                // Ignore a malformed assignment and continue with the next section.
            }
        }
        return out
    }

    fun walkStrictJson(value: Any, path: List<String>): List<ImportedTest> {
        val out = mutableListOf<ImportedTest>()
        fun cleanTitle(v: String?) = v?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() } ?: "Imported Section"
        fun isQuestion(o: org.json.JSONObject): Boolean {
            val hasStem = listOf("text","question","stem","question_text","questionText","raw_text").any { o.has(it) && !o.isNull(it) }
            val hasData = listOf("options","choices","answers","option_list","answer","ans","correct_answer","correctAnswer","explanation","solution","expl").any { o.has(it) && !o.isNull(it) }
            return hasStem && hasData
        }
        fun walk(v: Any?, p: List<String>) {
            when(v) {
                is org.json.JSONArray -> {
                    val qs = mutableListOf<org.json.JSONObject>()
                    for(i in 0 until v.length()) { val x=v.opt(i); if(x is org.json.JSONObject && isQuestion(x)) qs.add(x) }
                    if(qs.isNotEmpty()) {
                        val wrapper=org.json.JSONArray().put(org.json.JSONObject().put("title", cleanTitle(p.lastOrNull())).put("questions", org.json.JSONArray().also{a->qs.forEach{a.put(it)}}))
                        out.addAll(parseTests(wrapper))
                    }
                    for(i in 0 until v.length()) walk(v.opt(i),p)
                }
                is org.json.JSONObject -> {
                    val rawTitle=v.optString("title", v.optString("name", v.optString("subject", v.optString("lesson", v.optString("section", v.optString("chapter", ""))))))
                    val next=if(rawTitle.isBlank()) p else p + rawTitle
                    val keys=v.keys()
                    while(keys.hasNext()) { val k=keys.next(); if(k !in setOf("options","choices","answers","option_list","optionList")) walk(v.opt(k),next) }
                }
            }
        }
        walk(value,path)
        return out
    }

    fun extractBalancedAny(s: String, start: Int): String? {
        if (start < 0 || start >= s.length || (s[start] != '[' && s[start] != '{')) return null
        var square = 0
        var curly = 0
        var quote: Char? = null
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (quote != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == quote) quote = null
                continue
            }
            if (c == '"' || c == '\'') { quote = c; continue }
            when (c) {
                '[' -> square++
                ']' -> { square--; if (square == 0 && curly == 0) return s.substring(start, i + 1) }
                '{' -> curly++
                '}' -> { curly--; if (square == 0 && curly == 0) return s.substring(start, i + 1) }
            }
        }
        return null
    }

    fun dedupeImportedTests(input: List<ImportedTest>): List<ImportedTest> {
        val result = mutableListOf<ImportedTest>()
        val seenTest = HashSet<String>()
        for (t in input) {
            val key = "${t.title.trim().lowercase()}|${t.questions.firstOrNull()?.text?.trim()?.lowercase()?.take(240) ?: ""}|${t.questions.size}"
            if (seenTest.add(key)) result.add(t)
        }
        return result
    }

    fun extractBalanced(s: String, start: Int): String? {
        if (start < 0 || start >= s.length || s[start] != '[') return null
        var depth = 0; var quote: Char? = null; var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (quote != null) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == quote) quote = null
                continue
            }
            if (c == '\"' || c == '\'') { quote = c; continue }
            if (c == '[') depth++ else if (c == ']') { depth--; if (depth == 0) return s.substring(start, i + 1) }
        }
        return null
    }

    fun decodeHtmlEntitiesPreserveMarkup(s: String): String = s
    fun resolveQuestionMedia(q: ImportedQuestion, base: String): ImportedQuestion {
        fun rewrite(html: String?): String? {
            if (html.isNullOrBlank()) return html
            var out = html
            val rx = Pattern.compile("(<img\\b[^>]*(?:src|data-src|data-original|data-lazy-src)\\s*=\\s*[\"'])([^\"']+)([\"'])", Pattern.CASE_INSENSITIVE)
            val m = rx.matcher(out)
            val b = StringBuffer()
            while (m.find()) {
                val resolved = resolveMediaUri(m.group(2) ?: "", base)
                m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement((m.group(1) ?: "") + resolved + (m.group(3) ?: "")))
            }
            m.appendTail(b)
            val sr = Pattern.compile("(\\bsrcset\\s*=\\s*[\"'])([^\"']+)([\"'])", Pattern.CASE_INSENSITIVE)
            val sm = sr.matcher(b.toString())
            val sb = StringBuffer()
            while (sm.find()) {
                val resolved = (sm.group(2) ?: "").split(',').map { part ->
                    val bits = part.trim().split(Regex("\\s+"), limit = 2)
                    if (bits.isEmpty()) "" else resolveMediaUri(bits[0], base) + if (bits.size > 1) " " + bits[1] else ""
                }.filter { it.isNotBlank() }.joinToString(", ")
                sm.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement((sm.group(1) ?: "") + resolved + (sm.group(3) ?: "")))
            }
            sm.appendTail(sb)
            return sb.toString()
        }
        return q.copy(
            text = rewrite(q.text) ?: q.text,
            rawText = rewrite(q.rawText),
            explanation = rewrite(q.explanation),
            options = q.options.map { it.copy(text = rewrite(it.text) ?: it.text) },
            questionImages = q.questionImages.map { resolveMediaUri(it, base) }.distinct(),
            explanationImages = q.explanationImages.map { resolveMediaUri(it, base) }.distinct()
        )
    }

    fun resolveMediaUri(raw: String, base: String): String {
        val value = raw.trim()
        if (value.isBlank() || value.startsWith("data:", true) || value.startsWith("blob:", true)) return value
        if (value.startsWith("//")) return "https:$value"
        return try {
            val candidate = java.net.URI(value)
            if (candidate.isAbsolute) return candidate.toString()
            val b = java.net.URI(base)
            // A content:// document URI has no portable filesystem semantics, so URI.resolve()
            // can manufacture a syntactically valid but unreadable child URI. Preserve the
            // relationship and let the WebView fallback resolve it against the original document.
            if (b.scheme.equals("content", true)) {
                val encodedBase = java.net.URLEncoder.encode(base, Charsets.UTF_8.name())
                val encodedRel = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
                return "rovex-rel://image?base=$encodedBase&path=$encodedRel"
            }
            b.resolve(candidate).toString()
        } catch (_: Exception) { value }
    }
    fun parseTests(a: org.json.JSONArray): List<ImportedTest> {
        val out = mutableListOf<ImportedTest>()
        for (i in 0 until a.length()) {
            val t = a.getJSONObject(i)
            val qa = t.optJSONArray("questions") ?: org.json.JSONArray()
            val qs = mutableListOf<ImportedQuestion>()
            for (j in 0 until qa.length()) {
                val q = qa.optJSONObject(j) ?: continue
                val oa = q.optJSONArray("options") ?: q.optJSONArray("choices") ?: q.optJSONArray("answers") ?: org.json.JSONArray()
                val opts = mutableListOf<ImportedOption>()
                for (k in 0 until oa.length()) {
                    val raw = oa.opt(k)
                    if (raw is org.json.JSONObject) {
                        opts.add(ImportedOption(raw.optString("label", raw.optString("key", "${'A'.code + k}")), raw.optString("text", raw.optString("value", raw.optString("option", ""))), raw.optBoolean("correct", false) || raw.optBoolean("is_correct", false)))
                    } else if (raw != null) {
                        opts.add(ImportedOption("${'A'.code + k}", raw.toString(), false))
                    }
                }
                if (opts.size < 2) continue
                val ans = q.optStringOrNull("correct_answer") ?: q.optStringOrNull("correctAnswer") ?: q.optStringOrNull("answer") ?: q.optStringOrNull("ans")
                val normalizedOpts = if (!ans.isNullOrBlank()) opts.map { o ->
                    val a=ans.trim(); val l=o.label.trim(); val hit=a.equals(l,true) || a.startsWith("$l.",true) || a.startsWith("$l)",true)
                    if (hit) o.copy(correct=true) else o
                } else opts
                qs.add(ImportedQuestion(q.optStringOrNull("id") ?: q.optStringOrNull("question_id"), q.optString("text", q.optString("question", q.optString("stem", q.optString("raw_text", "")))), q.optStringOrNull("raw_text"), ans, q.optStringOrNull("explanation") ?: q.optStringOrNull("solution"), q.optStringOrNull("bot"), q.optStringOrNull("video"), q.optStringOrNull("audio"), normalizedOpts, jsonArray(q, "question_images"), jsonArray(q, "explanation_images")))
            }
            out.add(ImportedTest(t.optStringOrNull("id"), t.optString("title", "Section ${i + 1}"), t.optStringOrNull("path"), t.optDouble("total_marks", 0.0), t.optInt("duration", 0), t.optDouble("time_per_question", 0.0), qs))
        }
        return out
    }

    fun jsonArray(o: org.json.JSONObject, k: String): List<String> { val a = o.optJSONArray(k) ?: return emptyList(); return buildList { for (i in 0 until a.length()) add(a.optString(i)) } }
    fun org.json.JSONObject.optStringOrNull(k: String): String? = if (!has(k) || isNull(k)) null else optString(k, null)
}
