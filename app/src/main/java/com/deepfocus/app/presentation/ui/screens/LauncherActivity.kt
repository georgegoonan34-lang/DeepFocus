package com.deepfocus.app.presentation.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.deepfocus.app.util.BlockedApps
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DeepFocusTheme {
                MinimalLauncher()
            }
        }
    }

    override fun onBackPressed() {
        // Do nothing - this is the home screen
    }
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val isBlocked: Boolean
)

@Composable
fun MinimalLauncher() {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    var currentDate by remember { mutableStateOf(getCurrentDate()) }

    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            currentDate = getCurrentDate()
            kotlinx.coroutines.delay(1000)
        }
    }

    val apps = remember { getInstalledApps(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // Time and Date Header - Simple, clean
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = currentTime,
            color = Color.White,
            fontSize = 64.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = currentDate,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(48.dp))

        // App List - Just white text, nothing else
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(apps) { app ->
                AppRow(
                    app = app,
                    onClick = {
                        if (!app.isBlocked) {
                            launchApp(context, app.packageName)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppRow(
    app: AppInfo,
    onClick: () -> Unit
) {
    val textColor = if (app.isBlocked) {
        Color.White.copy(alpha = 0.2f)  // Dimmed for blocked apps
    } else {
        Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !app.isBlocked, onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = app.name,
            color = textColor,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        if (app.isBlocked) {
            Text(
                text = "blocked",
                color = Color.White.copy(alpha = 0.15f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

private fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

private fun getCurrentDate(): String {
    return SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
}

private fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)

    return resolveInfos
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName

            // Skip our own app in the list
            if (packageName == context.packageName) {
                return@mapNotNull null
            }

            val appName = resolveInfo.loadLabel(pm).toString()
            val isBlocked = BlockedApps.isBlocked(packageName)

            AppInfo(
                name = appName,
                packageName = packageName,
                isBlocked = isBlocked
            )
        }
        .sortedWith(compareBy(
            { it.isBlocked },  // Non-blocked apps first
            { it.name.lowercase() }  // Then alphabetically
        ))
}

private fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
