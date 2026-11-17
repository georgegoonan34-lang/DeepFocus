package com.deepfocus.app.presentation.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.data.repository.PinnedAppsRepository
import com.deepfocus.app.util.BlockedApps
import com.deepfocus.app.util.ScheduledApps
import com.deepfocus.app.util.ScreenTime
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One launchable app as shown by the launcher UI. Name only — the launcher
 * is a plain text menu, so icons are never loaded.
 */
data class LauncherApp(
    val name: String,
    val packageName: String,
    val isBlocked: Boolean,
    val statusLabel: String?,
    val isPinned: Boolean,
)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pinnedAppsRepository: PinnedAppsRepository,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<LauncherApp>>(emptyList())

    private val _screenTimeMinutes = MutableStateFlow<Long?>(null)

    /** Today's screen time for the home page; null until Usage Access is granted. */
    val screenTimeMinutes: StateFlow<Long?> = _screenTimeMinutes

    init {
        refreshApps()
        refreshScreenTime()
    }

    /** Re-read on every return to the home screen — that's when it has changed. */
    fun refreshScreenTime() {
        viewModelScope.launch(Dispatchers.IO) {
            _screenTimeMinutes.value = ScreenTime.todayMinutes(context)
        }
    }

    /**
     * Reload the launchable app list. Called again on every launcher resume
     * because installs, uninstalls, and schedule windows change while we're
     * backgrounded.
     */
    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            installedApps.value = loadInstalledApps()
        }
    }

    /** Pinned apps in pin order — the home page grid. */
    val homeApps: StateFlow<List<LauncherApp>> =
        combine(installedApps, pinnedAppsRepository.pinnedPackages) { apps, pins ->
            val byPackage = apps.associateBy { it.packageName }
            pins.mapNotNull { byPackage[it]?.copy(isPinned = true) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every launchable app: non-blocked first, then alphabetical (old launcher order). */
    val allApps: StateFlow<List<LauncherApp>> =
        combine(installedApps, pinnedAppsRepository.pinnedPackages) { apps, pins ->
            apps.map { it.copy(isPinned = it.packageName in pins) }
                .sortedWith(compareBy({ it.isBlocked }, { it.name.lowercase() }))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun togglePin(app: LauncherApp) {
        viewModelScope.launch {
            if (app.isPinned) pinnedAppsRepository.unpin(app.packageName)
            else pinnedAppsRepository.pin(app.packageName)
        }
    }

    /** Take an app off the home page. Does nothing if it isn't there. */
    fun unpin(app: LauncherApp) {
        viewModelScope.launch { pinnedAppsRepository.unpin(app.packageName) }
    }

    /**
     * Move a home-page app one place [up] or down. Works on the list as the
     * page shows it, so the app swaps with the neighbour you can see.
     */
    fun move(app: LauncherApp, up: Boolean) {
        val order = homeApps.value.map { it.packageName }.toMutableList()
        val from = order.indexOf(app.packageName)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in order.indices) return

        order[from] = order[to]
        order[to] = app.packageName
        viewModelScope.launch { pinnedAppsRepository.setOrder(order) }
    }

    fun launch(app: LauncherApp) {
        // The launcher never opens blocked apps; the accessibility service
        // stays as the backstop for launches from anywhere else.
        if (app.isBlocked) return
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun loadInstalledApps(): List<LauncherApp> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // An app can expose several launcher entries; the list is keyed by
        // package, so keep one row per app.
        return pm.queryIntentActivities(mainIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName

            // Skip our own app in the list
            if (packageName == context.packageName) return@mapNotNull null

            val alwaysBlocked = BlockedApps.isBlocked(packageName)
            val scheduledOut = ScheduledApps.isPackageScheduled(packageName) &&
                    !ScheduledApps.isPackageAllowedNow(packageName)

            LauncherApp(
                name = resolveInfo.loadLabel(pm).toString(),
                packageName = packageName,
                isBlocked = alwaysBlocked || scheduledOut,
                statusLabel = when {
                    alwaysBlocked -> "blocked"
                    scheduledOut -> ScheduledApps.packageScheduleLabel(packageName)
                    else -> null
                },
                isPinned = false,
            )
        }
    }
}
