package com.deepfocus.app.util

import java.util.Calendar

/**
 * Time-windowed access. Inside a window the app/URL is allowed; outside,
 * the accessibility service blocks it the same way as anything in
 * BlockedApps — the difference is the block screen tells you when it'll
 * open up again.
 *
 * Edit this file and reinstall via ADB to change schedules. No in-phone
 * configuration, same principle as BlockedApps.
 */
object ScheduledApps {

    /**
     * Daily window. Times are minutes-since-midnight (so 6pm = 18 * 60 = 1080).
     * The window is half-open: [startMin, endMin), so 18:00–21:00 means
     * "allowed at 20:59, blocked at 21:00".
     */
    data class TimeWindow(val startMin: Int, val endMin: Int) {
        fun isNowInside(): Boolean {
            val cal = Calendar.getInstance()
            val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            return if (startMin <= endMin) {
                now in startMin until endMin
            } else {
                // Window crosses midnight (e.g. 22:00–02:00).
                now >= startMin || now < endMin
            }
        }

        fun format(): String = "${fmt(startMin)}–${fmt(endMin)}"

        private fun fmt(minOfDay: Int): String =
            "%02d:%02d".format(minOfDay / 60, minOfDay % 60)
    }

    /** Package name → list of windows when the app is allowed. */
    val SCHEDULED_PACKAGES: Map<String, List<TimeWindow>> = emptyMap()

    /**
     * URL host substrings → list of windows. Match is by `contains`, so
     * "youtube.com" matches m.youtube.com, www.youtube.com, etc.
     */
    val SCHEDULED_URL_PATTERNS: Map<String, List<TimeWindow>> = emptyMap()

    fun isPackageScheduled(pkg: String): Boolean = pkg in SCHEDULED_PACKAGES

    fun isPackageAllowedNow(pkg: String): Boolean {
        val windows = SCHEDULED_PACKAGES[pkg] ?: return true
        return windows.any { it.isNowInside() }
    }

    fun packageScheduleLabel(pkg: String): String? {
        val windows = SCHEDULED_PACKAGES[pkg] ?: return null
        return windows.joinToString(", ") { it.format() }
    }

    private fun matchUrl(url: String): List<TimeWindow>? {
        val lower = url.lowercase()
        for ((pattern, windows) in SCHEDULED_URL_PATTERNS) {
            if (lower.contains(pattern)) return windows
        }
        return null
    }

    fun isUrlScheduled(url: String): Boolean = matchUrl(url) != null

    fun isUrlAllowedNow(url: String): Boolean {
        val windows = matchUrl(url) ?: return true
        return windows.any { it.isNowInside() }
    }

    fun urlScheduleLabel(url: String): String? {
        val windows = matchUrl(url) ?: return null
        return windows.joinToString(", ") { it.format() }
    }
}
