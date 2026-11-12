package com.deepfocus.app.presentation.ui.screens.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepfocus.app.data.model.Habit
import com.deepfocus.app.presentation.ui.theme.DeepFocusType
import com.deepfocus.app.presentation.ui.theme.Ink
import com.deepfocus.app.presentation.viewmodel.HabitsViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Width of the habit-name gutter; the 7 day columns share the rest. */
private val NAME_COLUMN_WIDTH = 96.dp

/** Habit grid: days across the top, habits down the side, tap to check off. */
@Composable
fun HabitsPage(viewModel: HabitsViewModel) {
    val week by viewModel.week.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val checks by viewModel.checks.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Habit?>(null) }

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
            Text(
                text = "Add habit",
                color = Ink.Muted,
                style = DeepFocusType.body,
                modifier = Modifier
                    .clickable { showAddDialog = true }
                    .padding(vertical = 12.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Week navigation. Forward is disabled on the current week — there
        // is nothing to pre-fill in the future.
        // The icon buttons carry 12dp of built-in padding; the offsets put
        // the arrows themselves on the page margin.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = viewModel::previousWeek,
                modifier = Modifier.offset(x = (-12).dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous week",
                    tint = Ink.Muted,
                )
            }
            Text(
                text = week.label,
                color = Ink.Muted,
                style = DeepFocusType.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = viewModel::nextWeek,
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
                        onToggle = viewModel::toggle,
                        onLongPressName = { editTarget = habit },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        HabitNameDialog(
            title = "New habit",
            initialName = "",
            confirmLabel = "Add",
            onConfirm = { name ->
                viewModel.addHabit(name)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editTarget?.let { habit ->
        EditHabitDialog(
            habit = habit,
            onRename = { name ->
                viewModel.renameHabit(habit.id, name)
                editTarget = null
            },
            onDelete = {
                viewModel.deleteHabit(habit.id)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitRow(
    habit: Habit,
    week: com.deepfocus.app.presentation.viewmodel.HabitWeek,
    checks: Set<Pair<Long, Long>>,
    onToggle: (Long, LocalDate) -> Unit,
    onLongPressName: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = habit.name,
            color = Ink.Primary,
            style = DeepFocusType.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(NAME_COLUMN_WIDTH)
                .combinedClickable(onClick = {}, onLongClick = onLongPressName)
                .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val cellModifier = Modifier
            .size(26.dp)
            .then(
                if (checked) {
                    Modifier.background(Ink.Primary, CircleShape)
                } else {
                    val ring = when {
                        isFuture -> Ink.Rule
                        isToday -> Ink.Muted
                        else -> Ink.Faint
                    }
                    Modifier.border(1.dp, ring, CircleShape)
                }
            )
            .clickable(enabled = !isFuture, onClick = onTap)

        // A filled dot is "done", an empty ring is "not yet". Nothing drawn
        // inside either.
        Box(modifier = cellModifier)
    }
}

@Composable
private fun HabitNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161616),
        title = { Text(text = title, color = Color.White) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = Color.White,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(text = confirmLabel, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
    )
}

@Composable
private fun EditHabitDialog(
    habit: Habit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(habit.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161616),
        title = { Text(text = "Edit habit", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = Color.White,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Deleting removes its history too (checks cascade).
                TextButton(onClick = onDelete) {
                    Text(text = "Delete habit and its history", color = Ink.Muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) {
                Text(text = "Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
    )
}
