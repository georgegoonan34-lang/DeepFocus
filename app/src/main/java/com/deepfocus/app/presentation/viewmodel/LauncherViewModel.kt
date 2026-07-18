package com.deepfocus.app.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.data.repository.PinnedAppsRepository
import com.deepfocus.app.util.BlockedApps
import com.deepfocus.app.util.ScheduledApps
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

/** One launchable app as shown by the launcher UI. */
data class LauncherApp(
    val name: String,
    val packageName: String,
    val icon: Drawable,
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

    init {
        refreshApps()
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

        return pm.queryIntentActivities(mainIntent, 0).mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName

            // Skip our own app in the list
            if (packageName == context.packageName) return@mapNotNull null

            val alwaysBlocked = BlockedApps.isBlocked(packageName)
            val scheduledOut = ScheduledApps.isPackageScheduled(packageName) &&
                    !ScheduledApps.isPackageAllowedNow(packageName)

            LauncherApp(
                name = resolveInfo.loadLabel(pm).toString(),
                packageName = packageName,
                icon = resolveInfo.loadIcon(pm),
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
