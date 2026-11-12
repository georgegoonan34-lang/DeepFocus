package com.deepfocus.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedAppsTest {

    // The bug this guards against: "x.com" used to match by `contains`,
    // which blocked every address ending in those letters.
    @Test
    fun `roblox sites are not caught by the x dot com rule`() {
        assertFalse(BlockedApps.isUrlBlocked("roblox.com"))
        assertFalse(BlockedApps.isUrlBlocked("https://www.roblox.com/home"))
        assertFalse(BlockedApps.isUrlBlocked("create.roblox.com/dashboard/creations"))
        assertFalse(BlockedApps.isUrlBlocked("advertise.roblox.com"))
        assertFalse(BlockedApps.isUrlBlocked("ads.roblox.com/manage"))
        assertFalse(BlockedApps.isUrlBlocked("dropbox.com/home"))
    }

    @Test
    fun `roblox app is not blocked`() {
        assertFalse(BlockedApps.isBlocked("com.roblox.client"))
    }

    @Test
    fun `x and twitter are still blocked`() {
        assertTrue(BlockedApps.isUrlBlocked("x.com"))
        assertTrue(BlockedApps.isUrlBlocked("https://x.com/home"))
        assertTrue(BlockedApps.isUrlBlocked("mobile.x.com/someone"))
        assertTrue(BlockedApps.isUrlBlocked("twitter.com/someone"))
    }

    @Test
    fun `netflix and snapchat are blocked as apps and sites`() {
        assertTrue(BlockedApps.isBlocked("com.netflix.mediaclient"))
        assertTrue(BlockedApps.isBlocked("com.snapchat.android"))
        assertTrue(BlockedApps.isUrlBlocked("netflix.com/browse"))
        assertTrue(BlockedApps.isUrlBlocked("www.netflix.com"))
        assertTrue(BlockedApps.isUrlBlocked("web.snapchat.com"))
    }

    @Test
    fun `subdomains and paths of blocked sites are blocked`() {
        assertTrue(BlockedApps.isUrlBlocked("m.youtube.com/shorts/abc"))
        assertTrue(BlockedApps.isUrlBlocked("https://www.youtube.com/watch?v=1"))
        assertTrue(BlockedApps.isUrlBlocked("YouTube.com"))
        assertTrue(BlockedApps.isUrlBlocked("old.reddit.com/r/all"))
    }

    @Test
    fun `a blocked name elsewhere in the address does not block`() {
        assertFalse(BlockedApps.isUrlBlocked("google.com/search?q=youtube.com"))
        assertFalse(BlockedApps.isUrlBlocked("google.com/search?q=https://youtube.com"))
        assertFalse(BlockedApps.isUrlBlocked("example.com/x.com"))
        assertFalse(BlockedApps.isUrlBlocked("notreddit.com"))
    }

    @Test
    fun `host is read from any address bar format`() {
        assertEquals("roblox.com", BlockedApps.hostOf("roblox.com"))
        assertEquals("www.roblox.com", BlockedApps.hostOf("https://www.roblox.com:443/home?a=1#top"))
        assertEquals("x.com", BlockedApps.hostOf("http://user@x.com/path"))
        assertNull(BlockedApps.hostOf("how to block x.com"))
        assertNull(BlockedApps.hostOf(""))
        assertNull(BlockedApps.hostOf("localhost"))
    }
}
