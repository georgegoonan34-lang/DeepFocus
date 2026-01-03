package com.deepfocus.app.util

/**
 * HARDCODED BLOCKED APPS
 *
 * To modify this list, you must:
 * 1. Edit this file on your computer
 * 2. Rebuild the app: ./gradlew installDebug
 * 3. Reinstall via ADB
 *
 * This is intentional - no way to change from the phone.
 */
object BlockedApps {

    /**
     * Apps that are PERMANENTLY blocked and will never open.
     * Add package names here to block them.
     */
    val BLOCKED_PACKAGES = setOf(
        // Social Media - The endless scroll machines
        "com.instagram.android",                    // Instagram
        "com.instagram.lite",                       // Instagram Lite
        "com.zhiliaoapp.musically",                // TikTok
        "com.ss.android.ugc.trill",                // TikTok (alternate)
        "com.twitter.android",                      // Twitter/X
        "com.twitter.android.lite",                // Twitter Lite
        "com.reddit.frontpage",                     // Reddit Official
        "com.andrewshu.android.reddit",            // Reddit is Fun
        "com.laurencedawson.reddit_sync",          // Sync for Reddit
        "com.laurencedawson.reddit_sync.pro",      // Sync Pro
        "ml.docilealligator.infinityforreddit",    // Infinity for Reddit
        "com.rubenmayayo.reddit",                  // Boost for Reddit
        "com.onelouder.baconreader",               // BaconReader
        "com.facebook.katana",                      // Facebook
        "com.facebook.lite",                        // Facebook Lite
        "com.snapchat.android",                     // Snapchat
        "com.linkedin.android",                     // LinkedIn

        // YouTube App - Use browser instead (Shorts can't be blocked in-app easily)
        "com.google.android.youtube",              // YouTube
        "com.google.android.apps.youtube.music",   // YouTube Music

        // Games - Time sinks
        "com.supercell.clashroyale",               // Clash Royale
        "com.supercell.clashofclans",              // Clash of Clans
        "com.supercell.brawlstars",                // Brawl Stars
        "com.king.candycrushsaga",                 // Candy Crush
        "com.kiloo.subwaysurf",                    // Subway Surfers

        // Other distractions
        "com.pinterest",                            // Pinterest
        "com.tumblr",                               // Tumblr
        "tv.twitch.android.app",                   // Twitch

        // Streaming - Time sinks
        "com.netflix.mediaclient",                 // Netflix
        "com.netflix.ninja",                       // Netflix (alternate)
    )

    /**
     * URL patterns to block in browsers.
     * If the browser URL contains any of these, show block screen.
     */
    val BLOCKED_URL_PATTERNS = setOf(
        "youtube.com/shorts",
        "youtu.be/shorts",
        "m.youtube.com/shorts",
        "/shorts/",
        "instagram.com",
        "tiktok.com",
        "twitter.com",
        "x.com",
        "reddit.com",
        "facebook.com",
    )

    /**
     * Browser package names to monitor for URL blocking.
     */
    val BROWSER_PACKAGES = setOf(
        "com.android.chrome",                       // Chrome
        "com.chrome.beta",                          // Chrome Beta
        "com.chrome.dev",                           // Chrome Dev
        "org.mozilla.firefox",                      // Firefox
        "org.mozilla.firefox_beta",                // Firefox Beta
        "com.brave.browser",                        // Brave
        "com.opera.browser",                        // Opera
        "com.opera.mini.native",                   // Opera Mini
        "com.microsoft.emmx",                       // Edge
        "com.sec.android.app.sbrowser",            // Samsung Internet
        "com.duckduckgo.mobile.android",           // DuckDuckGo
        "com.vivaldi.browser",                      // Vivaldi
    )

    /**
     * Apps that should always be allowed (essential functions).
     * These will show in the launcher but never be blocked.
     */
    val ALWAYS_ALLOWED = setOf(
        "com.android.dialer",                       // Phone
        "com.samsung.android.dialer",              // Samsung Phone
        "com.google.android.dialer",               // Google Phone
        "com.android.contacts",                     // Contacts
        "com.samsung.android.contacts",            // Samsung Contacts
        "com.android.mms",                          // Messages
        "com.samsung.android.messaging",           // Samsung Messages
        "com.google.android.apps.messaging",       // Google Messages
        "com.android.settings",                     // Settings
        "com.android.vending",                      // Play Store
        "com.google.android.gm",                   // Gmail
        "com.google.android.apps.maps",            // Maps
        "com.google.android.calendar",             // Calendar
        "com.samsung.android.calendar",            // Samsung Calendar
        "com.deepfocus.app",                        // This app
    )

    fun isBlocked(packageName: String): Boolean {
        return packageName in BLOCKED_PACKAGES
    }

    fun isUrlBlocked(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return BLOCKED_URL_PATTERNS.any { pattern ->
            lowerUrl.contains(pattern.lowercase())
        }
    }

    fun isBrowser(packageName: String): Boolean {
        return packageName in BROWSER_PACKAGES
    }
}
