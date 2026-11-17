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

    /**
     * Put the pins the home page is showing into [visibleOrder].
     *
     * A pin can outlive its app for a while (uninstalled, or hidden from the
     * launcher). Those rows are not in [visibleOrder]; they keep the slot
     * they had, and the visible ones are dealt back into the slots between.
     */
    suspend fun setOrder(visibleOrder: List<String>) {
        val pins = dao.pinnedAppsOnce()
        val byPackage = pins.associateBy { it.packageName }
        val reordered = visibleOrder.mapNotNull { byPackage[it] }.iterator()
        val visible = visibleOrder.toSet()

        val arranged = pins.map { pin ->
            if (pin.packageName in visible && reordered.hasNext()) reordered.next() else pin
        }
        dao.updateAll(arranged.mapIndexed { index, pin -> pin.copy(position = index) })
    }
}
