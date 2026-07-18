package com.deepfocus.app.presentation.ui.screens.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepfocus.app.presentation.viewmodel.LauncherApp
import com.deepfocus.app.presentation.viewmodel.LauncherViewModel

/** Everything installed: non-blocked apps first, blocked apps dimmed at the bottom. */
@Composable
fun AllAppsPage(viewModel: LauncherViewModel) {
    val apps by viewModel.allApps.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "All apps",
            color = Color.White,
            fontSize = 28.sp,
        )
        Text(
            text = "Tap to open · long-press to pin or unpin",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                AllAppsRow(
                    app = app,
                    onClick = { viewModel.launch(app) },
                    onLongClick = { viewModel.togglePin(app) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllAppsRow(
    app: LauncherApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Long-press still works on blocked apps (pinning is allowed); tapping
    // them does nothing because launch() refuses blocked apps.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box(modifier = Modifier.alpha(if (app.isBlocked) 0.25f else 1f)) {
            AppIconImage(app = app, size = 40.dp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = app.name,
            color = Color.White.copy(alpha = if (app.isBlocked) 0.3f else 1f),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (app.isPinned) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = "Pinned",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp),
            )
        }

        if (app.isBlocked && app.statusLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = app.statusLabel,
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 11.sp,
            )
        }
    }
}
