package com.autoclicker.detector

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Detects whether a new page has appeared by comparing
 * the previous frame to the current frame using pixel diff.
 */
class PageChangeDetector(
    private val changeThresholdPercent: Float = 15f  // % of pixels that must change
) {
    private var previousFrameMat: Mat? = null

    /**
     * Feed a new frame. Returns true if a significant page change is detected.
     */
    fun hasPageChanged(newFrame: Bitmap): Boolean {
        val newMat = Mat()
        Utils.bitmapToMat(newFrame, newMat)

        // Convert to grayscale for diff
        val newGray = Mat()
        Imgproc.cvtColor(newMat, newGray, Imgproc.COLOR_RGBA2GRAY)
        newMat.release()

        val prev = previousFrameMat
        if (prev == null || prev.size() != newGray.size()) {
            // First frame or size changed → treat as a page change
            prev?.release()
            previousFrameMat = newGray
            return prev != null  // true if size changed (new page), false on very first frame
        }

        // Absolute difference
        val diffMat = Mat()
        Core.absdiff(prev, newGray, diffMat)

        // Threshold to binary: pixel counted if diff > 30
        val binaryMat = Mat()
        Imgproc.threshold(diffMat, binaryMat, 30.0, 255.0, Imgproc.THRESH_BINARY)
        diffMat.release()

        // Count changed pixels
        val nonZero = Core.countNonZero(binaryMat)
        binaryMat.release()

        val totalPixels = newGray.rows() * newGray.cols()
        val changePercent = (nonZero.toFloat() / totalPixels) * 100f

        // Update previous frame
        prev.release()
        previousFrameMat = newGray

        return changePercent >= changeThresholdPercent
    }

    /**
     * Reset detector (call when starting a new session).
     */
    fun reset() {
        previousFrameMat?.release()
        previousFrameMat = null
    }
}
