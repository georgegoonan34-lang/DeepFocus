package com.deepfocus.app.presentation.ui.screens.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.ui.theme.DeepFocusType
import com.deepfocus.app.presentation.ui.theme.Ink
import com.deepfocus.app.presentation.viewmodel.LauncherApp
import com.deepfocus.app.presentation.viewmodel.LauncherViewModel
import com.deepfocus.app.util.ScreenTime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Side margin shared by every launcher page, so the text lines up as you swipe. */
internal val PAGE_MARGIN = 28.dp

/** Space above the first line of every page. Clears the camera cut-out. */
internal val PAGE_TOP = 56.dp

/** What the top of the home page shows; re-read once a minute. */
private data class Moment(val time: String, val date: String, val batteryPercent: Int?)

/** Home: clock, the pinned "important apps" list, and the doors to everything else. */
@Composable
fun HomePage(
    viewModel: LauncherViewModel,
    onOpenHabits: () -> Unit,
    onOpenAllApps: () -> Unit,
) {
    val context = LocalContext.current
    val apps by viewModel.homeApps.collectAsState()
    val screenTime by viewModel.screenTimeMinutes.collectAsState()
    var moment by remember { mutableStateOf(readMoment(context)) }

    // The clock shows minutes, so wake on each minute boundary rather than
    // every second.
    LaunchedEffect(Unit) {
        while (true) {
            moment = readMoment(context)
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }

    HomeContent(
        time = moment.time,
        date = moment.date,
        batteryPercent = moment.batteryPercent,
        screenTimeMinutes = screenTime,
        apps = apps,
        onLaunch = viewModel::launch,
        onUnpin = viewModel::togglePin,
        onOpenHabits = onOpenHabits,
        onOpenAllApps = onOpenAllApps,
    )
}

@Composable
fun HomeContent(
    time: String,
    date: String,
    batteryPercent: Int?,
    screenTimeMinutes: Long?,
    apps: List<LauncherApp>,
    onLaunch: (LauncherApp) -> Unit,
    onUnpin: (LauncherApp) -> Unit,
    onOpenHabits: () -> Unit,
    onOpenAllApps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = PAGE_MARGIN)
    ) {
        Spacer(modifier = Modifier.height(PAGE_TOP))

        // Large light digits carry a few dp of blank space on their left
        // edge. Pull the clock back so it sits on the same line as the text.
        Text(
            text = time,
            color = Ink.Primary,
            style = DeepFocusType.clock,
            modifier = Modifier.offset(x = (-4).dp),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = date,
                color = Ink.Muted,
                style = DeepFocusType.body,
                modifier = Modifier.weight(1f),
            )
            // The status bar is hidden here, so the page carries the battery.
            if (batteryPercent != null) {
                Text(
                    text = "$batteryPercent%",
                    color = Ink.Faint,
                    style = DeepFocusType.body,
                )
            }
        }

        if (screenTimeMinutes != null) {
            Text(
                text = "${ScreenTime.format(screenTimeMinutes)} on this phone today",
                color = Ink.Faint,
                style = DeepFocusType.body,
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        if (apps.isEmpty()) {
            Text(
                text = "Nothing here yet. Open All apps and hold an app to keep it on this page.",
                color = Ink.Faint,
                style = DeepFocusType.body,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(apps, key = { it.packageName }) { app ->
                    HomeAppRow(
                        app = app,
                        onClick = { onLaunch(app) },
                        onLongClick = { onUnpin(app) },
                    )
                }
            }
        }

        // The two links sit on the side you'd swipe towards: Habits is the
        // page to the left, All apps the page to the right.
        Row(modifier = Modifier.fillMaxWidth()) {
            PageLink(text = "Habits", onClick = onOpenHabits)
            Spacer(modifier = Modifier.weight(1f))
            PageLink(text = "All apps", onClick = onOpenAllApps)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppRow(
    app: LauncherApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    // A pinned app can still be blocked (e.g. outside a schedule window):
    // keep it visible but clearly inert.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Text(
            text = app.name,
            color = if (app.isBlocked) Ink.Faint else Ink.Primary,
            style = DeepFocusType.entry,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PageLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Ink.Muted,
        style = DeepFocusType.body,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

private fun readMoment(context: Context): Moment {
    val locale = Locale.getDefault()
    val now = Date()
    // Follow the phone's 12/24-hour setting and the locale's day/month order.
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    return Moment(
        time = SimpleDateFormat(timePattern, locale).format(now),
        date = SimpleDateFormat(datePattern, locale).format(now),
        batteryPercent = readBatteryPercent(context),
    )
}

private fun readBatteryPercent(context: Context): Int? {
    // ACTION_BATTERY_CHANGED is sticky: registering with no receiver just
    // hands back the latest reading.
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return null
    val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else null
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun HomeContentPreview() {
    val names = listOf("Phone", "Messages", "Maps", "Roblox", "Chrome", "Camera", "Netflix")
    DeepFocusTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            HomeContent(
                time = "14:32",
                date = "Thursday 12 November",
                batteryPercent = 82,
                screenTimeMinutes = 72,
                apps = names.map { name ->
                    LauncherApp(
                        name = name,
                        packageName = name,
                        isBlocked = name == "Netflix",
                        statusLabel = null,
                        isPinned = true,
                    )
                },
                onLaunch = {},
                onUnpin = {},
                onOpenHabits = {},
                onOpenAllApps = {},
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun HomeContentEmptyPreview() {
    DeepFocusTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            HomeContent(
                time = "9:05",
                date = "Thursday 12 November",
                batteryPercent = 100,
                screenTimeMinutes = null,
                apps = emptyList(),
                onLaunch = {},
                onUnpin = {},
                onOpenHabits = {},
                onOpenAllApps = {},
            )
        }
    }
}
