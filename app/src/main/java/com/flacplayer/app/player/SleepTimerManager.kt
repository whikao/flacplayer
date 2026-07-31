package com.flacplayer.app.player

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SleepTimerManager(private val onTimerFinish: () -> Unit) {

    private var timer: CountDownTimer? = null

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs

    val isActive: Boolean get() = _remainingMs.value > 0L

    /** minutes: 1..59999 */
    fun start(minutes: Int) {
        cancel()
        val total = minutes.coerceIn(1, 59999) * 60_000L
        timer = object : CountDownTimer(total, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingMs.value = millisUntilFinished
            }

            override fun onFinish() {
                _remainingMs.value = 0L
                onTimerFinish()
            }
        }.start()
    }

    fun cancel() {
        timer?.cancel()
        timer = null
        _remainingMs.value = 0L
    }
}
