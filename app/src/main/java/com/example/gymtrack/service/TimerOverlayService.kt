package com.example.gymtrack.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.view.TouchDelegate
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.gymtrack.MainActivity
import com.example.gymtrack.R

class TimerOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: android.view.View? = null
    private var timer: CountDownTimer? = null
    private var remainingMs: Long = 0L
    private lateinit var timerText: TextView
    private lateinit var topButtonsRow: View
    private lateinit var controlsRow: View
    private lateinit var hintText: TextView
    private lateinit var minimizeButton: ImageButton
    private lateinit var closeButton: ImageButton
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var isMinimized: Boolean = false
    private lateinit var prefs: SharedPreferences
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_DARK_MODE) overlayView?.post { applyTimerTheme() }
    }

    override fun onCreate() {
        super.onCreate()
        remainingMs = getConfiguredMs()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Temporizador activo"))
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_AUTOSTART, false) == true) {
            overlayView?.post {
                if (remainingMs <= 0L) remainingMs = getConfiguredMs()
                startTimer()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        timer?.cancel()
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        overlayView?.let { windowManager.removeView(it) }
        stopSound()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var dragStartX = 0
    private var dragStartY = 0
    private var dragRawStartX = 0f
    private var dragRawStartY = 0f
    private lateinit var dragTouchListener: View.OnTouchListener

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        isMinimized = prefs.getBoolean(KEY_TIMER_MINIMIZED, false)

        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_GymTrack)
        val inflater = LayoutInflater.from(themedContext)

        overlayView = inflater.inflate(R.layout.overlay_timer, null)
        timerText = overlayView!!.findViewById(R.id.timerText)
        topButtonsRow = overlayView!!.findViewById(R.id.topButtonsRow)
        controlsRow = overlayView!!.findViewById(R.id.controlsRow)
        hintText = overlayView!!.findViewById(R.id.hintText)
        minimizeButton = overlayView!!.findViewById(R.id.minimizeButton)
        val playButton: ImageButton  = overlayView!!.findViewById(R.id.playButton)
        val stopButton: ImageButton  = overlayView!!.findViewById(R.id.stopButton)
        val resetButton: ImageButton = overlayView!!.findViewById(R.id.resetButton)
        closeButton = overlayView!!.findViewById(R.id.closeButton)

        applyTimerTheme()
        renderTime()

        playButton.setOnClickListener  { startTimer() }
        stopButton.setOnClickListener  { timer?.cancel(); stopSound() }
        resetButton.setOnClickListener { timer?.cancel(); stopSound(); remainingMs = getConfiguredMs(); renderTime() }
        closeButton.setOnClickListener { stopSelf() }
        minimizeButton.setOnClickListener { setMinimized(!isMinimized, persist = true) }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - 300
            y = 200
        }

        val touchSlopPx = 12f * resources.displayMetrics.density
        var touchMoved = false
        dragTouchListener = View.OnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchMoved = false
                    dragStartX = overlayParams.x
                    dragStartY = overlayParams.y
                    dragRawStartX = e.rawX
                    dragRawStartY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - dragRawStartX
                    val dy = e.rawY - dragRawStartY
                    if (!touchMoved && (dx * dx + dy * dy) > touchSlopPx * touchSlopPx) {
                        touchMoved = true
                    }
                    if (touchMoved) {
                        overlayParams.x = dragStartX + dx.toInt()
                        overlayParams.y = dragStartY + dy.toInt()
                        clampOverlayWithinScreen()
                        windowManager.updateViewLayout(overlayView, overlayParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!touchMoved && isMinimized) setMinimized(false, persist = true)
                    true
                }
                else -> false
            }
        }
        setMinimized(isMinimized, persist = false)
        windowManager.addView(overlayView, overlayParams)
        overlayView?.post {
            if (isMinimized) updateMinimizedTouchDelegate()
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(ms: Long) { remainingMs = ms; renderTime() }
            override fun onFinish() {
                remainingMs = 0
                if (isMinimized) setMinimized(false, persist = true)
                renderTime()
                playFinishFeedback()
            }
        }.start()
    }

    private fun stopSound() {
        try { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null } catch (_: Exception) {}
    }

    private fun getConfiguredMs(): Long {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_TIMER_MS, FALLBACK_MS).coerceIn(5_000L, 60L * 60L * 1000L)
    }

    private fun playFinishFeedback() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val soundUriStr = prefs.getString(KEY_TIMER_SOUND_URI, "") ?: ""
        try {
            stopSound()
            val uri: Uri = if (soundUriStr.isNotBlank()) Uri.parse(soundUriStr)
                           else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                               ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                setDataSource(applicationContext, uri)
                isLooping = false
                prepare(); start()
                setOnCompletionListener { release() }
            }
        } catch (_: Exception) {}

        if (prefs.getBoolean(KEY_TIMER_VIBRATE, true)) {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) } catch (_: Exception) {}
            } else {
                try {
                    @Suppress("DEPRECATION")
                    (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(pattern, -1)
                } catch (_: Exception) {}
            }
        }
    }

    private fun renderTime() {
        val total = remainingMs / 1000
        timerText.text = String.format("%02d:%02d", total / 60, total % 60)
    }

    private fun setMinimized(value: Boolean, persist: Boolean) {
        isMinimized = value
        topButtonsRow.visibility = if (value) View.GONE else View.VISIBLE
        controlsRow.visibility = if (value) View.GONE else View.VISIBLE
        hintText.visibility = if (value) View.GONE else View.VISIBLE
        minimizeButton.contentDescription = if (value) "Expandir" else "Minimizar"
        applyOverlayTouchHandling()
        if (persist) persistOverlayUiPrefs()
    }

    /** Minimizado: hitbox = toda la caja (TouchDelegate), sin cambiar tamaño visual. */
    private fun applyOverlayTouchHandling() {
        val root = overlayView ?: return
        root.touchDelegate = null
        if (isMinimized) {
            timerText.setOnTouchListener(dragTouchListener)
            overlayView?.setOnTouchListener(null)
            root.post { updateMinimizedTouchDelegate() }
        } else {
            timerText.setOnTouchListener(null)
            overlayView?.setOnTouchListener(dragTouchListener)
        }
    }

    private fun updateMinimizedTouchDelegate() {
        val root = overlayView ?: return
        if (!isMinimized || root.width == 0 || root.height == 0) return
        val rect = Rect(0, 0, root.width, root.height)
        root.touchDelegate = TouchDelegate(rect, timerText)
    }

    private fun applyTimerTheme() {
        val darkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        val bg = if (darkMode) Color.BLACK else Color.WHITE
        val fg = if (darkMode) Color.WHITE else Color.BLACK
        val red = Color.rgb(211, 47, 47)

        overlayView?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * resources.displayMetrics.density
            setColor(bg)
            setStroke((3f * resources.displayMetrics.density).toInt(), fg)
        }
        timerText.setTextColor(fg)
        hintText.setTextColor(fg)
        minimizeButton.imageTintList = ColorStateList.valueOf(fg)
        closeButton.imageTintList = ColorStateList.valueOf(fg)
        listOf<ImageButton>(
            overlayView!!.findViewById(R.id.playButton),
            overlayView!!.findViewById(R.id.stopButton),
            overlayView!!.findViewById(R.id.resetButton),
        ).forEach { it.imageTintList = ColorStateList.valueOf(red) }
    }

    private fun persistOverlayUiPrefs() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TIMER_MINIMIZED, isMinimized)
            .apply()
    }

    private fun clampOverlayWithinScreen() {
        val view = overlayView ?: return
        val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
        overlayParams.x = overlayParams.x.coerceIn(0, maxX)
        overlayParams.y = overlayParams.y.coerceIn(0, maxY)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "GymTrack Timer", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(content: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GymTrack").setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pi).setOngoing(true).build()
    }

    companion object {
        private const val CHANNEL_ID      = "gymtrack_timer_channel"
        private const val NOTIFICATION_ID = 44
        private const val PREFS_NAME      = "gymtrack_prefs"
        /** Si es true, al guardar una nueva serie en una sesión se intenta iniciar el temporizador (overlay). */
        const val KEY_TIMER_AUTOSTART_AFTER_SET = "timer_autostart_after_set"

        const val EXTRA_AUTOSTART = "timer_extra_autostart"

        const val KEY_TIMER_MS            = "timer_ms"
        const val KEY_TIMER_SOUND_URI     = "timer_sound_uri"
        const val KEY_TIMER_SOUND_NAME    = "timer_sound_name"
        const val KEY_TIMER_SOUND_THEME   = "timer_sound_theme"
        const val KEY_TIMER_VIBRATE       = "timer_vibrate"
        const val KEY_TIMER_MINIMIZED     = "timer_minimized"
        private const val KEY_DARK_MODE   = "dark_mode"
        private const val FALLBACK_MS     = 5 * 60 * 1000L
    }
}
