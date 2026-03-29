package com.autoclicker.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autoclicker.AutoClickerState
import com.autoclicker.databinding.ActivityMainBinding
import com.autoclicker.service.AutoClickAccessibilityService
import com.autoclicker.service.ScreenCaptureService
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val pickImageRequest = 101
    private val mediaProjectionRequest = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init OpenCV
        OpenCVLoader.initLocal()

        setupSliders()
        setupButtons()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    // ── Permission checks ────────────────────────────────────────────────────

    private fun checkPermissions() {
        val accessOk = AutoClickAccessibilityService.isEnabled()
        val overlayOk = Settings.canDrawOverlays(this)

        binding.tvAccessibilityWarning.visibility =
            if (accessOk) android.view.View.GONE else android.view.View.VISIBLE
        binding.tvOverlayWarning.visibility =
            if (overlayOk) android.view.View.GONE else android.view.View.VISIBLE

        binding.tvAccessibilityWarning.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.tvOverlayWarning.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        binding.btnStart.isEnabled = accessOk && overlayOk
    }

    // ── Sliders ──────────────────────────────────────────────────────────────

    private fun setupSliders() {
        binding.seekThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = (progress + 1) / 100f   // 0.01 – 1.00
                AutoClickerState.matchThreshold = value
                binding.tvThresholdLabel.text = "Match Threshold: %.2f".format(value)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = (progress + 500L)   // 500 – 10 000 ms
                AutoClickerState.clickIntervalMs = ms
                binding.tvIntervalLabel.text = "Click Interval: $ms ms"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.seekMaxClicks.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val max = progress + 1  // 1 – 200
                AutoClickerState.maxClicks = max
                binding.tvMaxClicksLabel.text = "Max Clicks: $max"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.switchContinueOnChange.setOnCheckedChangeListener { _, isChecked ->
            AutoClickerState.continueOnPageChange = isChecked
        }
    }

    // ── Buttons ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, pickImageRequest)
        }

        binding.btnStart.setOnClickListener {
            if (AutoClickerState.targetBitmap == null) {
                binding.tvStatus.text = "Please pick a target image first!"
                return@setOnClickListener
            }
            requestScreenCapturePermission()
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            AutoClickerState.setRunning(false)
            AutoClickerState.setStatus("Stopped by user")
            updateRunningUI(false)
        }
    }

    private fun requestScreenCapturePermission() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), mediaProjectionRequest)
    }

    // ── State observers ──────────────────────────────────────────────────────

    private fun observeState() {
        lifecycleScope.launch {
            AutoClickerState.statusMessage.collect { msg ->
                binding.tvStatus.text = "Status: $msg"
            }
        }
        lifecycleScope.launch {
            AutoClickerState.clickCount.collect { count ->
                binding.tvClickCount.text = "Clicks: $count"
            }
        }
        lifecycleScope.launch {
            AutoClickerState.pageChangeCount.collect { count ->
                binding.tvPageChange.text = "Page changes: $count"
            }
        }
        lifecycleScope.launch {
            AutoClickerState.lastMatchScore.collect { score ->
                if (score > 0f)
                    binding.tvLastMatch.text = "Last match: ${"%.1f".format(score * 100)}%"
            }
        }
        lifecycleScope.launch {
            AutoClickerState.isRunning.collect { running ->
                updateRunningUI(running)
            }
        }
    }

    private fun updateRunningUI(running: Boolean) {
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled = running
        binding.btnPickImage.isEnabled = !running
    }

    // ── Activity results ─────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            pickImageRequest -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val uri = data.data ?: return
                    val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    AutoClickerState.targetBitmap = bmp
                    binding.ivTargetPreview.setImageBitmap(bmp)
                    binding.tvStatus.text = "Status: Target image loaded"
                }
            }

            mediaProjectionRequest -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    AutoClickerState.reset()
                    val serviceIntent = ScreenCaptureService.buildIntent(this, resultCode, data)
                    startForegroundService(serviceIntent)
                    updateRunningUI(true)
                } else {
                    binding.tvStatus.text = "Status: Screen permission denied"
                }
            }
        }
    }
}
