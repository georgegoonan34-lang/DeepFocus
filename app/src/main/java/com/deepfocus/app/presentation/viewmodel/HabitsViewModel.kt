package com.deepfocus.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.data.model.Habit
import com.deepfocus.app.data.repository.HabitsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** The Mon–Sun week currently shown by the habit grid. */
data class HabitWeek(
    val days: List<LocalDate>,
    val label: String,
    val isCurrentWeek: Boolean,
    val today: LocalDate,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitsRepository: HabitsRepository,
) : ViewModel() {

    /** 0 = this week, -1 = last week, ... Never positive. */
    private val weekOffset = MutableStateFlow(0)

    /** Kept in state (not read ad hoc) so the grid updates when the date rolls over. */
    private val today = MutableStateFlow(LocalDate.now())

    fun refreshToday() {
        today.value = LocalDate.now()
    }

    val week: StateFlow<HabitWeek> =
        combine(weekOffset, today) { offset, td -> buildWeek(offset, td) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, buildWeek(0, LocalDate.now()))

    val habits: StateFlow<List<Habit>> = habitsRepository.habits
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Checked (habitId, epochDay) pairs for the visible week. */
    val checks: StateFlow<Set<Pair<Long, Long>>> =
        combine(weekOffset, today) { offset, td -> weekStart(offset, td) }
            .flatMapLatest { start -> habitsRepository.checksForWeek(start) }
            .map { list -> list.map { it.habitId to it.epochDay }.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun previousWeek() {
        weekOffset.value -= 1
    }

    fun nextWeek() {
        if (weekOffset.value < 0) weekOffset.value += 1
    }

    fun toggle(habitId: Long, day: LocalDate) {
        if (day.isAfter(today.value)) return // can't check off the future
        val checked = (habitId to day.toEpochDay()) in checks.value
        viewModelScope.launch { habitsRepository.setChecked(habitId, day, !checked) }
    }

    fun addHabit(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { habitsRepository.addHabit(name) }
    }

    fun renameHabit(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { habitsRepository.renameHabit(id, name) }
    }

    fun deleteHabit(id: Long) {
        viewModelScope.launch { habitsRepository.deleteHabit(id) }
    }

    private fun weekStart(offset: Int, td: LocalDate): LocalDate =
        td.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())

    private fun buildWeek(offset: Int, td: LocalDate): HabitWeek {
        val start = weekStart(offset, td)
        val days = (0L..6L).map { start.plusDays(it) }
        val end = days.last()
        val label = if (start.month == end.month) {
            "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(MONTH_FORMAT)}"
        } else {
            "${start.dayOfMonth} ${start.format(MONTH_FORMAT)} – ${end.dayOfMonth} ${end.format(MONTH_FORMAT)}"
        }
        return HabitWeek(days, label, isCurrentWeek = offset == 0, today = td)
    }

    private companion object {
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
    }
}
