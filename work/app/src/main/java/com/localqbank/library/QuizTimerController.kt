package com.localqbank.library

import android.os.CountDownTimer

/**
 * Presentation-only timer coordinator for QuizActivity.
 *
 * It owns Android CountDownTimer lifecycle mechanics, but not quiz/session state.
 * State remains owned by QuizViewModel; callbacks report timer changes back to the UI.
 */
class QuizTimerController(
    private val onExamTick: (Long) -> Unit,
    private val onExamFinish: () -> Unit,
    private val onGuidanceTick: (Long) -> Unit,
    private val onGuidanceFinish: () -> Unit
) : AutoCloseable {
    private var examTimer: CountDownTimer? = null
    private var guidanceTimer: CountDownTimer? = null
    private var closed = false

    fun startExam(durationMs: Long) {
        if (closed || durationMs <= 0L) return
        examTimer?.cancel()
        examTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (!closed) onExamTick(millisUntilFinished)
            }

            override fun onFinish() {
                if (!closed) onExamFinish()
            }
        }.start()
    }

    fun startGuidance(durationMs: Long) {
        if (closed || durationMs <= 0L) return
        guidanceTimer?.cancel()
        guidanceTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (!closed) onGuidanceTick(millisUntilFinished)
            }

            override fun onFinish() {
                if (!closed) onGuidanceFinish()
            }
        }.start()
    }

    fun cancelExam() {
        examTimer?.cancel()
        examTimer = null
    }

    fun cancelGuidance() {
        guidanceTimer?.cancel()
        guidanceTimer = null
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelExam()
        cancelGuidance()
    }
}
