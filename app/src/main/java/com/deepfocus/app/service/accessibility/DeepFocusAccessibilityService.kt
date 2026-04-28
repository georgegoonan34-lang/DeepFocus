package com.deepfocus.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.deepfocus.app.presentation.ui.screens.BlockedActivity
import com.deepfocus.app.util.BlockedApps

/**
 * Accessibility Service that monitors app launches and browser URLs.
 *
 * This is the core of DeepFocus - it detects when blocked apps are opened
 * and immediately shows the block screen.
 */
class DeepFocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DeepFocusA11y"
        private var instance: DeepFocusAccessibilityService? = null

        fun getInstance(): DeepFocusAccessibilityService? = instance

        /**
         * Settings/installer packages whose screens we monitor for tamper
         * attempts. If any of these show a DeepFocus-related screen we
         * bounce the user home — by design the only way out is ADB.
         */
        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )

        private const val TAMPER_KICKOUT_COOLDOWN_MS = 1500L
        private const val SETTINGS_CHECK_THROTTLE_MS = 150L
        private const val MAX_TRAVERSAL_DEPTH = 14
    }

    private var lastBlockedPackage: String? = null
    private var lastBlockedUrl: String? = null
    private var lastBlockTime: Long = 0
    private val blockCooldown = 1000L // Prevent rapid re-blocking

    // Settings Guardian state
    private var lastTamperKickout: Long = 0
    private var lastSettingsCheck: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Configure the service for better URL detection
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
        }

        Log.d(TAG, "Accessibility service connected and configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Skip our own app — never kick the user out of our own setup screen.
        if (packageName == applicationContext.packageName) return

        // SETTINGS GUARDIAN: catch any path that could disable DeepFocus
        // (the accessibility toggle, App Info → Force Stop / Uninstall, the
        // device admin entry, the package installer's uninstall flow). This
        // runs before normal blocking because losing the service is the
        // worst possible outcome.
        if (packageName in SETTINGS_PACKAGES) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {
                checkSettingsTampering()
            }
            return
        }

        // Skip system UI for normal blocking checks
        if (packageName == "com.android.systemui") return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChange(packageName, event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Check for URL changes in browsers
                if (BlockedApps.isBrowser(packageName)) {
                    checkBrowserUrl(event, packageName)
                }
            }
        }
    }

    private fun handleWindowChange(packageName: String, event: AccessibilityEvent) {
        // Check if app is blocked
        if (BlockedApps.isBlocked(packageName)) {
            blockApp(packageName, BlockedActivity.TYPE_APP)
            return
        }

        // If it's a browser, also check URL on window change
        if (BlockedApps.isBrowser(packageName)) {
            checkBrowserUrl(event, packageName)
        }
    }

    private fun checkBrowserUrl(event: AccessibilityEvent, packageName: String) {
        try {
            val rootNode = rootInActiveWindow ?: event.source ?: return
            val url = findUrlInNode(rootNode, packageName)

            if (url != null) {
                Log.d(TAG, "Found URL: $url in $packageName")

                if (BlockedApps.isUrlBlocked(url)) {
                    // Avoid blocking same URL repeatedly
                    if (url == lastBlockedUrl && (System.currentTimeMillis() - lastBlockTime) < 3000) {
                        return
                    }

                    Log.d(TAG, "BLOCKING URL: $url")
                    lastBlockedUrl = url

                    val blockType = if (url.contains("shorts", ignoreCase = true)) {
                        BlockedActivity.TYPE_SHORTS
                    } else {
                        BlockedActivity.TYPE_URL
                    }

                    blockApp(packageName, blockType)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking browser URL", e)
        }
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo, packageName: String): String? {
        // Browser-specific URL bar IDs
        val urlBarIds = when (packageName) {
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev" -> listOf(
                "com.android.chrome:id/url_bar",
                "com.android.chrome:id/search_box_text",
                "com.android.chrome:id/omnibox_text_field"
            )
            "com.sec.android.app.sbrowser" -> listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                "com.sec.android.app.sbrowser:id/url_bar",
                "com.sec.android.app.sbrowser:id/address_bar_edit_text"
            )
            "org.mozilla.firefox", "org.mozilla.firefox_beta" -> listOf(
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox:id/url_bar_title"
            )
            "com.brave.browser" -> listOf(
                "com.brave.browser:id/url_bar",
                "com.brave.browser:id/search_box_text"
            )
            "com.microsoft.emmx" -> listOf(
                "com.microsoft.emmx:id/url_bar",
                "com.microsoft.emmx:id/search_box_text"
            )
            else -> listOf(
                "$packageName:id/url_bar",
                "$packageName:id/search_box_text",
                "$packageName:id/address_bar_edit_text",
                "$packageName:id/location_bar_edit_text",
                "$packageName:id/url",
                "$packageName:id/omnibox_text_field"
            )
        }

        // Try specific IDs first
        for (urlBarId in urlBarIds) {
            try {
                val nodes = node.findAccessibilityNodeInfosByViewId(urlBarId)
                if (!nodes.isNullOrEmpty()) {
                    val text = nodes[0].text?.toString()
                    if (!text.isNullOrEmpty() && text.length > 3) {
                        return text
                    }
                }
            } catch (e: Exception) {
                // Continue trying other IDs
            }
        }

        // Fallback: Search all nodes for URL-like content
        return findUrlInChildren(node, 0)
    }

    private fun findUrlInChildren(node: AccessibilityNodeInfo, depth: Int): String? {
        if (depth > 15) return null // Prevent deep recursion

        try {
            val text = node.text?.toString()
            if (text != null && isLikelyUrl(text) && text.length > 5) {
                return text
            }

            // Also check content description
            val contentDesc = node.contentDescription?.toString()
            if (contentDesc != null && isLikelyUrl(contentDesc) && contentDesc.length > 5) {
                return contentDesc
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findUrlInChildren(child, depth + 1)
                child.recycle()
                if (result != null) {
                    return result
                }
            }
        } catch (e: Exception) {
            // Ignore exceptions during traversal
        }

        return null
    }

    private fun isLikelyUrl(text: String): Boolean {
        val lower = text.lowercase()
        // Check if it looks like a URL and contains blocked domains
        return (lower.contains(".com") || lower.contains(".org") || lower.contains("http")) &&
                (lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("reddit.com") ||
                lower.contains("instagram.com") ||
                lower.contains("tiktok.com") ||
                lower.contains("twitter.com") ||
                lower.contains("x.com") ||
                lower.contains("facebook.com") ||
                lower.contains("shorts"))
    }

    private fun blockApp(packageName: String, blockType: String) {
        val now = System.currentTimeMillis()

        // Prevent rapid re-blocking of same app
        if (packageName == lastBlockedPackage && (now - lastBlockTime) < blockCooldown) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockTime = now

        Log.d(TAG, "BLOCKING: $packageName (type: $blockType)")

        // Perform back action first to try to close the page/app
        performGlobalAction(GLOBAL_ACTION_BACK)

        // Launch block screen
        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(BlockedActivity.EXTRA_BLOCKED_TYPE, blockType)
        }
        startActivity(intent)
    }

    // =========================================================================
    // SETTINGS GUARDIAN
    // =========================================================================
    // While the accessibility service is running it polices every Settings /
    // installer screen. The instant a DeepFocus-related screen appears we
    // bounce the user home and show the tamper block — they can't reach the
    // accessibility toggle, App Info, Device Admin entry, or uninstall flow.
    // Only ADB from a computer can disable us.
    // =========================================================================

    private fun checkSettingsTampering() {
        val now = System.currentTimeMillis()
        if (now - lastSettingsCheck < SETTINGS_CHECK_THROTTLE_MS) return
        lastSettingsCheck = now

        val rootNode = rootInActiveWindow ?: return
        if (containsDeepFocusReference(rootNode, 0)) {
            kickOutOfSettings()
        }
    }

    private fun containsDeepFocusReference(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > MAX_TRAVERSAL_DEPTH) return false

        try {
            if (matchesDeepFocus(node.text)) return true
            if (matchesDeepFocus(node.contentDescription)) return true
            if (matchesDeepFocus(node.viewIdResourceName)) return true

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    if (containsDeepFocusReference(child, depth + 1)) {
                        return true
                    }
                } finally {
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            // Traversal failure — fall through. Better to miss a tamper attempt
            // than to crash and lose the service entirely.
        }

        return false
    }

    private fun matchesDeepFocus(value: CharSequence?): Boolean {
        if (value == null) return false
        val text = value.toString().lowercase()
        return text.contains("deepfocus") ||
                text.contains("deep focus") ||
                text.contains("com.deepfocus")
    }

    private fun kickOutOfSettings() {
        val now = System.currentTimeMillis()
        if (now - lastTamperKickout < TAMPER_KICKOUT_COOLDOWN_MS) return
        lastTamperKickout = now

        Log.w(TAG, "TAMPER DETECTED: Settings screen referencing DeepFocus. Kicking out.")

        // Slam back first to dismiss any in-flight confirmation dialog
        // (e.g. the "Stop DeepFocus from running?" prompt that appears when
        // the user actually toggles the accessibility switch).
        performGlobalAction(GLOBAL_ACTION_BACK)

        // Send the user to the home screen — LauncherActivity is our home.
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch home", e)
        }

        // Layer the tamper block on top so the user understands what just
        // happened and what the only escape route is.
        val blockIntent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(BlockedActivity.EXTRA_BLOCKED_TYPE, BlockedActivity.TYPE_TAMPER)
        }
        try {
            startActivity(blockIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch tamper block", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }
}
