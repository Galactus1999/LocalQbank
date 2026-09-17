package com.localqbank.library

import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Shared edge-to-edge policy for secondary/fullscreen screens. The status bar is hidden so
 * app bars can occupy the real top edge; the quiz screen intentionally uses its own visible-bar
 * policy because the question header must remain cutout-safe. */
object SystemUi {
    fun immersive(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val controller=WindowCompat.getInsetsController(activity.window,activity.window.decorView)
        controller.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.show(WindowInsetsCompat.Type.navigationBars())
        controller.isAppearanceLightStatusBars=!ThemeManager.isDark(activity)
        controller.isAppearanceLightNavigationBars=!ThemeManager.isDark(activity)
        val content=activity.findViewById<View>(android.R.id.content)
        if(content!=null){
            AdaptiveLayoutManager.install(
                activity, content, topExtraDp=0, bottomExtraDp=0,
                keepStatusBarVisible=false, protectDisplayCutout=false
            )
        }
    }
    fun padTopInset(view:View,baseTopDp:Int,baseBottomDp:Int,baseStartDp:Int,baseEndDp:Int){
        val d=view.resources.displayMetrics.density;val top=(baseTopDp*d).toInt();val bottom=(baseBottomDp*d).toInt();val start=(baseStartDp*d).toInt();val end=(baseEndDp*d).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(view){v,insets->val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout());v.setPadding(start,top+safe.top,end,bottom);insets};ViewCompat.requestApplyInsets(view)
    }
    fun padBottomInset(view:View,baseTopDp:Int,baseBottomDp:Int){
        val d=view.resources.displayMetrics.density;val top=(baseTopDp*d).toInt();val bottom=(baseBottomDp*d).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(view){v,insets->val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout());v.setPadding(v.paddingLeft,top,v.paddingRight,bottom+safe.bottom);insets};ViewCompat.requestApplyInsets(view)
    }
}
