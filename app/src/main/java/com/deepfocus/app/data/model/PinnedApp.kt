package com.deepfocus.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An app the user pinned to the launcher's home page.
 * Position is the display order (0 = first).
 */
@Entity(tableName = "pinned_apps")
data class PinnedApp(
    @PrimaryKey val packageName: String,
    val position: Int,
    val pinnedAt: Long,
)
