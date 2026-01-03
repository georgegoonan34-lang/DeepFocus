package com.deepfocus.app.service.accessibility

import android.accessibilityservice.AccessibilityService
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
    }

    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0
    private val blockCooldown = 500L // Prevent rapid re-blocking

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Skip our own app
        if (packageName == applicationContext.packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChange(packageName)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // Check for URL changes in browsers
                if (BlockedApps.isBrowser(packageName)) {
                    checkBrowserUrl(event)
                }
            }
        }
    }

    private fun handleWindowChange(packageName: String) {
        // Check if app is blocked
        if (BlockedApps.isBlocked(packageName)) {
            blockApp(packageName, BlockedActivity.TYPE_APP)
            return
        }

        // If it's a browser, we'll monitor URL changes via content changed events
    }

    private fun checkBrowserUrl(event: AccessibilityEvent) {
        val url = extractUrlFromBrowser(event)
        if (url != null && BlockedApps.isUrlBlocked(url)) {
            Log.d(TAG, "Blocked URL detected: $url")

            val blockType = if (url.contains("shorts", ignoreCase = true)) {
                BlockedActivity.TYPE_SHORTS
            } else {
                BlockedActivity.TYPE_URL
            }

            blockApp(event.packageName?.toString() ?: "", blockType)
        }
    }

    private fun extractUrlFromBrowser(event: AccessibilityEvent): String? {
        try {
            val source = event.source ?: return null
            return findUrlInNode(source)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting URL", e)
            return null
        }
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo): String? {
        // Look for URL bar by common resource IDs
        val urlBarIds = listOf(
            "url_bar",
            "url_field",
            "search_box_text",
            "mozac_browser_toolbar_url_view",
            "url",
            "address_bar_edit_text",
            "search_edit_text"
        )

        for (urlBarId in urlBarIds) {
            val nodes = node.findAccessibilityNodeInfosByViewId("$urlBarId")
            if (nodes.isNullOrEmpty()) {
                // Try with package prefix
                val nodesByFullId = node.findAccessibilityNodeInfosByViewId(
                    "${node.packageName}:id/$urlBarId"
                )
                if (!nodesByFullId.isNullOrEmpty()) {
                    val text = nodesByFullId[0].text?.toString()
                    if (!text.isNullOrEmpty()) {
                        return text
                    }
                }
            } else {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrEmpty()) {
                    return text
                }
            }
        }

        // Fallback: Look for any EditText with URL-like content
        return findUrlInChildren(node)
    }

    private fun findUrlInChildren(node: AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 10) return null // Prevent infinite recursion

        val text = node.text?.toString()
        if (text != null && isLikelyUrl(text)) {
            return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findUrlInChildren(child, depth + 1)
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun isLikelyUrl(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("reddit.com") ||
                lower.contains("instagram.com") ||
                lower.contains("tiktok.com") ||
                lower.contains("twitter.com") ||
                lower.contains("x.com") ||
                lower.contains("facebook.com")
    }

    private fun blockApp(packageName: String, blockType: String) {
        val now = System.currentTimeMillis()

        // Prevent rapid re-blocking of same app
        if (packageName == lastBlockedPackage && (now - lastBlockTime) < blockCooldown) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockTime = now

        Log.d(TAG, "Blocking: $packageName (type: $blockType)")

        // Launch block screen
        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BlockedActivity.EXTRA_BLOCKED_TYPE, blockType)
        }
        startActivity(intent)

        // Also perform global back action to close the blocked app
        performGlobalAction(GLOBAL_ACTION_BACK)
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
