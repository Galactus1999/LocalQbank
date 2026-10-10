#!/usr/bin/env python3
from pathlib import Path
import sys

PROJECT = Path(sys.argv[1]).resolve()
PKG = PROJECT / "app/src/main/java/com/localqbank/library"

def one(name):
    p = PKG / name
    if not p.is_file():
        raise SystemExit(f"[636] missing expected source: {p}")
    return p

gradle = PROJECT / "app/build.gradle.kts"
g = gradle.read_text()
if 'versionName = "8.3.635"' not in g or "versionCode = 721" not in g:
    raise SystemExit("[636] expected v8.3.635 / 721 baseline; refusing to patch")
gradle.write_text(g.replace('versionName = "8.3.635"', 'versionName = "8.3.636"', 1).replace("versionCode = 721", "versionCode = 722", 1))

# Make Ben+AI a real full-screen child of the QuizActivity content root rather than a
# separate Dialog window. Preserve the quiz view tree, hide it while chat is active,
# and restore it on close/back so the quiz ViewModel and scroll/answer state stay alive.
p = one("QuizActivity.kt")
s = p.read_text()
field_anchor = "    private lateinit var content: LinearLayout"
fields = """    private var benAiPanelRoot: View? = null
    private var benAiPanelClose: (() -> Unit)? = null
    private var benAiBackCallback: androidx.activity.OnBackPressedCallback? = null
    private var benAiPanelOriginalVisibility: List<Pair<View, Int>> = emptyList()

    internal fun mountBenAiPanelRoot(panel: View, onClose: () -> Unit) {
        val host = findViewById<ViewGroup>(android.R.id.content)
            ?: throw IllegalStateException("Quiz content root is unavailable")
        benAiPanelClose?.invoke()
        benAiPanelOriginalVisibility = (0 until host.childCount).map { index ->
            val child = host.getChildAt(index)
            child to child.visibility
        }
        benAiPanelOriginalVisibility.forEach { (view, _) -> view.visibility = View.GONE }
        benAiPanelRoot = panel
        benAiPanelClose = onClose
        benAiBackCallback?.remove()
        benAiBackCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                benAiPanelClose?.invoke()
            }
        }.also { onBackPressedDispatcher.addCallback(this, it) }
        host.addView(panel, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        panel.isFocusableInTouchMode = true
        panel.requestFocus()
        panel.requestApplyInsets()
    }

    internal fun unmountBenAiPanelRoot(panel: View) {
        if (benAiPanelRoot !== panel) return
        val host = findViewById<ViewGroup>(android.R.id.content)
        benAiPanelRoot = null
        benAiPanelClose = null
        benAiBackCallback?.remove()
        benAiBackCallback = null
        host?.removeView(panel)
        benAiPanelOriginalVisibility.forEach { (view, visibility) ->
            if (view.parent === host) view.visibility = visibility
        }
        benAiPanelOriginalVisibility = emptyList()
    }

    override fun onBackPressed() {
        val closePanel = benAiPanelClose
        if (closePanel != null) {
            closePanel.invoke()
            return
        }
        super.onBackPressed()
    }

"""
if field_anchor not in s: raise SystemExit("[636] QuizActivity field anchor missing")
s = s.replace(field_anchor, fields + field_anchor, 1)
destroy_anchor = """    override fun onDestroy() {
        imageExecutor.close(); prefetchExecutor.close(); quizTimers.close()
        super.onDestroy()
    }"""
destroy_new = """    override fun onDestroy() {
        runCatching { benAiPanelClose?.invoke() }
        imageExecutor.close(); prefetchExecutor.close(); quizTimers.close()
        super.onDestroy()
    }"""
if destroy_anchor not in s: raise SystemExit("[636] QuizActivity onDestroy anchor missing")
s = s.replace(destroy_anchor, destroy_new, 1)
p.write_text(s)

# Replace Dialog lifecycle with an idempotent root mount/unmount lifecycle.
p = one("BenQuestionAiContextDialog.kt")
s = p.read_text()
s = s.replace("import android.app.Dialog\n", "", 1)
if "        val dialog = Dialog(activity)\n" not in s: raise SystemExit("[636] Ben+AI Dialog construction anchor missing")
s = s.replace("        val dialog = Dialog(activity)\n", "", 1)

dismiss_block = """        dialog.setOnDismissListener {
            chatGeneration++
            activeAiJob?.cancel()
            activeAiJob = null
            runCatching { web.stopLoading() }
            runCatching { web.loadUrl("about:blank") }
            runCatching { (web.parent as? android.view.ViewGroup)?.removeView(web) }
            runCatching { web.removeAllViews() }
            runCatching { web.destroy() }
            activeChromeController = null
        }
"""
if dismiss_block not in s: raise SystemExit("[636] Dialog dismiss cleanup anchor missing")
s = s.replace(dismiss_block, "", 1)

close_anchor = '        val close = action("CLOSE") { dialog.dismiss() }'
close_new = """        var panelClosed = false
        fun closePanel() {
            if (panelClosed) return
            panelClosed = true
            activity.unmountBenAiPanelRoot(root)
            chatGeneration++
            activeAiJob?.cancel()
            activeAiJob = null
            runCatching { web.stopLoading() }
            runCatching { web.loadUrl("about:blank") }
            runCatching { (web.parent as? android.view.ViewGroup)?.removeView(web) }
            runCatching { web.removeAllViews() }
            runCatching { web.destroy() }
            activeChromeController = null
        }
        val close = action("CLOSE") { closePanel() }"""
if close_anchor not in s: raise SystemExit("[636] Ben+AI close action anchor missing")
s = s.replace(close_anchor, close_new, 1)

s = s.replace("        dialog.setContentView(root)\n", "", 1)
start = """        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.let { window ->
            // Keep the chat chrome flush with the top edge. The question Activity already owns
            // system-bar presentation; this full-screen dialog should not reserve a second
            // status-bar band above the BEN + AI header.
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            window.attributes = window.attributes.apply { x = 0; y = 0 }
            window.decorView.setPadding(0, 0, 0, 0)
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(GradientDrawable().apply { setColor(ThemeManager.dialogBg(activity)); cornerRadius = 0f })
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.setDimAmount(if (ThemeManager.isDark(activity)) 0.24f else 0.10f)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        dialog.show()
        dialog.window?.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
"""
if start not in s: raise SystemExit("[636] Dialog window/fullscreen block anchor missing")
s = s.replace(start, """        // This is attached to android.R.id.content above the quiz tree, not rendered in a
        // Dialog window. Its MATCH_PARENT root receives keyboard/navigation insets directly.
        activity.mountBenAiPanelRoot(root, ::closePanel)
""", 1)
if "Dialog(activity)" in s or "dialog.window" in s or "dialog.setContentView" in s or "dialog.setOnDismissListener" in s: raise SystemExit("[636] legacy Ben+AI Dialog window reference remains")
p.write_text(s)

# Guard the discovered-source chain: every CI build must apply 636 after the validated 635 layer.
print("[636] applied v8.3.636 / versionCode 722")
print("[636] Ben+AI now mounts as a MATCH_PARENT child of QuizActivity's root content; back/close restores the original quiz tree and releases WebView/AI resources")
