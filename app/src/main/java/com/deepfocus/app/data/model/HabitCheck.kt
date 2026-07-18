package com.deepfocus.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One checked-off day for a habit. epochDay is LocalDate.toEpochDay(),
 * so the composite key makes a check unique per habit per calendar day.
 */
@Entity(
    tableName = "habit_checks",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class HabitCheck(
    val habitId: Long,
    val epochDay: Long,
)
