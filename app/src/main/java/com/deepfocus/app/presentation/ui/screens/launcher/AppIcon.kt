package com.deepfocus.app.presentation.ui.screens.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.deepfocus.app.presentation.viewmodel.LauncherApp

/**
 * Renders an app's icon. Drawables (including adaptive icons) are rasterized
 * once per app; 128 px is plenty for the sizes we show.
 */
@Composable
fun AppIconImage(app: LauncherApp, size: Dp) {
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(width = 128, height = 128).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}
