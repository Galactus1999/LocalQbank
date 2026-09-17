package com.localqbank.library

import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity

/** Launcher-only splash. MainActivity remains responsible for the actual app startup. */
class RovexSplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        SystemUi.immersive(this)

        val splash = RovexSplashView(this)
        setContentView(splash)
        splash.setOnFinished { openMain() }

        // A recreation (for example, configuration change) should never trap the user
        // in the splash animation. The normal fresh launcher path still gets the full effect.
        if (savedInstanceState != null) splash.skipToEnd() else splash.start()
    }

    private fun openMain() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        })
        overridePendingTransition(0, 0)
        finish()
        overridePendingTransition(0, 0)
    }
}
