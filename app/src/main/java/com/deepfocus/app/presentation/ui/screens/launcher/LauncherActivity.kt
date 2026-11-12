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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.viewmodel.HabitsViewModel
import com.deepfocus.app.presentation.viewmodel.LauncherViewModel
import com.deepfocus.app.service.blocking.BlockingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The phone's home screen. Three swipe pages:
 * Habits (left) — Home with pinned apps (center) — All apps (right).
 */
@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val launcherViewModel: LauncherViewModel by viewModels()
    private val habitsViewModel: HabitsViewModel by viewModels()

    /** Bumped each time Home is pressed while we're already showing. */
    private val homePresses = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Foreground-context service start, same as SetupActivity: BootReceiver
        // covers reboots, this covers fresh installs. minSdk is 26 so
        // startForegroundService is always available.
        startForegroundService(Intent(this, BlockingService::class.java))

        setContent {
            DeepFocusTheme {
                LauncherScreen(launcherViewModel, habitsViewModel, homePresses)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar()
        // The app set, screen time and "today" all change while we're backgrounded.
        launcherViewModel.refreshApps()
        launcherViewModel.refreshScreenTime()
        habitsViewModel.refreshToday()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on the launcher: go back to the centre page.
        homePresses.update { it + 1 }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The bar comes back after the notification shade or a dialog closes.
        if (hasFocus) hideStatusBar()
    }

    /**
     * No status bar on the home screen, so no row of notification icons
     * pulling at you every time you pass through. The page shows its own
     * clock and battery; a swipe down from the top still brings the bar
     * and the notification shade back when you actually want them.
     */
    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
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
    homePresses: StateFlow<Int>,
) {
    val pagerState = rememberPagerState(initialPage = HOME_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val homePressCount by homePresses.collectAsState()

    // Back returns to the Home page; on Home it does nothing — this is the
    // home screen, there is nowhere to go back to.
    BackHandler {
        if (pagerState.currentPage != HOME_PAGE) {
            scope.launch { pagerState.animateScrollToPage(HOME_PAGE) }
        }
    }

    LaunchedEffect(homePressCount) {
        if (homePressCount > 0) pagerState.animateScrollToPage(HOME_PAGE)
    }

    // Leaving the All apps page drops the search keyboard with it.
    LaunchedEffect(pagerState.currentPage) {
        focusManager.clearFocus()
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
                onOpenHabits = {
                    scope.launch { pagerState.animateScrollToPage(HABITS_PAGE) }
                },
                onOpenAllApps = {
                    scope.launch { pagerState.animateScrollToPage(ALL_APPS_PAGE) }
                },
            )
            ALL_APPS_PAGE -> AllAppsPage(
                viewModel = launcherViewModel,
                isCurrentPage = pagerState.currentPage == ALL_APPS_PAGE,
            )
        }
    }
}
