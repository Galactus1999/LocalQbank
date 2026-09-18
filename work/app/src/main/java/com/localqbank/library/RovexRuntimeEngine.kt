package com.localqbank.library

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Frame-aware runtime governor inspired by game-engine scheduling, but kept native to Android.
 * It does not replace Android's renderer/OpenGL/Vulkan stack and owns no study/database rules.
 * Its job is to protect the foreground frame budget by reducing background CPU concurrency
 * when the device is already missing frames or is under memory pressure.
 */
class RovexRuntimeEngine(context: Context) : ComponentCallbacks2 {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val frameMs = AtomicLong(16L)
    private val pressure = AtomicInteger(0) // 0 normal, 1 busy, 2 saturated
    private val trimLevel = AtomicInteger(ComponentCallbacks2.TRIM_MEMORY_COMPLETE + 1)
    @Volatile private var started = false

    private val frameCallback = object : Choreographer.FrameCallback {
        private var lastNs = 0L
        private var busyFrames = 0
        private var saturatedFrames = 0
        override fun doFrame(frameTimeNanos: Long) {
            if (lastNs != 0L) {
                val ms = ((frameTimeNanos - lastNs) / 1_000_000L).coerceAtLeast(1L)
                frameMs.set(ms)
                when {
                    ms >= 45L -> { saturatedFrames++; busyFrames++; }
                    ms >= 24L -> { busyFrames++; saturatedFrames = 0 }
                    else -> { busyFrames = 0; saturatedFrames = 0 }
                }
                pressure.set(when {
                    saturatedFrames >= 2 -> 2
                    busyFrames >= 2 -> 1
                    else -> 0
                })
            }
            lastNs = frameTimeNanos
            if (started) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        app.registerComponentCallbacks(this)
        started = true
        main.post { Choreographer.getInstance().postFrameCallback(frameCallback) }
    }

    /** Maximum simultaneous background jobs that should compete with the foreground. */
    fun recommendedBackgroundWorkers(): Int = when {
        pressure.get() >= 2 || trimLevel.get() >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> 1
        pressure.get() == 1 || trimLevel.get() >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> 1
        AppManagers.isReady() && AppManagers.battery.recommendedPrefetchDepth() <= 1 -> 1
        else -> 2
    }

    fun framePressure(): Int = pressure.get()
    fun lastFrameMs(): Long = frameMs.get()

    /**
     * Applies conservative RecyclerView tuning. It never forces a nested scrolling hierarchy
     * or a heavyweight cache; the caller remains responsible for the screen-level layout.
     */
    fun configureRecyclerView(rv: RecyclerView, fixedSize: Boolean) {
        rv.itemAnimator = null
        rv.setHasFixedSize(fixedSize)
        rv.isNestedScrollingEnabled = true
        rv.setItemViewCacheSize(if (pressure.get() == 0) 4 else 2)
        rv.recycledViewPool.setMaxRecycledViews(0, if (pressure.get() == 0) 12 else 6)
        rv.recycledViewPool.setMaxRecycledViews(1, if (pressure.get() == 0) 12 else 6)
        rv.setItemViewCacheSize(if (recommendedBackgroundWorkers() > 1) 4 else 2)
    }

    /**
     * Keeps custom Canvas animation on Android's hardware renderer when available. We do not
     * attempt to "overclock" the GPU; Android owns GPU scheduling. The useful optimization is
     * avoiding unnecessary software rendering/offscreen composition for the animation surface.
     */
    fun configureAnimatedSurface(view: View) {
        if (view.isHardwareAccelerated && pressure.get() < 2) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        view.forceHasOverlappingRendering(false)
    }
    fun shutdown() {
        if (!started) return
        started = false
        main.post { Choreographer.getInstance().removeFrameCallback(frameCallback) }
        runCatching { app.unregisterComponentCallbacks(this) }
    }

    override fun onTrimMemory(level: Int) { trimLevel.set(level) }
    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() { trimLevel.set(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) }
}
