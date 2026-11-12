package com.deepfocus.app.presentation.ui.screens.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.ui.theme.DeepFocusType
import com.deepfocus.app.presentation.ui.theme.Ink
import com.deepfocus.app.presentation.viewmodel.LauncherApp
import com.deepfocus.app.presentation.viewmodel.LauncherViewModel

/**
 * Width of the gutter that holds the index letter. App names start after
 * it, so the letters hang in the margin like the tabs of an address book.
 */
private val LETTER_GUTTER = 28.dp

/** Everything installed: non-blocked apps first, blocked apps dimmed at the bottom. */
@Composable
fun AllAppsPage(viewModel: LauncherViewModel, isCurrentPage: Boolean) {
    val apps by viewModel.allApps.collectAsState()

    AllAppsContent(
        apps = apps,
        isCurrentPage = isCurrentPage,
        onLaunch = viewModel::launch,
        onTogglePin = viewModel::togglePin,
    )
}

/**
 * [apps] arrive already sorted: non-blocked first, then alphabetical.
 * [isCurrentPage] is false while another launcher page is showing.
 */
@Composable
fun AllAppsContent(
    apps: List<LauncherApp>,
    isCurrentPage: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onTogglePin: (LauncherApp) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()

    // Every visit starts the same way: full list, at the top. Otherwise you
    // come back to wherever you last scrolled, or to an old search.
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            query = ""
            listState.scrollToItem(0)
        }
    }

    // Results change with every letter; keep the best match in view.
    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }

    val shown = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val (blockedApps, openApps) = remember(shown) { shown.partition { it.isBlocked } }

    fun open(app: LauncherApp) {
        onLaunch(app)
        // Come back to a clean list, not to the leftovers of the last search.
        if (!app.isBlocked) {
            query = ""
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = PAGE_MARGIN)
    ) {
        Spacer(modifier = Modifier.height(PAGE_TOP))

        Text(
            text = "All apps",
            color = Ink.Primary,
            style = DeepFocusType.pageTitle,
        )

        Spacer(modifier = Modifier.height(20.dp))

        SearchField(
            query = query,
            onQueryChange = { query = it },
            // The keyboard's Go key opens the top result: type two letters, go.
            onGo = { openApps.firstOrNull()?.let(::open) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(openApps, key = { _, app -> app.packageName }) { index, app ->
                // Mark the first app under each letter. While searching the
                // list is short and no longer a full alphabet, so skip it.
                val letter = indexLetter(app)
                val startsLetter = !searching &&
                        (index == 0 || indexLetter(openApps[index - 1]) != letter)
                AllAppsRow(
                    app = app,
                    gutterLetter = if (startsLetter) letter else null,
                    onClick = { open(app) },
                    onLongClick = { onTogglePin(app) },
                )
            }

            if (blockedApps.isNotEmpty()) {
                item(key = "blocked-header") {
                    Column(modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) {
                        HorizontalDivider(color = Ink.Rule)
                        Text(
                            text = "Blocked",
                            color = Ink.Muted,
                            style = DeepFocusType.body,
                            modifier = Modifier.padding(top = 16.dp, start = LETTER_GUTTER),
                        )
                    }
                }
                items(blockedApps, key = { it.packageName }) { app ->
                    AllAppsRow(
                        app = app,
                        gutterLetter = null,
                        onClick = { open(app) },
                        onLongClick = { onTogglePin(app) },
                    )
                }
            }

            // The how-to line only belongs under the full list; in the
            // middle of a search it's just noise.
            val footer = when {
                shown.isEmpty() -> "No app called “${query.trim()}”."
                !searching -> "Hold an app to keep it on the home page, or to take it off."
                else -> null
            }
            if (footer != null) {
                item(key = "footer") {
                    Text(
                        text = footer,
                        color = Ink.Faint,
                        style = DeepFocusType.body,
                        modifier = Modifier.padding(
                            start = LETTER_GUTTER,
                            top = 20.dp,
                            bottom = 32.dp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onGo: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = DeepFocusType.entry.copy(color = Ink.Primary),
        cursorBrush = SolidColor(Ink.Primary),
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Column {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(start = LETTER_GUTTER),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search",
                            color = Ink.Faint,
                            style = DeepFocusType.entry,
                        )
                    }
                    innerTextField()
                }
                // The underline is the field. It brightens while you type in it.
                HorizontalDivider(color = if (focused) Ink.Muted else Ink.Rule)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllAppsRow(
    app: LauncherApp,
    gutterLetter: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    // Long-press still works on blocked apps (pinning is allowed); tapping
    // them does nothing because launch() refuses blocked apps.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Text(
            text = gutterLetter.orEmpty(),
            color = Ink.Faint,
            style = DeepFocusType.body,
            modifier = Modifier.width(LETTER_GUTTER),
        )

        Text(
            text = app.name,
            color = if (app.isBlocked) Ink.Faint else Ink.Primary,
            style = DeepFocusType.entry,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // One quiet note at the end of the line: when a scheduled app opens
        // up again, or that the app already lives on the home page.
        val note = when {
            app.isBlocked && app.statusLabel != null && app.statusLabel != "blocked" ->
                app.statusLabel
            app.isPinned -> "on home"
            else -> null
        }
        if (note != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = note,
                color = Ink.Faint,
                style = DeepFocusType.note,
            )
        }
    }
}

/** "A" for Amazon, "#" for anything that doesn't start with a letter. */
private fun indexLetter(app: LauncherApp): String {
    val first = app.name.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercase() else "#"
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun AllAppsContentPreview() {
    val open = listOf(
        "Amazon", "Authenticator", "Calculator", "Calendar", "Camera",
        "Chrome", "Clock", "Maps", "Messages", "Roblox",
    )
    val blocked = listOf("Netflix", "Snapchat", "YouTube")
    DeepFocusTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            AllAppsContent(
                apps = open.map { name ->
                    LauncherApp(
                        name = name,
                        packageName = name,
                        isBlocked = false,
                        statusLabel = null,
                        isPinned = name == "Maps" || name == "Roblox",
                    )
                } + blocked.map { name ->
                    LauncherApp(
                        name = name,
                        packageName = name,
                        isBlocked = true,
                        statusLabel = "blocked",
                        isPinned = false,
                    )
                },
                isCurrentPage = true,
                onLaunch = {},
                onTogglePin = {},
            )
        }
    }
}
