package com.deepfocus.app.di

import android.content.Context
import androidx.room.Room
import com.deepfocus.app.data.local.DeepFocusDatabase
import com.deepfocus.app.data.local.HabitDao
import com.deepfocus.app.data.local.PinnedAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DeepFocusDatabase =
        Room.databaseBuilder(context, DeepFocusDatabase::class.java, "deepfocus.db").build()

    @Provides
    fun providePinnedAppDao(db: DeepFocusDatabase): PinnedAppDao = db.pinnedAppDao()

    @Provides
    fun provideHabitDao(db: DeepFocusDatabase): HabitDao = db.habitDao()
}
