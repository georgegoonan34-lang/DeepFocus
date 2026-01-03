package com.deepfocus.app.service.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.deepfocus.app.presentation.ui.screens.LauncherActivity
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * Service that tracks screen time and sends notifications every 30 minutes
 * to discourage excessive phone usage.
 */
class ScreenTimeService : Service() {

    companion object {
        private const val TAG = "ScreenTimeService"
        private const val CHANNEL_ID = "deepfocus_screentime"
        private const val NOTIFICATION_ID = 1002
        private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = false
    private var lastNotificationTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ScreenTimeService started")

        if (!isRunning) {
            isRunning = true
            startMonitoring()
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isRunning) {
                checkAndNotify()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun checkAndNotify() {
        try {
            val screenTimeMinutes = getTodayScreenTime()
            val now = System.currentTimeMillis()

            // Only notify if 30 mins have passed since last notification
            if (now - lastNotificationTime >= CHECK_INTERVAL_MS) {
                lastNotificationTime = now
                sendScreenTimeNotification(screenTimeMinutes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking screen time", e)
        }
    }

    private fun getTodayScreenTime(): Long {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now
        )

        var totalTimeMs = 0L
        usageStats?.forEach { stats ->
            totalTimeMs += stats.totalTimeInForeground
        }

        return totalTimeMs / (1000 * 60) // Convert to minutes
    }

    private fun sendScreenTimeNotification(minutes: Long) {
        val hours = minutes / 60
        val mins = minutes % 60

        val timeString = when {
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }

        val message = when {
            minutes < 30 -> "Screen time today: $timeString. Great job staying focused!"
            minutes < 60 -> "Screen time today: $timeString. You're doing well."
            minutes < 120 -> "Screen time today: $timeString. Consider taking a break."
            minutes < 180 -> "Screen time today: $timeString. Time to put the phone down?"
            else -> "Screen time today: $timeString. Your future self is watching."
        }

        val intent = Intent(this, LauncherActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Time Check")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "Screen time notification sent: $message")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Time Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications about your daily screen time"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        Log.d(TAG, "ScreenTimeService destroyed")
    }
}
