package com.deepfocus.app.util

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/**
 * Today's screen time, read on demand for the home screen.
 *
 * This used to be a background service that pushed a notification every
 * 30 minutes. A phone that pings you to say you use it too much is its own
 * distraction, so the number now just sits quietly under the clock.
 */
object ScreenTime {

    /** Minutes in the foreground since midnight, or null without Usage Access. */
    fun todayMinutes(context: Context): Long? {
        // Without PACKAGE_USAGE_STATS the query silently returns empty,
        // which would read as "0 minutes". Report "unknown" instead of
        // lying about the screen time.
        if (!hasUsageStatsPermission(context)) return null

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // INTERVAL_BEST lets the system pick the most accurate bucket size
        // for the requested range — for "today" it picks daily aggregates
        // but trims them to the actual range, which is more accurate than
        // INTERVAL_DAILY when called early in the day.
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startOfDay,
            System.currentTimeMillis(),
        )
        if (usageStats.isNullOrEmpty()) return 0

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
        val skipPackages = setOf(context.packageName, "com.android.systemui", "android")

        val totalMs = totalsByPackage.filterKeys { it !in skipPackages }.values.sum()
        return totalMs / (1000 * 60) // ms → minutes
    }

    /** "47m" or "2h 05m". */
    fun format(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h %02dm".format(mins) else "${mins}m"
    }
}
