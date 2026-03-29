package com.autoclicker.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.autoclicker.AutoClickerState
import com.autoclicker.detector.PageChangeDetector
import com.autoclicker.matcher.ImageMatcher
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val CHANNEL_ID = "autoclicker_channel"
        const val NOTIF_ID = 1001

        fun buildIntent(context: Context, resultCode: Int, data: Intent): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var pageChangeDetector: PageChangeDetector
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        pageChangeDetector = PageChangeDetector(changeThresholdPercent = 12f)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: return START_NOT_STICKY
        val resultData: Intent = intent.getParcelableExtra(EXTRA_RESULT_DATA)
            ?: return START_NOT_STICKY

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        setupVirtualDisplay()
        startClickLoop()

        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AutoClickerCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun startClickLoop() {
        AutoClickerState.setRunning(true)
        AutoClickerState.setStatus("Running…")
        pageChangeDetector.reset()

        serviceScope.launch {
            var clicksDone = 0
            var waitingForPageChange = false

            while (isActive && AutoClickerState.isRunning.value) {

                if (clicksDone >= AutoClickerState.maxClicks) {
                    AutoClickerState.setStatus("Done — max clicks reached")
                    break
                }

                val frame = captureScreen() ?: run {
                    delay(200)
                    null
                } ?: continue

                val target = AutoClickerState.targetBitmap
                if (target == null) {
                    AutoClickerState.setStatus("No target image set")
                    delay(500)
                    continue
                }

                if (waitingForPageChange) {
                    // Check if page has changed
                    val changed = pageChangeDetector.hasPageChanged(frame)
                    if (changed) {
                        AutoClickerState.incrementPageChangeCount()
                        AutoClickerState.setStatus("Page changed — continuing…")
                        waitingForPageChange = false
                        delay(300) // Small settle delay after page change
                    } else {
                        AutoClickerState.setStatus("Waiting for page change…")
                        delay(200)
                    }
                    continue
                }

                // Try to find target on screen
                val matchResult = ImageMatcher.findTemplateMultiScale(
                    frame, target, AutoClickerState.matchThreshold
                )

                if (matchResult != null) {
                    val (point, score) = matchResult
                    AutoClickerState.setLastMatchScore(score)
                    AutoClickerState.setStatus("Match found (${(score * 100).toInt()}%) — clicking…")

                    // Perform the tap via accessibility service
                    withContext(Dispatchers.Main) {
                        AutoClickAccessibilityService.performTap(point.x, point.y)
                    }
                    clicksDone++

                    // Wait for page change after click
                    delay(AutoClickerState.clickIntervalMs)
                    waitingForPageChange = true

                } else {
                    AutoClickerState.setStatus("Searching for target…")
                    delay(300)
                }
            }

            AutoClickerState.setRunning(false)
            AutoClickerState.setStatus("Stopped")
            stopSelf()
        }
    }

    private fun captureScreen(): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        return try {
            image = reader.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bmp = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)

            // Crop to exact screen size (removes row padding)
            val cropped = Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
            bmp.recycle()
            cropped
        } catch (e: Exception) {
            null
        } finally {
            image?.close()
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoClicker Running")
            .setContentText("Tap detection active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AutoClicker",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "AutoClicker foreground service" }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        AutoClickerState.setRunning(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
