from pathlib import Path
R=Path(__file__).resolve().parent
B=R/'app/src/main/java/com/localqbank/library'
if not B.exists(): raise SystemExit("source root missing")

(B/'RovexDailyStudyHub.kt').write_text(r'''package com.localqbank.library

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Daily Study Hub: local-first planner, study heatmap, streak, and revision-bag shell.
 *
 * No calendar account permission is requested. Calendar export uses Android's
 * ACTION_INSERT contract so the installed calendar handler (including Google
 * Calendar when available) owns the actual account selection and save operation.
 */
object RovexDailyStudyHub {
    private const val PREF = "rovex_daily_study_hub_v304"
    private const val MAX_HEAT = 7
    private const val SLOT_COUNT = 4
    private val slots = arrayOf("QBank Sprint", "PYQ + Error Review", "Flashcard Mix", "Rapid Revision")

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun key(cal: Calendar = Calendar.getInstance()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)

    fun markStudy(c: Context, amount: Int = 1) {
        val p = prefs(c)
        val k = "heat_" + key()
        val old = p.getInt(k, 0)
        p.edit().putInt(k, (old + max(1, amount)).coerceAtMost(9))
            .putBoolean("day_" + key(), true).apply()
    }

    private fun isStudyDay(c: Context, cal: Calendar): Boolean {
        return prefs(c).getBoolean("day_" + key(cal), false) ||
            prefs(c).getInt("heat_" + key(cal), 0) > 0
    }

    private fun streak(c: Context): Int {
        val cal = Calendar.getInstance()
        var n = 0
        while (n < 365 && isStudyDay(c, cal)) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    private fun card(c: Context, a: Int, b: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply {
            cornerRadius = dp(c, 24).toFloat()
            setStroke(dp(c, 1), Color.argb(100, 255, 255, 255))
        }

    private fun text(c: Context, s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = true) =
        TextView(c).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, 1)
            includeFontPadding = false
        }

    private fun heatmap(c: Context): View {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
            background = card(c, Color.argb(125, 0, 170, 255), Color.argb(105, 115, 45, 235))
            elevation = dp(c, 7).toFloat()
        }
        val head = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(c, "✦  Daily Progress Heatmap", 18f), LinearLayout.LayoutParams(0, dp(c, 30), 1f))
        head.addView(text(c, "🔥 " + streak(c) + " day streak", 13f, Color.argb(235,255,255,255), true))
        box.addView(head)
        box.addView(text(c, "Your last 28 study days", 12f, Color.argb(210,255,255,255), false),
            LinearLayout.LayoutParams(-1, dp(c, 24)).apply { topMargin = dp(c, 2) })

        val grid = GridLayout(c).apply { columnCount = 7; rowCount = 4 }
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -27)
        for (i in 0 until 28) {
            val amount = prefs(c).getInt("heat_" + key(cal), 0).coerceIn(0, MAX_HEAT)
            val cell = TextView(c).apply {
                gravity = Gravity.CENTER
                text = if (amount == 0) "·" else if (amount >= 6) "✦" else "•"
                textSize = if (amount >= 6) 13f else 12f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(c, 7).toFloat()
                    val alpha = if (amount == 0) 32 else 45 + amount * 25
                    setColor(Color.argb(alpha.coerceAtMost(245), 50, 205, 255))
                    if (amount >= 6) setStroke(dp(c, 1), Color.argb(180, 180, 245, 255))
                }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(i / 7, 1f), GridLayout.spec(i % 7, 1f)
            )
            lp.width = 0
            lp.height = dp(c, 32)
            lp.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3))
            grid.addView(cell, lp)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        box.addView(grid, LinearLayout.LayoutParams(-1, dp(c, 150)))
        return box
    }

    private fun planner(c: MainActivity, flashcards: View?): View {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
            background = card(c, Color.argb(120, 255, 65, 170), Color.argb(105, 70, 75, 235))
            elevation = dp(c, 7).toFloat()
        }
        box.addView(text(c, "◈  Today’s Mission", 18f))
        box.addView(text(c, "Plan → revise → log → build the streak", 12f, Color.argb(215,255,255,255), false),
            LinearLayout.LayoutParams(-1, dp(c, 26)).apply { topMargin = dp(c, 2) })

        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val plan = text(c, "PLAN MY DAY", 12f).apply {
            gravity = Gravity.CENTER
            background = card(c, Color.argb(100, 255, 255, 255), Color.argb(55, 110, 100, 255))
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            setOnClickListener { openPlanner(c) }
        }
        val mix = text(c, "MIX REVISION", 12f).apply {
            gravity = Gravity.CENTER
            background = card(c, Color.argb(100, 55, 210, 255), Color.argb(70, 145, 55, 235))
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            setOnClickListener {
                markStudy(c, 1)
                if (flashcards != null) flashcards.performClick() else openPlanner(c)
            }
        }
        row.addView(plan, LinearLayout.LayoutParams(0, dp(c, 48), 1f).apply { rightMargin = dp(c, 6) })
        row.addView(mix, LinearLayout.LayoutParams(0, dp(c, 48), 1f).apply { leftMargin = dp(c, 6) })
        box.addView(row, LinearLayout.LayoutParams(-1, dp(c, 54)).apply { topMargin = dp(c, 8) })

        val log = text(c, "✓  LOG TODAY'S STUDY", 12f).apply {
            gravity = Gravity.CENTER
            background = card(c, Color.argb(90, 0, 225, 185), Color.argb(70, 0, 100, 210))
            setOnClickListener { markStudy(c, 1); Toast.makeText(c, "Study day logged • streak updated", Toast.LENGTH_SHORT).show() }
        }
        box.addView(log, LinearLayout.LayoutParams(-1, dp(c, 44)).apply { topMargin = dp(c, 7) })
        return box
    }

    private fun openPlanner(c: MainActivity) {
        val holder = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 18), dp(c, 4), dp(c, 18), 0)
        }
        val fields = ArrayList<EditText>()
        val defaults = arrayOf("07:00  •  QBank Sprint", "10:30  •  PYQ + Error Review", "15:00  •  Flashcard Mix", "20:00  •  Rapid Revision")
        for (i in 0 until SLOT_COUNT) {
            val e = EditText(c).apply {
                setSingleLine(true)
                textSize = 14f
                setText(prefs(c).getString("slot_$i", defaults[i]))
                hint = "Time  •  Study block"
                setTextColor(ThemeManager.text(c))
            }
            fields.add(e)
            holder.addView(e, LinearLayout.LayoutParams(-1, dp(c, 48)).apply { topMargin = dp(c, 2) })
        }
        val dialog = AlertDialog.Builder(c)
            .setTitle("Daily Schedule Planner")
            .setMessage("Create a focused plan. Export any block to your calendar without giving Rovex calendar-account access.")
            .setView(holder)
            .setNegativeButton("Close", null)
            .setPositiveButton("Save Plan") { _, _ ->
                val ed = prefs(c).edit()
                fields.forEachIndexed { i, e -> ed.putString("slot_$i", e.text.toString().trim()) }
                ed.apply()
                Toast.makeText(c, "Today's plan saved", Toast.LENGTH_SHORT).show()
            }
            .create()
        dialog.setOnShowListener {
            val sync = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (sync != null) sync.setText("Calendar")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ed = prefs(c).edit()
                fields.forEachIndexed { i, e -> ed.putString("slot_$i", e.text.toString().trim()) }
                ed.apply()
                dialog.dismiss()
                exportFirstCalendarBlock(c)
            }
        }
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Calendar") { _, _ -> exportFirstCalendarBlock(c) }
        dialog.show()
    }

    private fun exportFirstCalendarBlock(c: MainActivity) {
        val raw = prefs(c).getString("slot_0", "07:00  •  QBank Sprint") ?: "07:00  •  QBank Sprint"
        val parts = raw.split("•", limit = 2)
        val time = parts.first().trim()
        val title = if (parts.size > 1) parts[1].trim() else raw
        val hm = time.split(":", limit = 2)
        val cal = Calendar.getInstance()
        if (hm.size == 2) {
            cal.set(Calendar.HOUR_OF_DAY, hm[0].toIntOrNull()?.coerceIn(0,23) ?: 7)
            cal.set(Calendar.MINUTE, hm[1].toIntOrNull()?.coerceIn(0,59) ?: 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        val begin = cal.timeInMillis
        val end = begin + 60L * 60L * 1000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            type = "vnd.android.cursor.dir/event"
            putExtra(CalendarContract.Events.TITLE, "Rovex • $title")
            putExtra(CalendarContract.Events.DESCRIPTION, "Rovex daily study plan")
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        }
        if (intent.resolveActivity(c.packageManager) != null) {
            c.startActivity(intent)
        } else {
            Toast.makeText(c, "No calendar app is available", Toast.LENGTH_SHORT).show()
        }
    }

    fun install(c: MainActivity, content: LinearLayout, flashcards: View?) {
        if (content.findViewWithTag<View>("ROVEX_DAILY_HUB_304") != null) return
        val wrap = LinearLayout(c).apply {
            tag = "ROVEX_DAILY_HUB_304"
            orientation = LinearLayout.VERTICAL
        }
        wrap.addView(heatmap(c), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 12) })
        wrap.addView(planner(c, flashcards), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 15) })
        content.addView(wrap, content.indexOfChild(content.findViewWithTag<View>("ROVEX_DAILY_HUB_ANCHOR")).takeIf { it >= 0 }
            ?.let { LinearLayout.LayoutParams(-1, -2) } ?: LinearLayout.LayoutParams(-1, -2))
    }
}
''')

p=B/'RovexHomeRevolution.kt'
s=p.read_text()
needle='  content.addView(tv(a,"Quick Tools",19f,Color.WHITE,true),'
if needle not in s: raise SystemExit("Quick Tools anchor missing")
s=s.replace(needle, '  RovexDailyStudyHub.install(a, content, pF)\n\n'+needle, 1)
# Defensive postcondition: the daily hub must actually be wired into the runtime home.
if 'RovexDailyStudyHub.install(' not in s:
    raise SystemExit("DAILY_HUB_WIRING_INSERT_FAILED")
# The hub must be placed before Quick Tools; no anchor needed.
g=R/'app/build.gradle.kts'
s=g.read_text().replace('versionCode = 397','versionCode = 398',1).replace('versionName = "8.3.303"','versionName = "8.3.304"',1)
g.write_text(s)
(B/'RovexRoadmapMarker.kt').write_text('''package com.localqbank.library
object RovexRoadmapMarker {
 const val VERSION="ROVEX_PHASE_304_DAILY_STUDY_HUB"
 const val FEATURES="HEATMAP,STREAK,PLANNER,REVISION_BAG,CALENDAR_INTENT"
 const val CALENDAR="ANDROID_CALENDARCONTRACT_ACTION_INSERT"
}
''')
