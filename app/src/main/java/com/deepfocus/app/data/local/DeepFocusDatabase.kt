package com.deepfocus.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.deepfocus.app.data.model.Habit
import com.deepfocus.app.data.model.HabitCheck
import com.deepfocus.app.data.model.PinnedApp

/**
 * Local database for launcher pins and the habit tracker.
 * Blocking config stays in code (BlockedApps/ScheduledApps) on purpose —
 * only user-editable launcher state lives here.
 */
@Database(
    entities = [PinnedApp::class, Habit::class, HabitCheck::class],
    version = 1,
    exportSchema = false,
)
abstract class DeepFocusDatabase : RoomDatabase() {
    abstract fun pinnedAppDao(): PinnedAppDao
    abstract fun habitDao(): HabitDao
}
