package com.localqbank.library

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/** Central responsive layout policy for phones, tablets, foldables and split-screen windows. */
object AdaptiveLayoutManager {
    enum class WindowClass { COMPACT, MEDIUM, EXPANDED }
    data class Metrics(val widthPx:Int, val heightPx:Int, val safeTop:Int, val safeBottom:Int, val safeLeft:Int, val safeRight:Int, val windowClass:WindowClass)

    fun install(activity: Activity, root: View, topExtraDp:Int = 0, bottomExtraDp:Int = 0, keepStatusBarVisible:Boolean = true,
                protectDisplayCutout:Boolean = true,
                onMetrics:(Metrics)->Unit = {}) {
        if (keepStatusBarVisible) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val insetTypes = if (protectDisplayCutout) WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() else WindowInsetsCompat.Type.systemBars()
            val bars = insets.getInsets(insetTypes)
            val left = bars.left
            val right = bars.right
            val top = bars.top + dp(v,topExtraDp)
            val bottom = bars.bottom + dp(v,bottomExtraDp)
            v.setTag(R.id.adaptiveSafeTop, top)
            v.setTag(R.id.adaptiveSafeBottom, bottom)
            // Root containers get the live safe area. This is intentionally calculated from
            // insets rather than a hard-coded status-bar height, so cutouts/foldables/split
            // windows cannot push critical controls underneath system UI.
            if (v is ViewGroup) v.setPadding(v.paddingLeft, top, v.paddingRight, bottom)
            val wm=v.context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val b=Rect(); v.getWindowVisibleDisplayFrame(b)
            val (width,height)=if(android.os.Build.VERSION.SDK_INT>=30){
                val bounds=wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            }else{
                max(v.width,b.width()) to max(v.height,b.height())
            }
            val wc=when { width < dp(v,600) -> WindowClass.COMPACT; width < dp(v,840) -> WindowClass.MEDIUM; else -> WindowClass.EXPANDED }
            v.setTag(R.id.adaptiveWindowClass, wc.name)
            onMetrics(Metrics(width,height,top,bottom,left,right,wc))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun headlineTopDp():Int = 32 // approximately 5 mm at mdpi; scales with density through dp().
    fun dp(view:View, value:Int):Int=(value*view.resources.displayMetrics.density).toInt()
}
