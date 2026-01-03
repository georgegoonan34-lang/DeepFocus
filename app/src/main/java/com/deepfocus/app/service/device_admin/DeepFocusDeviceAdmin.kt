package com.deepfocus.app.service.device_admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Admin Receiver that prevents uninstallation of DeepFocus.
 *
 * Once enabled, this can only be disabled from:
 * 1. Settings > Security > Device admin apps (requires user action)
 * 2. ADB commands from computer
 *
 * This is the key to making the blocking truly effective - you can't
 * just uninstall the app when temptation strikes.
 */
class DeepFocusDeviceAdmin : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeepFocusAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device admin enabled")
        Toast.makeText(context, "DeepFocus protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device admin disabled")
        // This shouldn't happen unless done from computer
        Toast.makeText(context, "DeepFocus protection disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Show warning when user tries to disable
        return "Disabling DeepFocus will allow you to uninstall it. " +
                "Are you sure you want to give in to distraction?"
    }
}
