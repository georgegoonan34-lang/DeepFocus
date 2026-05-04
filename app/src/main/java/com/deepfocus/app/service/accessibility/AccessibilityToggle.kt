package com.deepfocus.app.service.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Toggles DeepFocus's own accessibility service via WRITE_SECURE_SETTINGS.
 *
 * Used to placate banking apps (HSBC etc.) that refuse to run while any
 * accessibility service is enabled. The accessibility service is disabled
 * while the banking app is in the foreground and re-enabled the moment
 * the user leaves it.
 *
 * Requires the WRITE_SECURE_SETTINGS permission, which can only be granted
 * via ADB:
 *   adb shell pm grant com.deepfocus.app android.permission.WRITE_SECURE_SETTINGS
 *
 * If the grant is missing, the writes silently fail (SecurityException is
 * caught). The accessibility service stays in whatever state it's in and
 * the user will need to disable it manually for HSBC — same situation as
 * before this feature existed, so no regression.
 */
object AccessibilityToggle {

    private const val TAG = "A11yToggle"

    /**
     * Disables our accessibility service if currently enabled.
     * Returns true if the state was changed (we disabled it), false if it
     * was already off or the write failed.
     */
    fun disableOurService(context: Context): Boolean {
        return try {
            val current = readEnabledServices(context)
            val ourComponent = ourComponentName(context)
            if (!current.contains(ourComponent, ignoreCase = true)) {
                return false
            }
            val updated = removeComponent(current, ourComponent)
            writeEnabledServices(context, updated)
            Log.d(TAG, "Disabled own accessibility service")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted; cannot disable a11y", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable a11y", e)
            false
        }
    }

    /**
     * Enables our accessibility service if currently disabled.
     * Returns true if the state was changed (we enabled it), false if it
     * was already on or the write failed.
     */
    fun enableOurService(context: Context): Boolean {
        return try {
            val current = readEnabledServices(context)
            val ourComponent = ourComponentName(context)
            if (current.contains(ourComponent, ignoreCase = true)) {
                return false
            }
            val updated = addComponent(current, ourComponent)
            writeEnabledServices(context, updated)
            // Also make sure global accessibility is on — if every service
            // got disabled the master switch flips off and re-adding our
            // component to the list isn't enough by itself.
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            Log.d(TAG, "Enabled own accessibility service")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted; cannot enable a11y", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable a11y", e)
            false
        }
    }

    fun isOurServiceEnabled(context: Context): Boolean {
        val current = readEnabledServices(context)
        return current.contains(ourComponentName(context), ignoreCase = true)
    }

    private fun readEnabledServices(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
    }

    private fun writeEnabledServices(context: Context, value: String) {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            value
        )
    }

    private fun ourComponentName(context: Context): String {
        return ComponentName(
            context,
            DeepFocusAccessibilityService::class.java
        ).flattenToString()
    }

    private fun removeComponent(current: String, component: String): String {
        if (current.isEmpty()) return ""
        return current.split(":")
            .filter { it.isNotEmpty() && !it.equals(component, ignoreCase = true) }
            .joinToString(":")
    }

    private fun addComponent(current: String, component: String): String {
        return if (current.isEmpty()) {
            component
        } else {
            "$current:$component"
        }
    }
}
