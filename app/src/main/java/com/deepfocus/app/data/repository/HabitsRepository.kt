package com.deepfocus.app.data.repository

import com.deepfocus.app.data.local.HabitDao
import com.deepfocus.app.data.model.Habit
import com.deepfocus.app.data.model.HabitCheck
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Habits and their per-day check-offs. */
@Singleton
class HabitsRepository @Inject constructor(
    private val dao: HabitDao,
) {
    val habits: Flow<List<Habit>> = dao.habits()

    fun checksForWeek(weekStart: LocalDate): Flow<List<HabitCheck>> =
        dao.checksBetween(weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay())

    suspend fun addHabit(name: String) {
        dao.insert(
            Habit(
                name = name.trim(),
                position = dao.nextPosition(),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun renameHabit(id: Long, name: String) = dao.rename(id, name.trim())

    suspend fun deleteHabit(id: Long) = dao.delete(id)

    suspend fun setChecked(habitId: Long, day: LocalDate, checked: Boolean) {
        val check = HabitCheck(habitId, day.toEpochDay())
        if (checked) dao.insertCheck(check) else dao.deleteCheck(check)
    }
}
