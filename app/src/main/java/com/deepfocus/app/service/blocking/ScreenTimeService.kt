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
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.deepfocus.app.presentation.ui.screens.LauncherActivity
import com.deepfocus.app.util.hasUsageStatsPermission
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
            val now = System.currentTimeMillis()
            // Only notify if 30 mins have passed since last notification
            if (now - lastNotificationTime < CHECK_INTERVAL_MS) return

            // Without PACKAGE_USAGE_STATS the query silently returns empty,
            // which is exactly the "0 minutes" bug. Surface the missing
            // permission instead of lying about the screen time.
            if (!hasUsageStatsPermission(this)) {
                lastNotificationTime = now
                sendUsageAccessRequiredNotification()
                return
            }

            val screenTimeMinutes = getTodayScreenTime()
            lastNotificationTime = now
            sendScreenTimeNotification(screenTimeMinutes)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking screen time", e)
        }
    }

    private fun getTodayScreenTime(): Long {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        // INTERVAL_BEST lets the system pick the most accurate bucket size
        // for the requested range — for "today" it picks daily aggregates
        // but trims them to the actual range, which is more accurate than
        // INTERVAL_DAILY when called early in the day.
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startOfDay,
            now,
        )

        if (usageStats.isNullOrEmpty()) {
            Log.w(TAG, "queryUsageStats returned empty — usage access still not granted?")
            return 0
        }

        // Aggregate by package: keep the largest reported foreground time
        // per package across overlapping buckets, which avoids double-counting
        // when INTERVAL_BEST returns multiple buckets for the same app.
        val totalsByPackage = HashMap<String, Long>()
        for (stats in usageStats) {
            val current = totalsByPackage[stats.packageName] ?: 0L
            if (stats.totalTimeInForeground > current) {
                totalsByPackage[stats.packageName] = stats.totalTimeInForeground
            }
        }

        // Skip our own app and the system UI / launcher chrome that always
        // looks "in foreground" because it hosts the home screen.
        val skipPackages = setOf(
            packageName,
            "com.android.systemui",
            "android",
        )

        var totalTimeMs = 0L
        for ((pkg, time) in totalsByPackage) {
            if (pkg in skipPackages) continue
            totalTimeMs += time
        }

        return totalTimeMs / (1000 * 60) // ms → minutes
    }

    private fun sendUsageAccessRequiredNotification() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Grant Usage Access")
            .setContentText("DeepFocus needs Usage Access to track screen time. Tap to grant.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.w(TAG, "Usage access not granted — sent grant prompt notification")
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
