package com.localqbank.library

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.EOFException
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Streaming parser for very large combined QBank HTML files. It owns no Activity/UI state.
 * One question object is materialized at a time and delivered through the callback.
 */
class LargeHtmlScanner(private val file: File, private val onQuestion: (String, ImportedQuestion) -> Unit) {
    private val seen = HashSet<String>()
    private val maxQuestionChars = 16 * 1024 * 1024
    private var rootQuestionMode = false

    fun scan() {
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 256 * 1024).use { r ->
            // seekToDataArray() consumes the opening '[' so the parser starts exactly
            // at the first element of the real data array.
            if (!seekToDataArray(r)) throw IllegalArgumentException("No supported QBank data array found")
            while (true) {
                skipWs(r)
                val c = peek(r)
                if (c == -1 || c == ']'.code) break
                if (c == ','.code) { r.read(); continue }
                if (c != '{'.code) { skipValue(r); continue }
                if (rootQuestionMode) parseRootQuestion(r) else parseTestObject(r)
                skipWs(r)
                if (peek(r) == ','.code) r.read()
            }
        }
    }

    private fun seekToDataArray(r: BufferedReader): Boolean {
        // Do not merely search for the word TESTS_LIST: it also appears in comments
        // inside some generated QBanks. Match an actual JavaScript assignment such as
        //   const TESTS_LIST = [ ... ]
        //   let APP_DATA = [ ... ]
        // and consume the opening '['. This fixes the false match that caused
        // "Expected '[' in QBank data" / empty imports on DAMS files.
        val window = StringBuilder()
        val assignment = Pattern.compile(
            "(?:const\\s+|let\\s+|var\\s+)?(?:window\\.)?(TESTS_LIST|APP_DATA|QUESTIONS_DATA|questions_json|questionData|questions|question_list|items)\\s*=\\s*\\[",
            Pattern.CASE_INSENSITIVE
        )
        while (true) {
            val c = r.read()
            if (c < 0) return false
            window.append(c.toChar())
            if (window.length > 320) window.delete(0, window.length - 320)
            val m = assignment.matcher(window)
            if (m.find()) {
                val name=m.group(1) ?: ""
                rootQuestionMode=name.contains("question", true) || name.equals("items", true)
                return true
            }
        }
    }

    private fun parseRootQuestion(r: BufferedReader) {
        try {
            val raw=readObject(r)
            if (raw.length > maxQuestionChars) return
            val o=org.json.JSONObject(raw)
            val q=normalize(o) ?: return
            val key="Imported Section|"+(q.sourceId ?: "")+"|"+q.text.take(180)
            if(seen.add(key)) onQuestion("Imported Section",q)
        } catch (_: Exception) { }
    }

    private fun parseTestObject(r: BufferedReader) {
        expect(r, '{')
        var title = "DAMS PYQ"
        while (true) {
            skipWs(r)
            val c = peek(r)
            if (c == '}'.code) { r.read(); return }
            if (c == ','.code) { r.read(); continue }
            if (c != '"'.code) { skipValue(r); continue }
            val key = readString(r)
            skipWs(r); expect(r, ':'); skipWs(r)
            when (key.lowercase()) {
                "title", "name", "subject", "section" -> {
                    val v = readSimpleStringOrValue(r)
                    if (v.isNotBlank() && v.length < 150) title = v
                }
                "questions" -> parseQuestionsArray(r, title)
                else -> skipValue(r)
            }
        }
    }

    private fun parseQuestionsArray(r: BufferedReader, section: String) {
        skipWs(r)
        if (peek(r) != '['.code) { skipValue(r); return }
        r.read()
        while (true) {
            skipWs(r)
            val c = peek(r)
            if (c == -1 || c == ']'.code) { if (c == ']'.code) r.read(); return }
            if (c == ','.code) { r.read(); continue }
            if (c != '{'.code) { skipValue(r); continue }
            val raw = readObject(r)
            if (raw.length <= maxQuestionChars) parseCandidate(raw, section)
        }
    }

    private fun parseCandidate(src: String, section: String) {
        try {
            val o = org.json.JSONObject(src)
            val q = normalize(o) ?: return
            val key = section + "|" + (q.sourceId ?: "") + "|" + q.text.take(180)
            if (seen.add(key)) onQuestion(section, q)
        } catch (_: Exception) {
            // A malformed/non-JSON object is skipped without aborting the whole import.
        }
    }

    private fun normalize(q: org.json.JSONObject): ImportedQuestion? {
        val text = clean(q.opt("raw_text") ?: q.opt("text") ?: q.opt("question") ?: q.opt("stem") ?: q.opt("question_text") ?: "")
        val options = q.opt("options") ?: q.opt("choices") ?: q.opt("answers")
        val opts = mutableListOf<ImportedOption>()
        if (options is org.json.JSONArray) for (i in 0 until options.length()) {
            val x = options.opt(i)
            if (x is org.json.JSONObject) opts.add(ImportedOption(x.optString("label", "${'A'.code + i}"), clean(x.opt("text") ?: x.opt("value") ?: x.opt("option") ?: ""), x.optBoolean("correct", false) || x.optBoolean("is_correct", false)))
            else opts.add(ImportedOption("${'A'.code + i}", clean(x), false))
        } else if (options is org.json.JSONObject) {
            for (k in options.keys()) opts.add(ImportedOption(k, clean(options.opt(k)), false))
        }
        val ans = q.optString("correct_answer", q.optString("answer", q.optString("ans", ""))).ifBlank { null }
        val ex = clean(q.opt("explanation") ?: q.opt("solution") ?: q.opt("expl") ?: "")
        val imgs = (extractImages(text) + extractImagesFromValue(q.opt("question_images") ?: q.opt("images") ?: q.opt("img") ?: q.opt("image") ?: q.opt("questionImage") ?: "")).distinct()
        val exImgs = (extractImages(ex) + extractImagesFromValue(q.opt("explanation_images") ?: q.opt("explanationImage") ?: "")).distinct()
        if (text.isBlank() || opts.isEmpty()) return null
        return ImportedQuestion(q.optString("id", q.optString("question_id", null)), text, text, ans, ex.ifBlank { null }, q.optString("bot", null), q.optString("video", null), q.optString("audio", null), opts, imgs, exImgs)
    }

    private fun clean(v: Any?): String = when (v) { null -> ""; is String -> v; else -> v.toString() }

    private fun extractImages(s: String): List<String> {
        val out = LinkedHashMap<String, Boolean>()
        val p = Pattern.compile("<img[^>]+(?:src|data-src|data-original|data-lazy-src)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(s)
        while (m.find()) out[m.group(1)] = true
        val srcset = Pattern.compile("""\bsrcset=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(s)
        while (srcset.find()) groupSrcset(srcset.group(1), out)
        if (s.trim().startsWith("data:image", true)) out[s.trim()] = true
        return out.keys.toList()
    }

    private fun extractImagesFromValue(value: Any?): List<String> {
        val out = LinkedHashMap<String, Boolean>()
        fun add(v: String) {
            val x = v.trim()
            if (x.isBlank()) return
            if (x.startsWith("data:image", true) || x.startsWith("http://", true) || x.startsWith("https://", true) || x.startsWith("content://", true) || x.startsWith("file://", true)) out[x] = true
            else extractImages(x).forEach { out[it] = true }
        }
        when (value) {
            is org.json.JSONArray -> for (i in 0 until value.length()) add(value.optString(i))
            is Collection<*> -> value.forEach { add(it?.toString().orEmpty()) }
            else -> add(value?.toString().orEmpty())
        }
        return out.keys.toList()
    }

    private fun groupSrcset(value: String?, out: MutableMap<String, Boolean>) {
        value.orEmpty().split(',').forEach { part ->
            part.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }?.let { out[it] = true }
        }
    }

    private fun readObject(r: BufferedReader): String {
        val out = StringBuilder()
        var depth = 0
        var quote = false
        var escape = false
        while (true) {
            val c = r.read()
            if (c < 0) throw EOFException("Unexpected end of QBank JSON")
            out.append(c.toChar())
            if (out.length > maxQuestionChars) throw IllegalArgumentException("Question object is too large")
            if (quote) {
                if (escape) escape = false else if (c == '\\'.code) escape = true else if (c == '"'.code) quote = false
            } else {
                when (c) {
                    '"'.code -> quote = true
                    '{'.code -> depth++
                    '}'.code -> { depth--; if (depth == 0) return out.toString() }
                }
            }
        }
    }

    private fun readSimpleStringOrValue(r: BufferedReader): String {
        skipWs(r)
        return if (peek(r) == '"'.code) readString(r) else {
            val b = StringBuilder()
            while (true) {
                val c = peek(r)
                if (c < 0 || c == ','.code || c == '}'.code) break
                b.append(r.read().toChar())
            }
            b.toString().trim()
        }
    }

    private fun readString(r: BufferedReader): String {
        expect(r, '"')
        val out = StringBuilder()
        var escape = false
        while (true) {
            val c = r.read()
            if (c < 0) throw EOFException("Unterminated JSON string")
            if (escape) {
                out.append(when (c) { 'n'.code -> '\n'; 'r'.code -> '\r'; 't'.code -> '\t'; 'b'.code -> '\b'; 'f'.code -> '\u000C'; else -> c.toChar() })
                escape = false
            } else if (c == '\\'.code) escape = true
            else if (c == '"'.code) return out.toString()
            else out.append(c.toChar())
        }
    }

    private fun skipValue(r: BufferedReader) {
        skipWs(r)
        when (peek(r)) {
            '"'.code -> readString(r)
            '{'.code -> { readContainer(r, '{'.code, '}'.code) }
            '['.code -> { readContainer(r, '['.code, ']'.code) }
            else -> while (true) { val c = peek(r); if (c < 0 || c == ','.code || c == '}'.code || c == ']'.code) break; r.read() }
        }
    }

    private fun readContainer(r: BufferedReader, open: Int, close: Int) {
        var depth = 0; var quote = false; var escape = false
        while (true) {
            val c = r.read(); if (c < 0) throw EOFException("Unexpected end of JSON")
            if (quote) { if (escape) escape = false else if (c == '\\'.code) escape = true else if (c == '"'.code) quote = false; continue }
            if (c == '"'.code) quote = true else if (c == open) depth++ else if (c == close && --depth == 0) return
        }
    }

    private fun skipWs(r: BufferedReader) { while (true) { val c = peek(r); if (c < 0 || !c.toChar().isWhitespace()) return; r.read() } }
    private fun peek(r: BufferedReader): Int { r.mark(1); val c = r.read(); r.reset(); return c }
    private fun expect(r: BufferedReader, ch: Char) { val c = r.read(); if (c != ch.code) throw IllegalArgumentException("Expected '$ch' in QBank data") }
    }

