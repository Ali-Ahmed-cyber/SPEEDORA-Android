package com.speedora.app.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.speedora.app.MainActivity
import com.speedora.app.R
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.core.model.TransferProgressSnapshot
import com.speedora.app.core.model.TransferState

class TransferForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "speedora_transfers"
        private const val NOTIF_ID = 7001
        private const val ACTION_START = "com.speedora.app.ACTION_START_TRANSFER"
        private const val ACTION_UPDATE = "com.speedora.app.ACTION_UPDATE_TRANSFER"
        private const val ACTION_STOP = "com.speedora.app.ACTION_STOP_TRANSFER"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TEXT = "extra_text"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val EXTRA_INDETERMINATE = "extra_indeterminate"

        fun start(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, snapshot: TransferProgressSnapshot) {
            val title = when (snapshot.state) {
                TransferState.COMPLETED -> context.getString(R.string.state_completed)
                TransferState.FAILED -> context.getString(R.string.state_failed)
                TransferState.VERIFYING_INTEGRITY -> context.getString(R.string.state_verifying)
                else -> snapshot.currentFileName.ifBlank { context.getString(R.string.app_name) }
            }
            val text = "${snapshot.progressPercent}% · ${FileSizeFormatter.formatSpeed(snapshot.instantBytesPerSec)}"
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_PROGRESS, snapshot.progressPercent)
                putExtra(EXTRA_INDETERMINATE, snapshot.totalSessionBytes <= 0L)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                acquireLocks()
                startForeground(
                    NOTIF_ID,
                    buildNotification(
                        title = getString(R.string.app_name),
                        text = getString(R.string.state_connecting),
                        progress = 0,
                        indeterminate = true
                    )
                )
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, false)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification(title, text, progress, indeterminate))
            }
            ACTION_STOP -> {
                releaseLocks()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (wakeLock?.isHeld != true) {
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "speedora:transfer_wake")?.apply {
                acquire(15 * 60 * 1000L)
            }
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiLock?.isHeld != true) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm?.createWifiLock(mode, "speedora:transfer_wifi")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_speedora_nav)
        .setContentTitle(title)
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, progress, indeterminate)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
