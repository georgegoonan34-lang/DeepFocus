package com.deepfocus.app.presentation.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.service.device_admin.DeepFocusDeviceAdmin
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DeepFocusTheme {
                SetupScreen()
            }
        }
    }
}

@Composable
fun SetupScreen() {
    val context = LocalContext.current

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var deviceAdminEnabled by remember { mutableStateOf(isDeviceAdminEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Refresh permissions when screen is resumed
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = isAccessibilityEnabled(context)
            deviceAdminEnabled = isDeviceAdminEnabled(context)
            overlayEnabled = Settings.canDrawOverlays(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    val allEnabled = accessibilityEnabled && deviceAdminEnabled && overlayEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Setup",
            color = Color.White,
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Grant these permissions to enable app blocking.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Permission items
        PermissionItem(
            title = "Accessibility Service",
            description = "Required to detect and block apps",
            enabled = accessibilityEnabled,
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            title = "Device Administrator",
            description = "Prevents uninstallation from phone",
            enabled = deviceAdminEnabled,
            onClick = {
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
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            title = "Display Over Apps",
            description = "Shows block screen over blocked apps",
            enabled = overlayEnabled,
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        if (allEnabled) {
            Text(
                text = "All permissions granted.\nDeepFocus is now active.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Go home
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text("Done")
            }
        } else {
            Text(
                text = "Enable all permissions above to activate DeepFocus.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (enabled) Color(0xFF1A1A1A) else Color(0xFF0D0D0D),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = if (enabled) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (enabled) Color(0xFF4CAF50) else Color(0xFF666666)
            )
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

private fun isDeviceAdminEnabled(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, DeepFocusDeviceAdmin::class.java)
    return dpm.isAdminActive(adminComponent)
}
