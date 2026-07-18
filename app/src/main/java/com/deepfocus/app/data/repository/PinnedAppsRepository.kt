package com.deepfocus.app.data.repository

import com.deepfocus.app.data.local.PinnedAppDao
import com.deepfocus.app.data.model.PinnedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Pins for the launcher home page, in display order. */
@Singleton
class PinnedAppsRepository @Inject constructor(
    private val dao: PinnedAppDao,
) {
    val pinnedPackages: Flow<List<String>> =
        dao.pinnedApps().map { pins -> pins.map { it.packageName } }

    suspend fun pin(packageName: String) {
        dao.insert(PinnedApp(packageName, dao.nextPosition(), System.currentTimeMillis()))
    }

    suspend fun unpin(packageName: String) = dao.delete(packageName)
}
