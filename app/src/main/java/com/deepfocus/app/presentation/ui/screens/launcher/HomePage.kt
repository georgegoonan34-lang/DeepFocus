package com.deepfocus.app.presentation.ui.screens.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.ui.theme.DeepFocusType
import com.deepfocus.app.presentation.ui.theme.Ink
import com.deepfocus.app.presentation.viewmodel.LauncherApp
import com.deepfocus.app.presentation.viewmodel.LauncherViewModel
import com.deepfocus.app.presentation.viewmodel.TodayProgress
import com.deepfocus.app.util.ScreenTime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Side margin shared by every launcher page, so the text lines up as you swipe. */
internal val PAGE_MARGIN = 28.dp

/** Space above the first line of every page. Clears the camera cut-out. */
internal val PAGE_TOP = 56.dp

/**
 * Width of the gutter that holds a row's check dot in the app picker. Same
 * as the letter gutter on the All apps page, so the two lists line up.
 */
private val PICK_GUTTER = 28.dp

// The home list settles into place each time you arrive: every row fades up
// a few dp, each one starting a moment after the row above it.
private const val SETTLE_ROW_MS = 280f
private const val SETTLE_STAGGER_MS = 28f

/** Rows past this one all start together, so a long list doesn't drag on. */
private const val SETTLE_ROWS = 9
private const val SETTLE_TOTAL_MS = SETTLE_ROWS * SETTLE_STAGGER_MS + SETTLE_ROW_MS

/** What the top of the home page shows; re-read once a minute. */
private data class Moment(val time: String, val date: String, val batteryPercent: Int?)

/** What the home page is doing with its list of apps. */
private enum class HomeMode {
    /** Tap an app to open it. */
    Normal,

    /** Rows show their move and remove controls. */
    Editing,

    /** The full app list is up, to tick apps on and off the home page. */
    Picking,
}

/**
 * Home: clock, the pinned "important apps" list, and the doors to everything else.
 *
 * [resetSignal] changes whenever the Home button is pressed; the page drops
 * whatever it was editing and goes back to being a plain list.
 */
@Composable
fun HomePage(
    viewModel: LauncherViewModel,
    todayProgress: TodayProgress,
    resetSignal: Int,
    onOpenHabits: () -> Unit,
    onOpenAllApps: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val apps by viewModel.homeApps.collectAsState()
    val allApps by viewModel.allApps.collectAsState()
    val screenTime by viewModel.screenTimeMinutes.collectAsState()
    var moment by remember { mutableStateOf(readMoment(context)) }

    var mode by rememberSaveable { mutableStateOf(HomeMode.Normal) }

    // The picker opens from the plain page (when it is empty) or from edit
    // mode. "Done" goes back to whichever one it came from.
    var pickingFrom by rememberSaveable { mutableStateOf(HomeMode.Normal) }

    // 0 = the list is waiting off-stage, 1 = every row has settled.
    val settle = remember { Animatable(0f) }

    // The clock shows minutes, so wake on each minute boundary rather than
    // every second.
    LaunchedEffect(Unit) {
        while (true) {
            moment = readMoment(context)
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }

    LaunchedEffect(resetSignal) {
        mode = HomeMode.Normal
    }

    // Runs once per visit: from the moment the home screen is shown until the
    // moment it is covered again.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                // Catch the minute that passed while another app was open.
                moment = readMoment(context)
                settle.snapTo(0f)
                settle.animateTo(1f, tween(SETTLE_TOTAL_MS.toInt(), easing = LinearEasing))
                awaitCancellation()
            } finally {
                // Leaving: put the list back off-stage and close any edit, so
                // the next arrival starts clean instead of flashing the old state.
                withContext(NonCancellable) { settle.snapTo(0f) }
                mode = HomeMode.Normal
            }
        }
    }

    if (mode != HomeMode.Normal) {
        HoldPager()
        // Back steps out one level: picker, then edit mode, then the plain page.
        BackHandler {
            mode = if (mode == HomeMode.Picking) pickingFrom else HomeMode.Normal
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeContent(
            time = moment.time,
            date = moment.date,
            batteryPercent = moment.batteryPercent,
            screenTimeMinutes = screenTime,
            apps = apps,
            habitsNote = habitsNote(todayProgress),
            editing = mode == HomeMode.Editing,
            onLaunch = viewModel::launch,
            onEdit = { mode = HomeMode.Editing },
            onDone = { mode = HomeMode.Normal },
            onAddApps = {
                pickingFrom = mode
                mode = HomeMode.Picking
            },
            onRemove = viewModel::unpin,
            onMove = viewModel::move,
            onOpenHabits = onOpenHabits,
            onOpenAllApps = onOpenAllApps,
            settle = { settle.value },
        )

        AnimatedVisibility(
            visible = mode == HomeMode.Picking,
            enter = fadeIn(tween(160)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 12 },
            exit = fadeOut(tween(140)),
        ) {
            AppPicker(
                apps = allApps,
                onToggle = viewModel::togglePin,
                onDone = { mode = pickingFrom },
            )
        }
    }
}

/**
 * [settle] is read while drawing, 0 to 1, and places the list rows during
 * the arrival animation. Previews leave it at 1: everything in place.
 */
@Composable
fun HomeContent(
    time: String,
    date: String,
    batteryPercent: Int?,
    screenTimeMinutes: Long?,
    apps: List<LauncherApp>,
    habitsNote: String?,
    editing: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onAddApps: () -> Unit,
    onRemove: (LauncherApp) -> Unit,
    onMove: (app: LauncherApp, up: Boolean) -> Unit,
    onOpenHabits: () -> Unit,
    onOpenAllApps: () -> Unit,
    settle: () -> Float = { 1f },
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
        // A new minute fades in over the old one instead of snapping.
        AnimatedContent(
            targetState = time,
            transitionSpec = {
                fadeIn(tween(700)) togetherWith fadeOut(tween(350)) using
                        SizeTransform(clip = false)
            },
            label = "clock",
            modifier = Modifier.offset(x = (-3).dp),
        ) { shown ->
            Text(
                text = shown,
                color = Ink.Primary,
                style = DeepFocusType.clock,
            )
        }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nothing here yet. Choose the apps you want one tap away.",
                    color = Ink.Faint,
                    style = DeepFocusType.body,
                )
                // In edit mode the same door is already in the bottom row.
                if (!editing) {
                    TextLink(text = "Choose apps", color = Ink.Primary, onClick = onAddApps)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    HomeAppRow(
                        app = app,
                        editing = editing,
                        canMoveUp = index > 0,
                        canMoveDown = index < apps.lastIndex,
                        onClick = { onLaunch(app) },
                        // Holding a row is the shortcut into edit mode. It
                        // used to remove the app outright, which was too easy
                        // to do by accident and impossible to discover.
                        onLongClick = onEdit,
                        onMoveUp = { onMove(app, true) },
                        onMoveDown = { onMove(app, false) },
                        onRemove = { onRemove(app) },
                        modifier = Modifier
                            .animateItem()
                            .settleIn(index, settle),
                    )
                }

                // The visible way in, at the foot of the list it edits.
                if (!editing) {
                    item(key = "edit-link") {
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .settleIn(apps.size, settle),
                        ) {
                            TextLink(text = "Edit list", color = Ink.Faint, onClick = onEdit)
                        }
                    }
                }
            }
        }

        // The two links sit on the side you'd swipe towards: Habits is the
        // page to the left, All apps the page to the right. While editing,
        // the same row holds the two things you can do next instead.
        AnimatedContent(
            targetState = editing,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "home-footer",
        ) { isEditing ->
            Row(modifier = Modifier.fillMaxWidth()) {
                if (isEditing) {
                    TextLink(text = "Add apps", onClick = onAddApps)
                    Spacer(modifier = Modifier.weight(1f))
                    TextLink(text = "Done", color = Ink.Primary, onClick = onDone)
                } else {
                    HabitsLink(note = habitsNote, onClick = onOpenHabits)
                    Spacer(modifier = Modifier.weight(1f))
                    TextLink(text = "All apps", onClick = onOpenAllApps)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Where one row is in the arrival animation. Read at draw time only. */
private fun Modifier.settleIn(index: Int, settle: () -> Float): Modifier = graphicsLayer {
    val startsAt = minOf(index, SETTLE_ROWS) * SETTLE_STAGGER_MS
    val elapsed = settle() * SETTLE_TOTAL_MS - startsAt
    val progress = FastOutSlowInEasing.transform((elapsed / SETTLE_ROW_MS).coerceIn(0f, 1f))
    alpha = progress
    translationY = (1f - progress) * 12.dp.toPx()
}

@Composable
private fun HomeAppRow(
    app: LauncherApp,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A pinned app can still be blocked (e.g. outside a schedule window):
    // keep it visible but clearly inert.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            // While editing, the row itself does nothing: only its controls do.
            .quietClick(enabled = !editing, onLongClick = onLongClick, onClick = onClick),
    ) {
        Text(
            text = app.name,
            color = if (app.isBlocked) Ink.Faint else Ink.Primary,
            style = DeepFocusType.entry,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        AnimatedVisibility(
            visible = editing,
            enter = fadeIn(tween(220)) + expandHorizontally(tween(220), expandFrom = Alignment.Start),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180), shrinkTowards = Alignment.Start),
        ) {
            // Each control is a 44dp target around a 22dp glyph; the offset
            // puts the last glyph itself on the page margin.
            Row(modifier = Modifier.offset(x = 11.dp)) {
                RowControl(
                    icon = Icons.Filled.KeyboardArrowUp,
                    label = "Move ${app.name} up",
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                )
                RowControl(
                    icon = Icons.Filled.KeyboardArrowDown,
                    label = "Move ${app.name} down",
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                )
                RowControl(
                    icon = Icons.Filled.Close,
                    label = "Remove ${app.name} from the home page",
                    enabled = true,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun RowControl(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .quietClick(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            // The first row can't go up and the last can't go down.
            tint = if (enabled) Ink.Muted else Ink.Rule,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The Habits door, with today's count beside it so you needn't open it to know. */
@Composable
private fun HabitsLink(note: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .quietClick(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = "Habits",
            color = Ink.Muted,
            style = DeepFocusType.body,
            modifier = Modifier.alignByBaseline(),
        )
        if (note != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = note,
                color = Ink.Faint,
                style = DeepFocusType.note,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** "2 of 5 today"; nothing at all until there is a habit to count. */
private fun habitsNote(progress: TodayProgress): String? = when {
    progress.total == 0 -> null
    progress.done >= progress.total -> "all done today"
    else -> "${progress.done} of ${progress.total} today"
}

/**
 * Every installed app with a dot beside it: filled means it is on the home
 * page. Tap a row to flip it. Slides up over the home page and covers it.
 */
@Composable
private fun AppPicker(
    apps: List<LauncherApp>,
    onToggle: (LauncherApp) -> Unit,
    onDone: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }

    val shown = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    // Results change with every letter; keep the best match in view.
    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Blank space here must not pass taps down to the home page underneath.
            .pointerInput(Unit) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = PAGE_MARGIN)
    ) {
        Spacer(modifier = Modifier.height(PAGE_TOP))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Home apps",
                color = Ink.Primary,
                style = DeepFocusType.pageTitle,
                modifier = Modifier.weight(1f),
            )
            TextLink(text = "Done", color = Ink.Primary, onClick = onDone)
        }

        Spacer(modifier = Modifier.height(8.dp))

        LineField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search",
            startPadding = PICK_GUTTER,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            // Nothing to "go" to here; the key just puts the keyboard away.
            onImeAction = { focusManager.clearFocus() },
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.packageName }) { app ->
                PickerRow(app = app, onClick = { onToggle(app) })
            }

            val footer = when {
                shown.isEmpty() -> "No app called “${query.trim()}”."
                query.isBlank() -> "A filled dot means the app is on the home page."
                else -> null
            }
            if (footer != null) {
                item(key = "footer") {
                    Text(
                        text = footer,
                        color = Ink.Faint,
                        style = DeepFocusType.body,
                        modifier = Modifier.padding(
                            start = PICK_GUTTER,
                            top = 20.dp,
                            bottom = 32.dp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(app: LauncherApp, onClick: () -> Unit) {
    // No dimming here: the dot popping in or out is the answer to the tap.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .quietClick(role = Role.Checkbox, dim = false, onClick = onClick),
    ) {
        Box(modifier = Modifier.width(PICK_GUTTER)) {
            CheckDot(checked = app.isPinned, ring = Ink.Faint, size = 14.dp)
        }

        // Blocked apps can still be pinned; they show dimmed, as everywhere else.
        Text(
            text = app.name,
            color = if (app.isBlocked) Ink.Faint else Ink.Primary,
            style = DeepFocusType.entry,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (app.isBlocked && app.statusLabel != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = app.statusLabel,
                color = Ink.Faint,
                style = DeepFocusType.note,
            )
        }
    }
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

private fun previewApps(): List<LauncherApp> =
    listOf("Phone", "Messages", "Maps", "Stretch", "Chrome", "Camera", "Netflix").map { name ->
        LauncherApp(
            name = name,
            packageName = name,
            isBlocked = name == "Netflix",
            statusLabel = if (name == "Netflix") "blocked" else null,
            isPinned = name != "Chrome",
        )
    }

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun HomeContentPreview() {
    DeepFocusTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            HomeContent(
                time = "14:32",
                date = "Thursday 12 November",
                batteryPercent = 82,
                screenTimeMinutes = 72,
                apps = previewApps(),
                habitsNote = "2 of 5 today",
                editing = false,
                onLaunch = {},
                onEdit = {},
                onDone = {},
                onAddApps = {},
                onRemove = {},
                onMove = { _, _ -> },
                onOpenHabits = {},
                onOpenAllApps = {},
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun HomeContentEditingPreview() {
    DeepFocusTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            HomeContent(
                time = "14:32",
                date = "Thursday 12 November",
                batteryPercent = 82,
                screenTimeMinutes = 72,
                apps = previewApps(),
                habitsNote = null,
                editing = true,
                onLaunch = {},
                onEdit = {},
                onDone = {},
                onAddApps = {},
                onRemove = {},
                onMove = { _, _ -> },
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
                habitsNote = null,
                editing = false,
                onLaunch = {},
                onEdit = {},
                onDone = {},
                onAddApps = {},
                onRemove = {},
                onMove = { _, _ -> },
                onOpenHabits = {},
                onOpenAllApps = {},
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun AppPickerPreview() {
    DeepFocusTheme {
        AppPicker(apps = previewApps(), onToggle = {}, onDone = {})
    }
}
