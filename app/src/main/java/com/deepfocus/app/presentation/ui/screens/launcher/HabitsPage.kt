package com.deepfocus.app.presentation.ui.screens.launcher

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deepfocus.app.data.model.Habit
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.ui.theme.DeepFocusType
import com.deepfocus.app.presentation.ui.theme.Ink
import com.deepfocus.app.presentation.viewmodel.HabitWeek
import com.deepfocus.app.presentation.viewmodel.HabitsViewModel
import com.deepfocus.app.presentation.viewmodel.TodayProgress
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Width of the habit-name gutter; the 7 day columns share the rest. */
private val NAME_COLUMN_WIDTH = 96.dp

/**
 * Habit grid: days across the top, habits down the side, tap to check off.
 *
 * [resetSignal] changes whenever the Home button is pressed; any open sheet
 * is dropped so the launcher always comes back clean.
 */
@Composable
fun HabitsPage(viewModel: HabitsViewModel, resetSignal: Int) {
    val week by viewModel.week.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val checks by viewModel.checks.collectAsState()
    val today by viewModel.todayProgress.collectAsState()

    var adding by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Habit?>(null) }

    LaunchedEffect(resetSignal) {
        adding = false
        editTarget = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HabitsContent(
            week = week,
            habits = habits,
            checks = checks,
            today = today,
            onAdd = { adding = true },
            onEdit = { editTarget = it },
            onToggle = viewModel::toggle,
            onPreviousWeek = viewModel::previousWeek,
            onNextWeek = viewModel::nextWeek,
        )

        if (adding) {
            HabitSheet(
                heading = "New habit",
                initialName = "",
                confirmLabel = "Add",
                onConfirm = viewModel::addHabit,
                onDelete = null,
                onDismiss = { adding = false },
            )
        }

        editTarget?.let { habit ->
            HabitSheet(
                heading = "Habit",
                initialName = habit.name,
                confirmLabel = "Save",
                onConfirm = { name -> viewModel.renameHabit(habit.id, name) },
                onDelete = { viewModel.deleteHabit(habit.id) },
                onDismiss = { editTarget = null },
            )
        }
    }
}

@Composable
fun HabitsContent(
    week: HabitWeek,
    habits: List<Habit>,
    checks: Set<Pair<Long, Long>>,
    today: TodayProgress,
    onAdd: () -> Unit,
    onEdit: (Habit) -> Unit,
    onToggle: (Long, LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = PAGE_MARGIN)
    ) {
        Spacer(modifier = Modifier.height(PAGE_TOP))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Habits",
                color = Ink.Primary,
                style = DeepFocusType.pageTitle,
                modifier = Modifier.weight(1f),
            )
            TextLink(text = "Add habit", onClick = onAdd)
        }

        // Always about today, whichever week the grid below is showing.
        if (today.total > 0) {
            Text(
                text = if (today.done >= today.total) "All done today"
                else "${today.done} of ${today.total} done today",
                color = Ink.Faint,
                style = DeepFocusType.body,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Week navigation. Forward is disabled on the current week — there
        // is nothing to pre-fill in the future.
        // The icon buttons carry 12dp of built-in padding; the offsets put
        // the arrows themselves on the page margin.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPreviousWeek,
                modifier = Modifier.offset(x = (-12).dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous week",
                    tint = Ink.Muted,
                )
            }
            // The dates fade from one week to the next rather than jumping.
            AnimatedContent(
                targetState = week.label,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "week",
                modifier = Modifier.weight(1f),
            ) { label ->
                Text(
                    text = label,
                    color = Ink.Muted,
                    style = DeepFocusType.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(
                onClick = onNextWeek,
                enabled = !week.isCurrentWeek,
                modifier = Modifier.offset(x = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next week",
                    tint = if (week.isCurrentWeek) Ink.Rule else Ink.Muted,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day header row
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(NAME_COLUMN_WIDTH))
            week.days.forEach { day ->
                val isToday = day == week.today
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = if (isToday) Ink.Primary else Ink.Faint,
                        style = DeepFocusType.note,
                    )
                    Text(
                        text = day.dayOfMonth.toString(),
                        color = if (isToday) Ink.Primary else Ink.Faint,
                        style = DeepFocusType.note,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (habits.isEmpty()) {
            Text(
                text = "No habits yet. Add one, then tick it off each day you do it.",
                color = Ink.Faint,
                style = DeepFocusType.body,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 12.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(habits, key = { it.id }) { habit ->
                    HabitRow(
                        habit = habit,
                        week = week,
                        checks = checks,
                        onToggle = onToggle,
                        onEditName = { onEdit(habit) },
                        // A deleted habit fades out and the rows below close the gap.
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "how-to") {
                    Text(
                        text = "Tap a habit's name to rename or delete it.",
                        color = Ink.Faint,
                        style = DeepFocusType.note,
                        modifier = Modifier
                            .animateItem()
                            .padding(top = 20.dp, bottom = 32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    week: HabitWeek,
    checks: Set<Pair<Long, Long>>,
    onToggle: (Long, LocalDate) -> Unit,
    onEditName: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        // A tap opens the habit's sheet. Holding still works too, for hands
        // that learned it back when that was the only way in.
        Text(
            text = habit.name,
            color = Ink.Primary,
            style = DeepFocusType.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(NAME_COLUMN_WIDTH)
                .quietClick(onLongClick = onEditName, onClick = onEditName)
                .padding(end = 8.dp, top = 10.dp, bottom = 10.dp),
        )
        week.days.forEach { day ->
            val checked = (habit.id to day.toEpochDay()) in checks
            HabitCell(
                checked = checked,
                isFuture = day.isAfter(week.today),
                isToday = day == week.today,
                onTap = { onToggle(habit.id, day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HabitCell(
    checked: Boolean,
    isFuture: Boolean,
    isToday: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val arm = remember { TapArm() }
    val interactionSource = remember { MutableInteractionSource() }

    // The whole cell is the target, not just the 26dp dot in the middle of it.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 42.dp)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                // No ripple: the dot popping is the feedback.
                indication = null,
                enabled = !isFuture,
                role = Role.Checkbox,
                onValueChange = { nowChecked ->
                    arm.armed = true
                    // One small click in the hand for a tick; none for taking it back.
                    if (nowChecked) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onTap()
                },
            ),
    ) {
        // A filled dot is "done", an empty ring is "not yet". Nothing drawn
        // inside either.
        CheckDot(
            checked = checked,
            ring = when {
                isFuture -> Ink.Rule
                isToday -> Ink.Muted
                else -> Ink.Faint
            },
            arm = arm,
        )
    }
}

/**
 * The one sheet for naming a habit. With [onDelete] it is the edit sheet:
 * rename, or delete after a second, explicit yes.
 */
@Composable
private fun HabitSheet(
    heading: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    QuietSheet(onDismiss = onDismiss) { close ->
        var name by remember { mutableStateOf(initialName) }
        var confirmingDelete by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        val valid = HabitsViewModel.isValidName(name)

        fun confirm() {
            if (!valid) return
            onConfirm(name)
            close()
        }

        // A new habit has nothing to read first, so go straight to typing.
        // An existing one opens calm: you may only be here to delete it.
        if (onDelete == null) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }

        AnimatedContent(
            targetState = confirmingDelete,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "habit-sheet",
        ) { confirming ->
            if (!confirming) {
                Column {
                    Text(text = heading, color = Ink.Muted, style = DeepFocusType.body)
                    LineField(
                        value = name,
                        // Typing past the limit simply stops; nothing to explain.
                        onValueChange = { typed ->
                            if (typed.length <= HabitsViewModel.MAX_NAME_LENGTH) name = typed
                        },
                        placeholder = "Name",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        onImeAction = ::confirm,
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDelete != null) {
                            TextLink(text = "Delete", onClick = { confirmingDelete = true })
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextLink(text = "Cancel", onClick = close)
                        Spacer(modifier = Modifier.width(28.dp))
                        TextLink(
                            text = confirmLabel,
                            color = Ink.Primary,
                            enabled = valid,
                            onClick = ::confirm,
                        )
                    }
                }
            } else {
                Column {
                    Text(
                        text = "Delete “$initialName”?",
                        color = Ink.Primary,
                        style = DeepFocusType.entry,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Deleting removes its history too (checks cascade).
                    Text(
                        text = "Every day you ticked it goes too. This can't be undone.",
                        color = Ink.Muted,
                        style = DeepFocusType.body,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextLink(text = "Keep it", onClick = { confirmingDelete = false })
                        Spacer(modifier = Modifier.weight(1f))
                        TextLink(
                            text = "Delete habit",
                            color = Ink.Primary,
                            onClick = {
                                onDelete?.invoke()
                                close()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun HabitsContentPreview() {
    val today = LocalDate.of(2026, 11, 12)
    val monday = today.minusDays(3)
    val habits = listOf("Stretch", "Read", "Walk", "No sugar").mapIndexed { index, name ->
        Habit(id = index + 1L, name = name, position = index, createdAt = 0)
    }
    DeepFocusTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            HabitsContent(
                week = HabitWeek(
                    days = (0L..6L).map { monday.plusDays(it) },
                    label = "9–15 Nov",
                    isCurrentWeek = true,
                    today = today,
                ),
                habits = habits,
                checks = setOf(
                    1L to monday.toEpochDay(),
                    1L to monday.plusDays(1).toEpochDay(),
                    1L to today.toEpochDay(),
                    2L to monday.plusDays(2).toEpochDay(),
                    3L to today.toEpochDay(),
                ),
                today = TodayProgress(done = 2, total = 4),
                onAdd = {},
                onEdit = {},
                onToggle = { _, _ -> },
                onPreviousWeek = {},
                onNextWeek = {},
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun HabitSheetPreview() {
    DeepFocusTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            HabitSheet(
                heading = "Habit",
                initialName = "Stretch",
                confirmLabel = "Save",
                onConfirm = {},
                onDelete = {},
                onDismiss = {},
            )
        }
    }
}
