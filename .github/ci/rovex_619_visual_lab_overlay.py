#!/usr/bin/env python3
"""Apply Rovex v8.3.619 developer-only Visual Lab to verified v8.3.618/704."""
from pathlib import Path
import sys

EXPECTED_VERSION = 'versionName = "8.3.618"'
EXPECTED_CODE = "versionCode = 704"

VISUAL_LAB = r'''package com.localqbank.library

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Developer-only visual contract surface; never mutates production study/theme state. */
class VisualLabActivity : AppCompatActivity() {
    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        SystemUi.immersive(this)
        setContentView(build())
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(18), d(14), d(18), d(30))
        }
        root.addView(header())
        root.addView(section("THEME SAMPLES", "All eight immutable profiles, without changing the active app theme."))
        root.addView(themeSamples())
        root.addView(section("SURFACE SYSTEM", "Standard/prominent glass, borders and highlights."))
        root.addView(surfaceSamples())
        root.addView(section("ACTION SYSTEM", "Shared button, ripple, border and press feedback."))
        root.addView(actionSamples())
        root.addView(section("STUDY STATES", "Representative QBank and flashcard visual contracts."))
        root.addView(studyStates())
        root.addView(section("PROGRESS + MOTION", "Stable progress plus bounded press animation."))
        root.addView(progressMotion())
        root.addView(section("DIALOG + FEEDBACK", "Dialog, sound and haptic test controls only."))
        root.addView(feedback())
        return ScrollView(this).apply {
            isFillViewport = true
            background = ThemeManager.backgroundDrawable(this@VisualLabActivity)
            addView(root)
        }
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val copy = LinearLayout(this@VisualLabActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@VisualLabActivity).apply {
                text = "Visual Lab"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.text(this@VisualLabActivity))
            })
            addView(TextView(this@VisualLabActivity).apply {
                text = "Rovex visual contract • developer surface"; textSize = 11.5f
                setTextColor(ThemeManager.muted(this@VisualLabActivity))
            })
        }
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        addView(button("CLOSE") { finish() }, LinearLayout.LayoutParams(d(82), d(40)))
    }

    private fun section(title: String, sub: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(d(2), d(18), d(2), d(7))
        addView(TextView(this@VisualLabActivity).apply {
            text = title; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = .08f
            setTextColor(ThemeManager.accent(this@VisualLabActivity))
        })
        addView(TextView(this@VisualLabActivity).apply {
            text = sub; textSize = 10.5f; setTextColor(ThemeManager.muted(this@VisualLabActivity))
        })
    }

    private fun themeSamples() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val keys = listOf(
            ThemeManager.LIGHT, ThemeManager.PASTEL, ThemeManager.MINT, ThemeManager.SUNSET,
            ThemeManager.LAVENDER, ThemeManager.AMOLED, ThemeManager.PANDORA, ThemeManager.SPACE
        )
        keys.forEachIndexed { i, key ->
            val p = RovexThemeProfile.forKey(key)
            addView(LinearLayout(this@VisualLabActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(d(7), d(8), d(7), d(7))
                background = GradientDrawable().apply { setColor(p.panel); cornerRadius = d(12).toFloat(); setStroke(d(1), p.accent) }
                contentDescription = "Theme sample \${p.name}"
                addView(TextView(this@VisualLabActivity).apply {
                    text = p.name; textSize = 9f; typeface = Typeface.DEFAULT_BOLD; setTextColor(p.text)
                })
                val swatches = LinearLayout(this@VisualLabActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(6), 0, 0) }
                listOf(p.backgroundA, p.elevated, p.accent).forEachIndexed { j, c ->
                    addView(View(this@VisualLabActivity).apply {
                        background = GradientDrawable().apply { setColor(c); cornerRadius = d(5).toFloat() }
                    }, LinearLayout.LayoutParams(0, d(15), 1f).apply { if (j > 0) leftMargin = d(3) })
                }
                addView(swatches)
            }, LinearLayout.LayoutParams(0, d(66), 1f).apply { if (i > 0) leftMargin = d(4) })
        }
    }

    private fun surfaceSamples() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(surface("Standard glass", false))
        addView(surface("Prominent glass", true), LinearLayout.LayoutParams(-1, d(76)).apply { topMargin = d(7) })
    }

    private fun surface(label: String, prominent: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(d(15), d(10), d(15), d(10))
        RovexVisualSurfaceStyle.apply(this, this@VisualLabActivity, 18f, prominent, false)
        addView(TextView(this@VisualLabActivity).apply {
            text = label; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(this@VisualLabActivity))
        })
        addView(TextView(this@VisualLabActivity).apply {
            text = if (prominent) "Stronger edge + higher surface presence" else "Bounded translucency + accent edge + highlight"
            textSize = 10.5f; setTextColor(ThemeManager.muted(this@VisualLabActivity))
        })
    }

    private fun button(label: String, emphasized: Boolean = false, click: () -> Unit = {}) = TextView(this).apply {
        text = label; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(d(7), 0, d(7), 0)
        RovexVisualButtonStyle.apply(this, this@VisualLabActivity, emphasized)
        setOnClickListener { click() }
    }

    private fun actionSamples() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(button("NORMAL"), LinearLayout.LayoutParams(0, d(44), 1f))
        addView(button("EMPHASIZED", true), LinearLayout.LayoutParams(0, d(44), 1f).apply { leftMargin = d(7) })
        addView(button("PRESS TEST") {
            android.widget.Toast.makeText(this@VisualLabActivity, "Ripple + press motion + haptic path active", android.widget.Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, d(44), 1f).apply { leftMargin = d(7) })
    }

    private fun studyStates() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val states = listOf(
            Triple("DEFAULT OPTION", ThemeManager.optionBg(this@VisualLabActivity), ThemeManager.optionText(this@VisualLabActivity)),
            Triple("SELECTED OPTION", ThemeManager.pastelBlueFill(this@VisualLabActivity), ThemeManager.pastelBlueText(this@VisualLabActivity)),
            Triple("CORRECT OPTION", ThemeManager.correctBg(this@VisualLabActivity), ThemeManager.correctText(this@VisualLabActivity)),
            Triple("WRONG OPTION", ThemeManager.wrongBg(this@VisualLabActivity), ThemeManager.wrongText(this@VisualLabActivity))
        )
        states.forEachIndexed { i, s ->
            addView(LinearLayout(this@VisualLabActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(d(13), d(9), d(13), d(9))
                background = GradientDrawable().apply { setColor(s.second); cornerRadius = d(13).toFloat(); setStroke(d(1), s.third) }
                addView(TextView(this@VisualLabActivity).apply {
                    text = s.first; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(s.third)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(this@VisualLabActivity).apply {
                    text = "A"; textSize = 11f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(s.third)
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(32, Color.red(s.third), Color.green(s.third), Color.blue(s.third))) }
                }, LinearLayout.LayoutParams(d(28), d(28)))
            }, LinearLayout.LayoutParams(-1, d(50)).apply { if (i > 0) topMargin = d(6) })
        }
        val flash = LinearLayout(this@VisualLabActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(flashCard("FLASHCARD FRONT", ThemeManager.pastelLavenderFill(this@VisualLabActivity), ThemeManager.pastelLavenderText(this@VisualLabActivity)), LinearLayout.LayoutParams(0, d(64), 1f))
            addView(flashCard("MARKED", ThemeManager.pastelYellowFill(this@VisualLabActivity), ThemeManager.pastelYellowText(this@VisualLabActivity)), LinearLayout.LayoutParams(0, d(64), 1f).apply { leftMargin = d(7) })
        }
        addView(flash, LinearLayout.LayoutParams(-1, d(64)).apply { topMargin = d(7) })
    }

    private fun flashCard(label: String, fill: Int, text: Int) = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(fill); cornerRadius = d(15).toFloat(); setStroke(d(1), text) }
        addView(TextView(this@VisualLabActivity).apply { this.text = label; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(text) })
    }

    private fun progressMotion() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val track = LinearLayout(this@VisualLabActivity).apply {
            background = GradientDrawable().apply { setColor(ThemeManager.panel(this@VisualLabActivity)); cornerRadius = d(9).toFloat() }
            addView(View(this@VisualLabActivity).apply {
                background = GradientDrawable().apply { setColor(ThemeManager.accent(this@VisualLabActivity)); cornerRadius = d(9).toFloat() }
            }, LinearLayout.LayoutParams(0, d(13), .68f))
        }
        addView(track, LinearLayout.LayoutParams(-1, d(13)))
        addView(TextView(this@VisualLabActivity).apply {
            text = "68% series progress • continuous counter contract"; textSize = 10.5f
            setTextColor(ThemeManager.muted(this@VisualLabActivity)); setPadding(0, d(4), 0, d(8))
        })
        addView(button("RUN PRESS MOTION", true) {
            RovexMotionSystem.pressDown(track)
            track.postDelayed({ RovexMotionSystem.pressUp(track) }, RovexMotionSpec.PRESS_DOWN_MS)
        }, LinearLayout.LayoutParams(-1, d(44)))
    }

    private fun feedback() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(button("OPEN DIALOG") { dialogSample() }, LinearLayout.LayoutParams(0, d(44), 1f))
        addView(button("SOUND + HAPTIC") {
            RovexSoundFeedback.setEnabled(this@VisualLabActivity, true)
            RovexSoundFeedback.playTest(this@VisualLabActivity)
            window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        }, LinearLayout.LayoutParams(0, d(44), 1f).apply { leftMargin = d(7) })
    }

    private fun dialogSample() {
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(d(18), d(14), d(18), d(8))
            background = GradientDrawable().apply { setColor(ThemeManager.dialogBg(this@VisualLabActivity)); cornerRadius = d(22).toFloat(); setStroke(d(1), RovexVisualColors.border(this@VisualLabActivity, true)) }
            addView(TextView(this@VisualLabActivity).apply { text = "Visual dialog"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(this@VisualLabActivity)) })
            addView(TextView(this@VisualLabActivity).apply { text = "Bounded production-style dialog sample. No study state changes."; textSize = 11.5f; setTextColor(ThemeManager.muted(this@VisualLabActivity)); setPadding(0, d(5), 0, d(12)) })
            addView(button("DONE", true) { dialog.dismiss() }, LinearLayout.LayoutParams(-1, d(42)))
        }
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
'''

def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: rovex_619_visual_lab_overlay.py <project>")
    project = Path(sys.argv[1]).resolve()
    app = project / "app"
    pkg = app / "src" / "main" / "java" / "com" / "com" / "localqbank" / "library"
    pkg = app / "src" / "main" / "java" / "com" / "localqbank" / "library"
    gradle = app / "build.gradle.kts"
    manifest = app / "src" / "main" / "AndroidManifest.xml"
    settings = pkg / "SettingsScreen.kt"
    test = app / "src" / "androidTest" / "java" / "com" / "localqbank" / "library" / "RovexStage7InstrumentedTest.kt"
    if not all(p.is_file() for p in (gradle, manifest, settings, test)):
        raise SystemExit("v8.3.619 overlay: required baseline files missing")
    g = gradle.read_text(encoding="utf-8")
    if EXPECTED_VERSION not in g or EXPECTED_CODE not in g:
        raise SystemExit("v8.3.619 overlay requires v8.3.618/704; refusing to patch")
    lab = pkg / "VisualLabActivity.kt"
    if lab.exists():
        raise SystemExit("v8.3.619 overlay: VisualLabActivity already exists")
    lab.write_text(VISUAL_LAB, encoding="utf-8")
    m = manifest.read_text(encoding="utf-8")
    anchor = '<activity android:name=".SettingsActivity" android:screenOrientation="unspecified" android:resizeableActivity="true"/>\n'
    if anchor not in m:
        raise SystemExit("v8.3.619 overlay: SettingsActivity manifest anchor missing")
    manifest.write_text(m.replace(anchor, anchor + '<activity android:name=".VisualLabActivity" android:screenOrientation="unspecified" android:resizeableActivity="true" android:exported="false"/>\n', 1), encoding="utf-8")
    s = settings.read_text(encoding="utf-8")
    anchor = '        root.addView(category("Reference PDFs for Ben","Import local PDFs for bounded offline retrieval and AI context","▤",Color.rgb(80,180,210)){activity.startActivity(Intent(activity,BenPdfImportActivity::class.java))},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})\n'
    if anchor not in s:
        raise SystemExit("v8.3.619 overlay: Settings category anchor missing")
    row = '        if (BuildConfig.DEBUG) root.addView(category("Visual Lab (Developer)","Inspect theme tokens, glass surfaces, study states, motion, dialogs and feedback in isolation","◌",ThemeManager.accent(activity)){activity.startActivity(Intent(activity,VisualLabActivity::class.java))},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})\n'
    settings.write_text(s.replace(anchor, anchor + row, 1), encoding="utf-8")
    t = test.read_text(encoding="utf-8")
    anchor = '''    @Test
    fun groundedBenPipelineReturnsGroundedAnswerOrFallbackSignalWhenNeuralAccelerationIsUnavailable() {
'''
    if anchor not in t:
        raise SystemExit("v8.3.619 overlay: instrumented test anchor missing")
    test_body = '''    @Test
    fun developerVisualLabLaunchesAndExposesCoreVisualContracts() {
        ActivityScenario.launch<VisualLabActivity>(Intent(context, VisualLabActivity::class.java)).use { scenario ->
            assertTrue("VisualLabActivity failed to reach RESUMED", scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            scenario.onActivity { activity ->
                assertTrue("Visual Lab title missing", activity.findTextView("Visual Lab")?.isShown == true)
                assertTrue("Theme samples missing", activity.findTextView("THEME SAMPLES")?.isShown == true)
                assertTrue("Surface system missing", activity.findTextView("SURFACE SYSTEM")?.isShown == true)
                assertTrue("Study states missing", activity.findTextView("STUDY STATES")?.isShown == true)
                assertTrue("Dialog/feedback section missing", activity.findTextView("DIALOG + FEEDBACK")?.isShown == true)
            }
        }
    }

'''
    test.write_text(t.replace(anchor, test_body + anchor, 1), encoding="utf-8")
    gradle.write_text(g.replace(EXPECTED_CODE, "versionCode = 705", 1).replace(EXPECTED_VERSION, 'versionName = "8.3.619"', 1), encoding="utf-8")
    print("v8.3.619 developer Visual Lab overlay: APPLIED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
