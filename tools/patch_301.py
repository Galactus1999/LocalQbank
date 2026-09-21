from pathlib import Path
R=Path(__file__).resolve().parent
B=R/'app/src/main/java/com/localqbank/library'
A=R/'theme301'
p=R/'app/build.gradle.kts';s=p.read_text();s=s.replace('versionCode = 394','versionCode = 395',1).replace('versionName = "8.3.300"','versionName = "8.3.301"',1);p.write_text(s)
for n in ('ThemeManager.kt','ThemeAtmosphereDrawable.kt','RovexThemeEngine.kt'):(B/n).write_text((A/n).read_text())
for p in B.glob('*.kt'):
    s=p.read_text()
    if 'ThemeManager.OBSIDIAN_NIGHT' in s:p.write_text(s.replace('ThemeManager.OBSIDIAN_NIGHT','ThemeManager.AMOLED'))
p=B/'SettingsScreen.kt';s=p.read_text();s=s.replace('Light, Dark, Sepia, Obsidian Night, Midnight, Cosmos and Pandora','Light, Pastel, Mint, Sunset, Lavender and AMOLED')
s=s.replace('''val keys=arrayOf(ThemeManager.LIGHT,ThemeManager.DARK,ThemeManager.SEPIA,ThemeManager.AMOLED,ThemeManager.MIDNIGHT,ThemeManager.COSMOS,ThemeManager.AVATAR)
        val labels=arrayOf("Light","Dark","Sepia","Obsidian Night","Midnight Blue","Cosmos • Galaxy","Pandora • Avatar")
        val descriptions=arrayOf("Clean daylight reading","Low-glare night reading","Warm textbook paper","Near-black OLED reading with warm ivory text and cyan guidance","Deep blue, high contrast","OLED-black reading with subtle cinematic depth","Indigo bioluminescent Pandora-inspired theme")''','''val keys=arrayOf(ThemeManager.LIGHT,ThemeManager.PASTEL,ThemeManager.MINT,ThemeManager.SUNSET,ThemeManager.LAVENDER,ThemeManager.AMOLED)
        val labels=arrayOf("Light","Pastel","Mint","Sunset","Lavender","AMOLED")
        val descriptions=arrayOf("Clean airy daylight","Glossy blue-violet study UI","Fresh mint clinical UI","Warm orange-coral energy UI","Purple-blue focused study UI","Pitch-black neon OLED UI")''')
start=s.index('    private fun themeSwatch(key:String):Int=when(key)');end=s.index('\n\n    private fun addAppearance',start)
s=s[:start]+'''    private fun themeSwatch(key:String):Int=when(key){
        ThemeManager.LIGHT->Color.rgb(247,249,255)
        ThemeManager.PASTEL->Color.rgb(226,238,255)
        ThemeManager.MINT->Color.rgb(214,250,240)
        ThemeManager.SUNSET->Color.rgb(255,230,210)
        ThemeManager.LAVENDER->Color.rgb(235,225,255)
        ThemeManager.AMOLED->Color.BLACK
        else->Color.rgb(247,249,255)
    }'''+s[end:]
s=s.replace('val themes=linkedMapOf(ThemeManager.LIGHT to "Light",ThemeManager.DARK to "Dark",ThemeManager.SEPIA to "Sepia",ThemeManager.AMOLED to "Obsidian Night",ThemeManager.MIDNIGHT to "Midnight Blue",ThemeManager.COSMOS to "Cosmos • Galaxy",ThemeManager.AVATAR to "Pandora • Avatar")','val themes=linkedMapOf(ThemeManager.LIGHT to "Light",ThemeManager.PASTEL to "Pastel",ThemeManager.MINT to "Mint",ThemeManager.SUNSET to "Sunset",ThemeManager.LAVENDER to "Lavender",ThemeManager.AMOLED to "AMOLED")');p.write_text(s)
p=B/'QuizActivity.kt';s=p.read_text();s=s.replace('''listOf(
            ThemeManager.LIGHT to "Light",
            ThemeManager.DARK to "Dark",
            ThemeManager.SEPIA to "Sepia",
            ThemeManager.AMOLED to "Obsidian Night",
            ThemeManager.MIDNIGHT to "Midnight",
            ThemeManager.COSMOS to "Cosmos",
            ThemeManager.AVATAR to "Pandora"
        )''','''listOf(
            ThemeManager.LIGHT to "Light",
            ThemeManager.PASTEL to "Pastel",
            ThemeManager.MINT to "Mint",
            ThemeManager.SUNSET to "Sunset",
            ThemeManager.LAVENDER to "Lavender",
            ThemeManager.AMOLED to "AMOLED"
        )''');p.write_text(s)
p=B/'ResilienceManager.kt';s=p.read_text();s=s.replace('RovexAdaptiveUi.apply(activity)\n                ProductionCrashReporter.screen','RovexAdaptiveUi.apply(activity)\n                RovexThemeEngine.apply(activity)\n                ProductionCrashReporter.screen');s=s.replace('a.window.decorView.post { RovexAdaptiveUi.apply(a) }','a.window.decorView.post { RovexAdaptiveUi.apply(a); RovexThemeEngine.apply(a) }');p.write_text(s)
