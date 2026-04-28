package com.deepfocus.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.deepfocus.app.service.device_admin.DeepFocusDeviceAdmin

/**
 * PACKAGE_USAGE_STATS is a "special" permission — declaring it in the
 * manifest is not enough, the user must enable it manually in
 * Settings → Apps → Special access → Usage data access. Without it
 * UsageStatsManager.queryUsageStats returns an empty list, which is why
 * screen time was always reading zero.
 */
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

fun isDeviceAdminEnabled(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isAdminActive(ComponentName(context, DeepFocusDeviceAdmin::class.java))
}

fun isFullySetUp(context: Context): Boolean {
    return isAccessibilityServiceEnabled(context) &&
            isDeviceAdminEnabled(context) &&
            Settings.canDrawOverlays(context) &&
            hasUsageStatsPermission(context)
}
