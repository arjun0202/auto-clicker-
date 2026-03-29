package com.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.autoclicker.AutoClickerState

/**
 * Accessibility Service that:
 * 1. Provides the ability to dispatch tap gestures anywhere on screen (no root needed).
 * 2. Exposes a singleton reference so other components can call performTap().
 */
class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        /** Live reference to the running service. Null when disabled. */
        var instance: AutoClickAccessibilityService? = null
            private set

        /**
         * Perform a tap at the given screen coordinates.
         * Returns false if the service is not running.
         */
        fun performTap(x: Int, y: Int, onComplete: (() -> Unit)? = null): Boolean {
            val svc = instance ?: return false

            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            svc.dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    AutoClickerState.incrementClickCount()
                    onComplete?.invoke()
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    onComplete?.invoke()
                }
            }, null)
            return true
        }

        /** Check if the service is currently active. */
        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed — we only use gesture dispatch
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
