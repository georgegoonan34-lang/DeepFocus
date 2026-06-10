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
import com.deepfocus.app.presentation.ui.screens.SetupActivity
import com.deepfocus.app.service.accessibility.AccessibilityToggle
import com.deepfocus.app.util.BankingApps
import com.deepfocus.app.util.BlockedApps
import com.deepfocus.app.util.ScheduledApps
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

    // Tracks whether the last foreground app we saw was a banking app.
    // When the user leaves the banking app we use this edge to know we
    // should restore the accessibility service. We can't rely on "did WE
    // disable it" because the accessibility service can disable itself
    // directly on its own window event — both paths converge here.
    private var lastWasBankingApp = false

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

            // Banking apps (HSBC etc.) refuse to run with accessibility on.
            // Toggle our service off while banking is in foreground, back on
            // the moment the user leaves. This runs BEFORE the early-return
            // for our own package so leaving HSBC straight to our launcher
            // still re-enables accessibility.
            handleBankingAppPresence(currentApp)

            // Skip our own app
            if (currentApp == packageName) return

            if (BlockedApps.isBlocked(currentApp)) {
                Log.d(TAG, "Detected blocked app in foreground: $currentApp")
                blockApp(scheduleLabel = null)
            } else if (ScheduledApps.isPackageScheduled(currentApp) &&
                !ScheduledApps.isPackageAllowedNow(currentApp)
            ) {
                Log.d(TAG, "Detected scheduled app outside window: $currentApp")
                blockApp(scheduleLabel = ScheduledApps.packageScheduleLabel(currentApp))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    private fun handleBankingAppPresence(currentApp: String) {
        val isBanking = BankingApps.isBankingApp(currentApp)
        if (isBanking) {
            // Make sure accessibility is off while banking is open. Safe to
            // call even if it's already off — disableOurService no-ops in
            // that case.
            AccessibilityToggle.disableOurService(this)
            lastWasBankingApp = true
        } else if (lastWasBankingApp) {
            // User just left the banking app — restore accessibility.
            // enableOurService no-ops if it's already on.
            AccessibilityToggle.enableOurService(this)
            lastWasBankingApp = false
        }
    }

    private fun blockApp(scheduleLabel: String?) {
        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_BLOCKED_TYPE, BlockedActivity.TYPE_APP)
            if (scheduleLabel != null) {
                putExtra(BlockedActivity.EXTRA_SCHEDULE_LABEL, scheduleLabel)
            }
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
        val intent = Intent(this, SetupActivity::class.java)
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped DeepFocus out of recents — don't let the service
        // die quietly. Reschedule a self-restart through the system.
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "Task removed; restarting BlockingService")
        val restartIntent = Intent(applicationContext, BlockingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        Log.d(TAG, "BlockingService destroyed")
    }
}
