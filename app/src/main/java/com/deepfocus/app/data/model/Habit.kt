package com.deepfocus.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A habit tracked on the launcher's habit page. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val createdAt: Long,
)
