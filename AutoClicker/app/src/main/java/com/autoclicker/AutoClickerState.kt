package com.autoclicker

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton holding shared state between UI, services, and detectors.
 */
object AutoClickerState {

    // Target image the user wants to find on screen
    var targetBitmap: Bitmap? = null

    // Settings
    var matchThreshold: Float = 0.80f
    var clickIntervalMs: Long = 1000L
    var maxClicks: Int = 50
    var continueOnPageChange: Boolean = true

    // Runtime counters
    private val _clickCount = MutableStateFlow(0)
    val clickCount: StateFlow<Int> = _clickCount

    private val _pageChangeCount = MutableStateFlow(0)
    val pageChangeCount: StateFlow<Int> = _pageChangeCount

    private val _statusMessage = MutableStateFlow("Idle")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _lastMatchScore = MutableStateFlow(0f)
    val lastMatchScore: StateFlow<Float> = _lastMatchScore

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    fun incrementClickCount() {
        _clickCount.value++
    }

    fun incrementPageChangeCount() {
        _pageChangeCount.value++
    }

    fun setStatus(msg: String) {
        _statusMessage.value = msg
    }

    fun setLastMatchScore(score: Float) {
        _lastMatchScore.value = score
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun reset() {
        _clickCount.value = 0
        _pageChangeCount.value = 0
        _statusMessage.value = "Idle"
        _lastMatchScore.value = 0f
        _isRunning.value = false
    }
}
