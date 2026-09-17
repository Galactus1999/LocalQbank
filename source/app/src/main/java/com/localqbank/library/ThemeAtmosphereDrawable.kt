package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.sin

/**
 * Lightweight procedural atmosphere for the two cinematic dark themes.
 * No copyrighted movie artwork/assets are bundled. The Avatar theme is Pandora-inspired:
 * deep indigo/blue, cyan bioluminescence and a warm celestial body.
 */
class ThemeAtmosphereDrawable(private val context: Context) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = Array(72) { i ->
        val x = ((i * 37) % 101) / 100f
        val y = ((i * 61 + 17) % 101) / 100f
        val r = 0.45f + ((i * 13) % 9) / 10f
        Triple(x, y, r)
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        val theme = ThemeManager.get(context)
        when (theme) {
            ThemeManager.COSMOS -> drawCosmos(canvas, w, h)
            ThemeManager.AVATAR -> drawAvatar(canvas, w, h)
            else -> {
                paint.shader = null
                paint.color = ThemeManager.bg(context)
                canvas.drawRect(0f, 0f, w, h, paint)
            }
        }
    }

    private fun drawCosmos(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, w, h,
            Color.BLACK, Color.rgb(4, 7, 18), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * .76f, h * .18f, w * .55f,
            intArrayOf(Color.argb(72, 64, 38, 150), Color.argb(22, 22, 76, 145), Color.TRANSPARENT),
            floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(w * .76f, h * .18f, w * .55f, paint)
        paint.shader = RadialGradient(w * .12f, h * .82f, w * .42f,
            intArrayOf(Color.argb(50, 20, 102, 145), Color.argb(12, 74, 35, 120), Color.TRANSPARENT),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(w * .12f, h * .82f, w * .42f, paint)
        drawStars(canvas, w, h, Color.argb(210, 225, 235, 255))
        planet(canvas, w * .86f, h * .72f, minOf(w, h) * .09f, Color.rgb(105, 74, 176), Color.rgb(25, 19, 56))
        planet(canvas, w * .18f, h * .14f, minOf(w, h) * .045f, Color.rgb(71, 142, 201), Color.rgb(13, 39, 70))
    }

    private fun drawAvatar(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            Color.rgb(3, 7, 25), Color.rgb(5, 25, 45), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * .72f, h * .18f, w * .62f,
            intArrayOf(Color.argb(68, 22, 125, 177), Color.argb(20, 63, 57, 153), Color.TRANSPARENT),
            floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(w * .72f, h * .18f, w * .62f, paint)
        drawStars(canvas, w, h, Color.argb(150, 145, 235, 255))
        planet(canvas, w * .83f, h * .14f, minOf(w, h) * .12f, Color.rgb(50, 107, 148), Color.rgb(9, 37, 58))
        paint.shader = RadialGradient(w * .22f, h * .76f, minOf(w, h) * .11f,
            intArrayOf(Color.argb(105, 64, 235, 211), Color.argb(26, 28, 150, 161), Color.TRANSPARENT),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(w * .22f, h * .76f, minOf(w, h) * .11f, paint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float, color: Int) {
        paint.shader = null
        paint.color = color
        stars.forEachIndexed { i, star ->
            val pulse = 0.78f + 0.22f * sin((i * 1.7f) + w * .0002f).toFloat()
            canvas.drawCircle(star.first * w, star.second * h, star.third * pulse, paint)
        }
    }

    private fun planet(canvas: Canvas, x: Float, y: Float, r: Float, light: Int, dark: Int) {
        paint.shader = RadialGradient(x - r * .32f, y - r * .34f, r * 1.1f,
            intArrayOf(Color.WHITE, light, dark), floatArrayOf(0f, .18f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * .045f
        paint.color = Color.argb(70, 230, 245, 255)
        canvas.drawOval(x - r * 1.45f, y - r * .30f, x + r * 1.45f, y + r * .30f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
