package com.subtitleoverlay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SubtitleOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.subtitleoverlay.app.action.START"
        const val ACTION_STOP = "com.subtitleoverlay.app.action.STOP"
        const val EXTRA_URI = "extra_uri"

        private const val CHANNEL_ID = "subtitle_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 150L
        private const val MIN_FONT_SP = 10f
        private const val MAX_FONT_SP = 40f
        private const val MIN_ALPHA = 40
        private const val MAX_ALPHA = 255
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private var cues: List<Cue> = emptyList()
    private var currentCueIndex = -1

    private var fontSizeSp = 18f
    private var backgroundAlpha = 160
    private var offsetMs = 0L

    // Internal playback clock, independent of any real media player.
    // "accumulatedPlayedMs" is how far along the subtitle track we are.
    private var playbackStartRealtime = 0L
    private var accumulatedPlayedMs = 0L
    private var isPlaying = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvSubtitle: TextView
    private lateinit var controlsPanel: View

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateSubtitle()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                intent.getStringExtra(EXTRA_URI)?.let { loadSubtitles(Uri.parse(it)) }
                if (overlayView == null) {
                    createOverlay()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // View was already detached; nothing to clean up.
            }
        }
        overlayView = null
    }

    // ---- Subtitle loading & clock -----------------------------------------

    private fun loadSubtitles(uri: Uri) {
        cues = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val isVtt = uri.lastPathSegment?.lowercase()?.endsWith(".vtt") == true
                SubtitleParser.parse(BufferedReader(InputStreamReader(input)), isVtt)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        currentCueIndex = -1
        tvSubtitle.let { it.text = "" }
        resetClock()
    }

    private fun resetClock() {
        playbackStartRealtime = SystemClock.elapsedRealtime()
        accumulatedPlayedMs = 0L
        isPlaying = true
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    private fun currentPositionMs(): Long {
        val played = if (isPlaying) {
            accumulatedPlayedMs + (SystemClock.elapsedRealtime() - playbackStartRealtime)
        } else {
            accumulatedPlayedMs
        }
        return played + offsetMs
    }

    private fun updateSubtitle() {
        if (cues.isEmpty()) return
        val pos = currentPositionMs()
        val idx = cues.indexOfFirst { pos in it.startMs..it.endMs }
        if (idx != currentCueIndex) {
            currentCueIndex = idx
            tvSubtitle.text = if (idx >= 0) cues[idx].text else ""
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            accumulatedPlayedMs += SystemClock.elapsedRealtime() - playbackStartRealtime
            isPlaying = false
        } else {
            playbackStartRealtime = SystemClock.elapsedRealtime()
            isPlaying = true
        }
    }

    /** Nudge the internal playback position, e.g. to line up with the video after a seek. */
    private fun seekBy(deltaMs: Long) {
        if (isPlaying) {
            accumulatedPlayedMs += SystemClock.elapsedRealtime() - playbackStartRealtime
            playbackStartRealtime = SystemClock.elapsedRealtime()
        }
        accumulatedPlayedMs = max(0L, accumulatedPlayedMs + deltaMs)
        updateSubtitle()
    }

    /** Fine sync correction against the externally-playing video, without resetting position. */
    private fun adjustOffset(deltaMs: Long) {
        offsetMs += deltaMs
        updateSubtitle()
    }

    private fun adjustFontSize(deltaSp: Float) {
        fontSizeSp = min(MAX_FONT_SP, max(MIN_FONT_SP, fontSizeSp + deltaSp))
        tvSubtitle.textSize = fontSizeSp
    }

    private fun adjustOpacity(delta: Int) {
        backgroundAlpha = min(MAX_ALPHA, max(MIN_ALPHA, backgroundAlpha + delta))
        applyBackgroundAlpha()
    }

    private fun applyBackgroundAlpha() {
        (overlayView?.findViewById<View>(R.id.overlayRoot)?.background as? GradientDrawable)
            ?.alpha = backgroundAlpha
    }

    // ---- Overlay window ----------------------------------------------------

    private fun createOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_layout, null)
        overlayView = view
        tvSubtitle = view.findViewById(R.id.tvSubtitle)
        controlsPanel = view.findViewById(R.id.controlsPanel)
        tvSubtitle.textSize = fontSizeSp

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager.addView(view, params)
        applyBackgroundAlpha()
        setupDrag(view.findViewById(R.id.dragHandle))
        setupControls(view)
    }

    private fun setupDrag(handleView: View) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var moved = false

        handleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        controlsPanel.visibility =
                            if (controlsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupControls(view: View) {
        view.findViewById<View>(R.id.btnPlayPause).setOnClickListener { togglePlayPause() }
        view.findViewById<View>(R.id.btnSkipBack).setOnClickListener { seekBy(-5000) }
        view.findViewById<View>(R.id.btnSkipForward).setOnClickListener { seekBy(5000) }
        view.findViewById<View>(R.id.btnClose).setOnClickListener { stopSelf() }

        view.findViewById<View>(R.id.btnFontDec).setOnClickListener { adjustFontSize(-2f) }
        view.findViewById<View>(R.id.btnFontInc).setOnClickListener { adjustFontSize(2f) }
        view.findViewById<View>(R.id.btnOpacityDec).setOnClickListener { adjustOpacity(-25) }
        view.findViewById<View>(R.id.btnOpacityInc).setOnClickListener { adjustOpacity(25) }

        view.findViewById<View>(R.id.btnOffsetBack).setOnClickListener { adjustOffset(-500) }
        view.findViewById<View>(R.id.btnOffsetForward).setOnClickListener { adjustOffset(500) }
    }

    // ---- Foreground notification --------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Subtitle Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, SubtitleOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_running_notification))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }
}
