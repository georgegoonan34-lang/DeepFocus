package com.deepfocus.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deepfocus.app.data.model.PinnedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedAppDao {

    @Query("SELECT * FROM pinned_apps ORDER BY position")
    fun pinnedApps(): Flow<List<PinnedApp>>

    /** The same list, read once. Used when the order is being rewritten. */
    @Query("SELECT * FROM pinned_apps ORDER BY position")
    suspend fun pinnedAppsOnce(): List<PinnedApp>

    /** Rows are matched by package name, so this only rewrites positions. */
    @Update
    suspend fun updateAll(pins: List<PinnedApp>)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM pinned_apps")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pin: PinnedApp)

    @Query("DELETE FROM pinned_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
