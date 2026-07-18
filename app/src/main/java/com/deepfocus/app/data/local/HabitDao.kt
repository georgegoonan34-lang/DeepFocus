package com.deepfocus.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepfocus.app.data.model.Habit
import com.deepfocus.app.data.model.HabitCheck
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY position")
    fun habits(): Flow<List<Habit>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM habits")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(habit: Habit): Long

    @Query("UPDATE habits SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM habit_checks WHERE epochDay BETWEEN :startDay AND :endDay")
    fun checksBetween(startDay: Long, endDay: Long): Flow<List<HabitCheck>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheck(check: HabitCheck)

    @Delete
    suspend fun deleteCheck(check: HabitCheck)
}
