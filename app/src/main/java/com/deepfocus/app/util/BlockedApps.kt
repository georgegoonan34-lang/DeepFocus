package com.deepfocus.app.util

/**
 * HARDCODED BLOCKED APPS
 *
 * To modify this list, you must:
 * 1. Edit this file on your computer
 * 2. Plug the phone in
 * 3. Rebuild and reinstall: ./install.sh
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
        "com.linkedin.android",                     // LinkedIn
        "com.snapchat.android",                     // Snapchat

        // Streaming
        "com.netflix.mediaclient",                  // Netflix

        // YouTube - fully blocked, app and web (see the youtube.com URL
        // patterns below). Blocking the app matters because Shorts can't be
        // detected inside it: the accessibility service only reads browser
        // URL bars. Leaving the app open would also let YouTube app-links
        // intercept youtube.com URLs and bypass the URL blocking entirely.
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
    )

    /**
     * Sites to block in browsers, as domains.
     * A page is blocked when its host IS one of these or is a subdomain
     * of one, so "youtube.com" also covers www./m. variants and every
     * /shorts URL under them.
     *
     * Matching is on the host, never on the raw text: a plain `contains`
     * made "x.com" swallow roblox.com, netflix.com, dropbox.com and every
     * other address that happens to end in those letters.
     */
    val BLOCKED_URL_PATTERNS = setOf(
        "youtube.com",
        "youtu.be",
        "instagram.com",
        "tiktok.com",
        "twitter.com",
        "x.com",
        "reddit.com",
        "netflix.com",
        "snapchat.com",
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
        val host = hostOf(url) ?: return false
        return BLOCKED_URL_PATTERNS.any { domain -> hostMatches(host, domain) }
    }

    /**
     * Pulls the host out of whatever the browser's address bar shows.
     * Bars show anything from "https://www.roblox.com/home" down to a bare
     * "roblox.com", so scheme, login, port and path are all optional.
     * Returns null for text that isn't an address (e.g. a search phrase).
     */
    fun hostOf(url: String): String? {
        val text = url.trim().lowercase()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null

        // Only a leading "https://" is a scheme. A "://" further along
        // belongs to a link inside the path or query and must not be
        // mistaken for the start of the address.
        val schemeEnd = text.indexOf("://")
        val hasScheme = schemeEnd > 0 &&
                text.substring(0, schemeEnd).all { it.isLetterOrDigit() || it in "+-." }
        val afterScheme = if (hasScheme) text.substring(schemeEnd + 3) else text

        val host = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .trimEnd('.')

        return host.takeIf { it.contains('.') }
    }

    /** True when [host] is [domain] itself or any subdomain of it. */
    fun hostMatches(host: String, domain: String): Boolean {
        val d = domain.lowercase()
        return host == d || host.endsWith(".$d")
    }

    fun isBrowser(packageName: String): Boolean {
        return packageName in BROWSER_PACKAGES
    }
}
