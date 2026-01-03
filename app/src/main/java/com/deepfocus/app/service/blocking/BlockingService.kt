package com.deepfocus.app.service.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.deepfocus.app.presentation.ui.screens.BlockedActivity
import com.deepfocus.app.presentation.ui.screens.LauncherActivity
import com.deepfocus.app.util.BlockedApps
import kotlinx.coroutines.*

/**
 * Foreground service that monitors running apps as a backup to accessibility service.
 * Uses UsageStatsManager to detect foreground apps and block if necessary.
 */
class BlockingService : Service() {

    companion object {
        private const val TAG = "BlockingService"
        private const val CHANNEL_ID = "deepfocus_blocking"
        private const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 500L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BlockingService started")

        startForeground(NOTIFICATION_ID, createNotification())

        if (!isRunning) {
            isRunning = true
            startMonitoring()
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isRunning) {
                checkForegroundApp()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun checkForegroundApp() {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (usageStats.isNullOrEmpty()) return

            val currentApp = usageStats
                .filter { it.lastTimeUsed > 0 }
                .maxByOrNull { it.lastTimeUsed }
                ?.packageName ?: return

            // Skip our own app
            if (currentApp == packageName) return

            if (BlockedApps.isBlocked(currentApp)) {
                Log.d(TAG, "Detected blocked app in foreground: $currentApp")
                blockApp()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    private fun blockApp() {
        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_BLOCKED_TYPE, BlockedActivity.TYPE_APP)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DeepFocus Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "DeepFocus is running"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, LauncherActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeepFocus")
            .setContentText("Blocking distractions")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        Log.d(TAG, "BlockingService destroyed")
    }
}
