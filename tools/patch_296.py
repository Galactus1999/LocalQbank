from pathlib import Path
ROOT=Path(__file__).resolve().parent
def p(rel): return ROOT/rel
def replace(rel, old, new):
    f=p(rel); s=f.read_text()
    if old not in s: raise SystemExit(f"PATCH_MISS {rel}: {old[:120]!r}")
    f.write_text(s.replace(old,new,1))
bg="app/build.gradle.kts"
s=p(bg).read_text()
s=s.replace("versionCode = 389","versionCode = 390",1).replace('versionName = "8.3.295"','versionName = "8.3.296"',1)
needle='    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.2")\n'
if "androidx.profileinstaller:profileinstaller:1.4.1" not in s:
    if needle not in s: raise SystemExit("PATCH_MISS app gradle dependency anchor")
    s=s.replace(needle,needle+'    implementation("androidx.profileinstaller:profileinstaller:1.4.1")\n',1)
p(bg).write_text(s)

rel="app/src/main/java/com/localqbank/library/BenCognitiveArchitecture.kt"
replace(rel,'''        val basePlan = if (control.plannerEnabled) planner.plan(clean, control.knowledgeGraphEnabled) else BenCognitiveArchitecture.Plan(clean, null, emptyList(), emptySet(), emptyList(), 30)
        val retrieval = if (control.retrievalEnabled) frankenstein.retrieve(clean, 12) else BenFrankensteinEngine.Retrieval(LongArray(0), emptyList(), listOf(clean), emptySet())
''','''        val understanding = runCatching { knowledge.understand(clean) }.getOrElse {
            ClinicalKnowledgeLayer.Understanding(clean, clean, emptyList(), null, emptySet(), listOf(clean), false, emptySet(), emptyList())
        }
        val basePlan = if (control.plannerEnabled) planner.plan(clean, control.knowledgeGraphEnabled, understanding) else BenCognitiveArchitecture.Plan(clean, null, emptyList(), emptySet(), emptyList(), 30)
        val retrieval = if (control.retrievalEnabled) frankenstein.retrieve(clean, 12, understanding) else BenFrankensteinEngine.Retrieval(LongArray(0), emptyList(), listOf(clean), emptySet())
''')
replace(rel,'''            visualIntent = runCatching { ClinicalKnowledgeLayer(app).understand(clean).visualIntent }.getOrDefault(false),
            examTerms = runCatching { BenExamTerminology(app).understand(clean).matched.map { it.term }.take(8) }.getOrDefault(emptyList()),
''','''            visualIntent = understanding.visualIntent,
            examTerms = understanding.examTerms.take(8),
''')
replace(rel,'''        val specialist = if (control.specialistRoutingEnabled) frankenstein.specialist(clean) else BenFrankensteinEngine.Specialist("clinical_retrieval", "Clinical retrieval", "Specialist routing disabled")
''','''        val specialist = if (control.specialistRoutingEnabled) frankenstein.specialist(clean, understanding) else BenFrankensteinEngine.Specialist("clinical_retrieval", "Clinical retrieval", "Specialist routing disabled")
''')
replace(rel,'''        val relationBoost = (knowledge.relationCount(knowledge.resolve(query)) * 2).coerceAtMost(20)
''','''        val relationBoost = (understanding.concepts.sumOf { it.related.size } * 2).coerceAtMost(20)
''')
replace(rel,'''    fun plan(query: String, knowledgeGraphEnabled: Boolean = true): BenCognitiveArchitecture.Plan {
        val understanding = if (knowledgeGraphEnabled) knowledgeUnderstanding(query) else
''','''    fun plan(query: String, knowledgeGraphEnabled: Boolean = true, precomputed: ClinicalKnowledgeLayer.Understanding? = null): BenCognitiveArchitecture.Plan {
        val understanding = if (knowledgeGraphEnabled) (precomputed ?: knowledgeUnderstanding(query)) else
''')

rel="app/src/main/java/com/localqbank/library/BenFrankensteinEngine.kt"
replace(rel,'''    fun retrieve(query: String, limit: Int = 12): Retrieval {
        val understanding = runCatching { clinical.understand(query) }
''','''    fun retrieve(query: String, limit: Int = 12, precomputed: ClinicalKnowledgeLayer.Understanding? = null): Retrieval {
        val understanding = precomputed ?: runCatching { clinical.understand(query) }
''')
replace(rel,'''    fun specialist(query: String): Specialist {
        val understanding = runCatching { clinical.understand(query) }.getOrNull()
''','''    fun specialist(query: String, precomputed: ClinicalKnowledgeLayer.Understanding? = null): Specialist {
        val understanding = precomputed ?: runCatching { clinical.understand(query) }.getOrNull()
''')

rel="app/src/main/java/com/localqbank/library/RenActivity.kt"
replace(rel,'''            val local = try { AppManagers.benBrain.answer(query) } catch (_: Exception) { null }
            val concepts = context?.concepts.orEmpty().ifEmpty { local?.plan?.concepts.orEmpty() }
''','''            val concepts = context?.concepts.orEmpty()
''')
replace(rel,'''                    local?.plan?.specialist?.let { append("Specialist: ").append(it).append("\\n") }
                    local?.confidence?.let { append("Local confidence: ").append(it).append("/100\\n") }
''',"")
replace(rel,'''        val answer=WebView(this).apply {
''','''        val answer=WebView(this).apply {
            id = View.generateViewId()
            contentDescription = "frankensteinChat"
''')
replace(rel,'''        val input=EditText(this).apply {
            hint="Ask Dr. Frankenstein…"; textSize=15f
''','''        val input=EditText(this).apply {
            hint="Ask Dr. Frankenstein…"; textSize=15f
            contentDescription = "frankensteinPrompt"
''')

rel="app/src/main/java/com/localqbank/library/BenNeuralModelManager.kt"
replace(rel,'''            if (embeddingTokenizerFile.exists() && !embeddingTokenizerFile.delete()) { temp.delete(); return null }
            if (!temp.renameTo(embeddingTokenizerFile)) { temp.delete(); return null }
            embeddingTokenizerFile
''','''            RovexAtomicFile.replace(temp, embeddingTokenizerFile)
            embeddingTokenizerFile
''')
replace(rel,'''            if (!temp.renameTo(destination)) {
                temp.delete()
                return null
            }
            InstalledModel(profile, destination, total)
''','''            RovexAtomicFile.replace(temp, destination)
            InstalledModel(profile, destination, total)
''')

rel="app/src/main/java/com/localqbank/library/KnowledgeEngineManager.kt"
replace(rel,'''        val noteFile = File(notesDir, "q_$safeId.txt")
        val old = if (noteFile.exists()) noteFile.readText() else ""
        if (old != note) noteFile.writeText(note)
''','''        val noteFile = File(notesDir, "q_$safeId.txt")
        val old = if (noteFile.exists()) noteFile.readText() else ""
        if (old != note) writeAtomic(noteFile, note)
''')
replace(rel,'''        tableRx.findAll(explanation).take(8).forEachIndexed { index, m ->
            File(tablesDir, "q_${safeId}_$index.html").writeText(m.value)
            tables++
        }
''','''        tableRx.findAll(explanation).take(8).forEachIndexed { index, m ->
            writeAtomic(File(tablesDir, "q_${safeId}_$index.html"), m.value)
            tables++
        }
''')
replace(rel,'''


    fun saveTextNote''','''

    private fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".partial")
        try {
            java.io.FileOutputStream(temp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            RovexAtomicFile.replace(temp, target)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun saveTextNote''')

rel="app/src/main/java/com/localqbank/library/ApkgImporter.kt"
replace(rel,'''    private fun writeState(file:File,uri:String,sourceName:String,cardIndex:Int,totalCards:Int,skipped:Int,imported:Int=0){
        runCatching{file.writeText(JSONObject().put("uri",uri).put("sourceName",sourceName).put("cardIndex",cardIndex).put("totalCards",totalCards).put("skipped",skipped).put("imported",imported).put("updatedAt",System.currentTimeMillis()).toString())}
    }
''','''    private fun writeState(file:File,uri:String,sourceName:String,cardIndex:Int,totalCards:Int,skipped:Int,imported:Int=0){
        runCatching {
            val json = JSONObject().put("uri",uri).put("sourceName",sourceName)
                .put("cardIndex",cardIndex).put("totalCards",totalCards)
                .put("skipped",skipped).put("imported",imported)
                .put("updatedAt",System.currentTimeMillis()).toString()
            val temp = File(file.parentFile, file.name + ".partial")
            try {
                FileOutputStream(temp).use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                RovexAtomicFile.replace(temp, file)
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }
''')

rel="app/src/main/java/com/localqbank/library/SearchActivity.kt"
replace(rel,'''        } catch (t: Throwable) {
            showSearchStartupError(t)
            return
        }
''','''        } catch (t: Exception) {
            showSearchStartupError(t)
            return
        }
''')

rel="benchmark/src/main/kotlin/com/localqbank/library/benchmark/ColdStartupBenchmark.kt"
s=p(rel).read_text()
if "frankensteinWorkspaceStartup" not in s:
    anchor='''    @Test
    fun coldStartupPartialCompilation()'''
    block='''    @Test
    fun frankensteinWorkspaceStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            MemoryUsageMetric(mode = MemoryUsageMetric.Mode.Max)
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        uiAutomator {
            startActivityAndWait(Intent().setComponent(ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.RenActivity")))
            onElement(10_000) { contentDescription == "frankensteinPrompt" && isVisibleToUser }
        }
    }

'''+anchor
    if anchor not in s: raise SystemExit("PATCH_MISS benchmark anchor")
    s=s.replace(anchor,block,1)
p(rel).write_text(s)
p("app/src/main/baseline-prof").mkdir(parents=True,exist_ok=True)
p("app/src/main/baseline-prof/baseline-prof.txt").write_text("""# Seed profile. Regenerate from the Macrobenchmark module before a performance release.
HSPLcom/localqbank/library/MainActivity;->onCreate(Landroid/os/Bundle;)V
HSPLcom/localqbank/library/MainActivity;->onResume()V
Lcom/localqbank/library/AppManagers;
Lcom/localqbank/library/PerformanceManager;
Lcom/localqbank/library/RovexRuntimeEngine;
HSPLcom/localqbank/library/RovexSectionDashboardActivity;->onCreate(Landroid/os/Bundle;)V
HSPLcom/localqbank/library/RovexSectionDashboardActivity;->switchSection(Ljava/lang/String;)V
HSPLcom/localqbank/library/RenActivity;->onCreate(Landroid/os/Bundle;)V
HSPLcom/localqbank/library/RenActivity;->renderChat(Landroid/webkit/WebView;)V
Lcom/localqbank/library/FrankensteinSupportEngine;
HSPLcom/localqbank/library/RenCognitiveEngine;->searchQuestionIds(Ljava/lang/String;I)[J
""")
