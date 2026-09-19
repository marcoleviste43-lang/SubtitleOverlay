package com.subtitleoverlay.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.subtitleoverlay.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedUri: Uri? = null

    private val pickSubtitleLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Not every provider supports persistable grants; a one-shot read still works.
                }
                selectedUri = uri
                binding.tvFileName.text = uri.lastPathSegment ?: uri.toString()
                updateStartButtonEnabled()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnPickFile.setOnClickListener {
            pickSubtitleLauncher.launch(arrayOf("*/*"))
        }
        binding.btnStart.setOnClickListener { startOverlay() }
        binding.btnStop.setOnClickListener {
            val intent = Intent(this, SubtitleOverlayService::class.java).apply {
                action = SubtitleOverlayService.ACTION_STOP
            }
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayStatus()
        updateStartButtonEnabled()
    }

    private fun refreshOverlayStatus() {
        val granted = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = getString(
            if (granted) R.string.overlay_permission_granted
            else R.string.overlay_permission_missing
        )
        binding.btnGrantOverlay.isEnabled = !granted
    }

    private fun updateStartButtonEnabled() {
        binding.btnStart.isEnabled = selectedUri != null && Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startOverlay() {
        val uri = selectedUri ?: return
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, SubtitleOverlayService::class.java).apply {
            action = SubtitleOverlayService.ACTION_START
            putExtra(SubtitleOverlayService.EXTRA_URI, uri.toString())
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
