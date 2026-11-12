package com.deepfocus.app.presentation.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.ui.theme.DeepFocusType
import com.deepfocus.app.presentation.ui.theme.Ink
import com.deepfocus.app.service.blocking.BlockingService
import com.deepfocus.app.service.device_admin.DeepFocusDeviceAdmin
import com.deepfocus.app.util.hasUsageStatsPermission
import com.deepfocus.app.util.isAccessibilityServiceEnabled
import com.deepfocus.app.util.isDefaultHome
import com.deepfocus.app.util.isDeviceAdminEnabled
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the blocking service from this foreground context.
        // BootReceiver starts it on boot, but a user who installed without
        // rebooting needs a foreground-context start too. Starting from a
        // foreground activity is safe under Android 12+ FGS restrictions.
        val blockingIntent = Intent(this, BlockingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(blockingIntent)
        } else {
            startService(blockingIntent)
        }

        setContent {
            DeepFocusTheme {
                SetupScreen()
            }
        }
    }
}

/** Everything DeepFocus needs switched on, as read from the system right now. */
data class SetupStatus(
    val accessibility: Boolean,
    val deviceAdmin: Boolean,
    val overlay: Boolean,
    val usageAccess: Boolean,
    val homeScreen: Boolean,
) {
    /** How many things are still off. */
    val missing: Int
        get() = listOf(accessibility, deviceAdmin, overlay, usageAccess, homeScreen).count { !it }
}

private fun readSetupStatus(context: Context) = SetupStatus(
    accessibility = isAccessibilityServiceEnabled(context),
    deviceAdmin = isDeviceAdminEnabled(context),
    overlay = Settings.canDrawOverlays(context),
    usageAccess = hasUsageStatsPermission(context),
    homeScreen = isDefaultHome(context),
)

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readSetupStatus(context)) }

    // Refresh permissions when screen is resumed
    LaunchedEffect(Unit) {
        while (true) {
            status = readSetupStatus(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    SetupContent(
        status = status,
        onOpenAccessibility = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onOpenDeviceAdmin = {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(context, DeepFocusDeviceAdmin::class.java)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "DeepFocus needs device admin to prevent you from easily uninstalling it. You can only disable this from your computer."
                )
            }
            context.startActivity(intent)
        },
        onOpenOverlay = {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        },
        onOpenUsageAccess = {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        },
        onGoHome = {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        },
    )
}

@Composable
fun SetupContent(
    status: SetupStatus,
    onOpenAccessibility: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onGoHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "DeepFocus",
            color = Ink.Primary,
            style = DeepFocusType.pageTitle,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (status.missing) {
                0 -> "Everything is on. Blocking is active."
                1 -> "One thing below still needs turning on."
                else -> "${status.missing} things below still need turning on."
            },
            color = Ink.Muted,
            style = DeepFocusType.body,
        )

        Spacer(modifier = Modifier.height(28.dp))

        SetupRow(
            title = "Accessibility",
            description = "Sees which app or site is open so it can block it.",
            enabled = status.accessibility,
            onClick = onOpenAccessibility,
        )
        SetupRow(
            title = "Device admin",
            description = "Stops DeepFocus being uninstalled from the phone.",
            enabled = status.deviceAdmin,
            onClick = onOpenDeviceAdmin,
        )
        SetupRow(
            title = "Display over apps",
            description = "Puts the block screen on top of a blocked app.",
            enabled = status.overlay,
            onClick = onOpenOverlay,
        )
        SetupRow(
            title = "Usage access",
            description = "Counts today's screen time for the home page.",
            enabled = status.usageAccess,
            onClick = onOpenUsageAccess,
        )
        // The phone's own "default home app" screen is off limits once
        // DeepFocus is running (the settings guardian closes any screen
        // that names DeepFocus), so this one is switched on from the
        // computer: install.sh does it as part of every install.
        SetupRow(
            title = "Home screen",
            description = if (status.homeScreen) {
                "The Home button opens the DeepFocus menu."
            } else {
                "Set from your computer: plug the phone in and run install.sh."
            },
            enabled = status.homeScreen,
            actionLabel = "Off",
            onClick = null,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Go to home screen",
            color = Ink.Muted,
            style = DeepFocusType.body,
            modifier = Modifier
                .clickable(onClick = onGoHome)
                .padding(vertical = 16.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * One requirement and its state. Brightness does the work a coloured tick
 * used to: what's already on fades back, what still needs doing stays bright.
 */
@Composable
fun SetupRow(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    actionLabel: String = "Turn on",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Ink.Primary,
                style = DeepFocusType.entry,
            )
            Text(
                text = description,
                color = Ink.Faint,
                style = DeepFocusType.note,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = if (enabled) "On" else actionLabel,
            color = if (enabled) Ink.Faint else Ink.Primary,
            style = DeepFocusType.body,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun SetupContentPreview() {
    DeepFocusTheme {
        SetupContent(
            status = SetupStatus(
                accessibility = true,
                deviceAdmin = true,
                overlay = false,
                usageAccess = true,
                homeScreen = false,
            ),
            onOpenAccessibility = {},
            onOpenDeviceAdmin = {},
            onOpenOverlay = {},
            onOpenUsageAccess = {},
            onGoHome = {},
        )
    }
}
