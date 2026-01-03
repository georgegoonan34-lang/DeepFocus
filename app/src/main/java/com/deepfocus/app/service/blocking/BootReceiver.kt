package com.deepfocus.app.service.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Starts the blocking service when the device boots.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) {
            Log.d(TAG, "Boot completed, starting services")
            startBlockingService(context)
            startScreenTimeService(context)
        }
    }

    private fun startBlockingService(context: Context) {
        val serviceIntent = Intent(context, BlockingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BlockingService", e)
        }
    }

    private fun startScreenTimeService(context: Context) {
        val serviceIntent = Intent(context, ScreenTimeService::class.java)
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenTimeService", e)
        }
    }
}
