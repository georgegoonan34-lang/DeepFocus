package com.deepfocus.app.presentation.ui.screens.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.viewmodel.HabitsViewModel
import com.deepfocus.app.presentation.viewmodel.LauncherViewModel
import com.deepfocus.app.service.blocking.BlockingService
import com.deepfocus.app.service.blocking.ScreenTimeService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * The phone's home screen. Three swipe pages:
 * Habits (left) — Home with pinned apps (center) — All apps (right).
 */
@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val launcherViewModel: LauncherViewModel by viewModels()
    private val habitsViewModel: HabitsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Foreground-context service start, same as SetupActivity: BootReceiver
        // covers reboots, this covers fresh installs. minSdk is 26 so
        // startForegroundService is always available.
        startForegroundService(Intent(this, BlockingService::class.java))
        startService(Intent(this, ScreenTimeService::class.java))

        setContent {
            DeepFocusTheme {
                LauncherScreen(launcherViewModel, habitsViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The app set and "today" can both change while we're backgrounded.
        launcherViewModel.refreshApps()
        habitsViewModel.refreshToday()
    }
}

private const val HABITS_PAGE = 0
private const val HOME_PAGE = 1
private const val ALL_APPS_PAGE = 2
private const val PAGE_COUNT = 3

@Composable
fun LauncherScreen(
    launcherViewModel: LauncherViewModel,
    habitsViewModel: HabitsViewModel,
) {
    val pagerState = rememberPagerState(initialPage = HOME_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    // Back returns to the Home page; on Home it does nothing — this is the
    // home screen, there is nowhere to go back to.
    BackHandler {
        if (pagerState.currentPage != HOME_PAGE) {
            scope.launch { pagerState.animateScrollToPage(HOME_PAGE) }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        beyondViewportPageCount = 2,
    ) { page ->
        when (page) {
            HABITS_PAGE -> HabitsPage(habitsViewModel)
            HOME_PAGE -> HomePage(
                viewModel = launcherViewModel,
                onOpenAllApps = {
                    scope.launch { pagerState.animateScrollToPage(ALL_APPS_PAGE) }
                },
            )
            ALL_APPS_PAGE -> AllAppsPage(launcherViewModel)
        }
    }
}
