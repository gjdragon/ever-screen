package com.example.everscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that holds a wake lock so the screen stays on even
 * when the app is backgrounded (unlike an Activity's FLAG_KEEP_SCREEN_ON,
 * which only works while that Activity is visible).
 */
class ScreenOnService : Service() {

    // Observable state the Activity can collect while it's alive, so the
    // UI reflects the countdown even after being backgrounded and resumed.
    data class State(
        val isRunning: Boolean = false,
        val remainingMillis: Long = 0L,
        val endTimeMillis: Long = 0L
    )

    companion object {
        const val ACTION_START = "com.example.everscreen.action.START"
        const val ACTION_STOP = "com.example.everscreen.action.STOP"
        const val EXTRA_MINUTES = "extra_minutes"

        private const val CHANNEL_ID = "screen_on_channel"
        private const val NOTIFICATION_ID = 1001

        private val _state = MutableStateFlow(State())
        val state = _state.asStateFlow()
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var countDownTimer: CountDownTimer? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 30)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(minutes * 60_000L),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification(minutes * 60_000L))
                }
                acquireWakeLock(minutes * 60_000L)
                addOverlayView()
                startCountdown(minutes * 60_000L)
            }
        }
        return START_NOT_STICKY
    }

    private fun acquireWakeLock(durationMillis: Long) {
        releaseWakeLock()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        // PARTIAL_WAKE_LOCK keeps CPU active so the service countdown stays running.
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EverScreen::WakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(durationMillis + 2000L) // small safety margin over the timer
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun addOverlayView() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutParams = WindowManager.LayoutParams(
            1, 1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        val view = View(this)
        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlayView() {
        overlayView?.let { view ->
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayView = null
    }

    private fun startCountdown(durationMillis: Long) {
        countDownTimer?.cancel()
        val endTime = System.currentTimeMillis() + durationMillis
        _state.value = State(isRunning = true, remainingMillis = durationMillis, endTimeMillis = endTime)

        countDownTimer = object : CountDownTimer(durationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                _state.value = _state.value.copy(remainingMillis = millisUntilFinished)
                updateNotification(millisUntilFinished)
            }

            override fun onFinish() {
                stopSelfCleanly()
            }
        }.start()
    }

    private fun stopSelfCleanly() {
        countDownTimer?.cancel()
        countDownTimer = null
        removeOverlayView()
        releaseWakeLock()
        _state.value = State(isRunning = false, remainingMillis = 0L, endTimeMillis = 0L)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        removeOverlayView()
        releaseWakeLock()
        _state.value = State()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EverScreen",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while the screen is being kept awake"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(remainingMillis: Long): Notification {
        createChannelIfNeeded()

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenOnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("EverScreen — screen kept awake")
            .setContentText(formatRemaining(remainingMillis) + " remaining")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(remainingMillis: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(remainingMillis))
    }

    private fun formatRemaining(millis: Long): String {
        val minutes = millis / 60000
        val seconds = (millis / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/** Small formatting helper shared with MainActivity. */
fun formatClockTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
