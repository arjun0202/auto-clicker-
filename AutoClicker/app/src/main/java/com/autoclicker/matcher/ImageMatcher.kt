package com.autoclicker.matcher

import android.graphics.Bitmap
import android.graphics.Point
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Uses OpenCV template matching to find the target image inside a screen frame.
 */
object ImageMatcher {

    /**
     * Searches [frame] for [template].
     * @return Pair(Point, confidence) if found above [threshold], null otherwise.
     */
    fun findTemplate(
        frame: Bitmap,
        template: Bitmap,
        threshold: Float = 0.80f
    ): Pair<Point, Float>? {
        val frameMat = Mat()
        val templateMat = Mat()

        Utils.bitmapToMat(frame, frameMat)
        Utils.bitmapToMat(template, templateMat)

        // Convert to grayscale for faster matching
        val frameGray = Mat()
        val templateGray = Mat()
        Imgproc.cvtColor(frameMat, frameGray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_RGBA2GRAY)

        val resultCols = frameGray.cols() - templateGray.cols() + 1
        val resultRows = frameGray.rows() - templateGray.rows() + 1

        if (resultCols <= 0 || resultRows <= 0) {
            frameGray.release(); templateGray.release()
            frameMat.release(); templateMat.release()
            return null
        }

        val result = Mat(resultRows, resultCols, CvType.CV_32FC1)
        Imgproc.matchTemplate(frameGray, templateGray, result, Imgproc.TM_CCOEFF_NORMED)

        val mmResult = Core.minMaxLoc(result)
        val confidence = mmResult.maxVal.toFloat()
        val matchLoc = mmResult.maxLoc

        // Release all Mats
        result.release()
        frameGray.release()
        templateGray.release()
        frameMat.release()
        templateMat.release()

        return if (confidence >= threshold) {
            // Return center of matched region
            val cx = (matchLoc.x + templateGray.cols() / 2).toInt()
            val cy = (matchLoc.y + templateGray.rows() / 2).toInt()
            Pair(Point(cx, cy), confidence)
        } else {
            null
        }
    }

    /**
     * Scale-aware search: tries multiple scales of the template.
     * Useful when screen DPI differs from the template source.
     */
    fun findTemplateMultiScale(
        frame: Bitmap,
        template: Bitmap,
        threshold: Float = 0.80f,
        scales: List<Float> = listOf(0.7f, 0.85f, 1.0f, 1.15f, 1.3f)
    ): Pair<Point, Float>? {
        var bestResult: Pair<Point, Float>? = null
        var bestScore = 0f

        for (scale in scales) {
            val scaledTemplate = Bitmap.createScaledBitmap(
                template,
                (template.width * scale).toInt().coerceAtLeast(1),
                (template.height * scale).toInt().coerceAtLeast(1),
                true
            )
            val result = findTemplate(frame, scaledTemplate, threshold)
            if (result != null && result.second > bestScore) {
                bestScore = result.second
                bestResult = result
            }
            if (scaledTemplate != template) scaledTemplate.recycle()
        }

        return bestResult
    }
}
