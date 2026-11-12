package com.deepfocus.app.presentation.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.ui.theme.DeepFocusType
import com.deepfocus.app.presentation.ui.theme.Ink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_TYPE = "blocked_type"
        const val EXTRA_SCHEDULE_LABEL = "schedule_label"
        const val TYPE_APP = "app"
        const val TYPE_SHORTS = "shorts"
        const val TYPE_URL = "url"
        const val TYPE_TAMPER = "tamper"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedType = intent.getStringExtra(EXTRA_BLOCKED_TYPE) ?: TYPE_APP
        val scheduleLabel = intent.getStringExtra(EXTRA_SCHEDULE_LABEL)

        setContent {
            DeepFocusTheme {
                BlockedScreen(
                    blockedType = blockedType,
                    scheduleLabel = scheduleLabel,
                    onDismiss = { dismiss(blockedType) },
                )
            }
        }
    }

    override fun onBackPressed() {
        val blockedType = intent.getStringExtra(EXTRA_BLOCKED_TYPE) ?: TYPE_APP
        dismiss(blockedType)
    }

    private fun dismiss(blockedType: String) {
        // For browser blocks the accessibility service already pressed Back
        // in the browser before launching us, so the browser is sitting at
        // the previous page. Just finish — the user is returned there. No
        // reason to slam them out to the home screen for a single misclick.
        if (blockedType == TYPE_URL || blockedType == TYPE_SHORTS) {
            finish()
            return
        }
        goHome()
    }

    private fun goHome() {
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
fun BlockedScreen(
    blockedType: String,
    scheduleLabel: String? = null,
    onDismiss: () -> Unit,
) {
    val dismissLabel = when (blockedType) {
        BlockedActivity.TYPE_URL, BlockedActivity.TYPE_SHORTS -> "Go back"
        else -> "Go to home screen"
    }

    val message = when {
        scheduleLabel != null -> "Outside hours."
        blockedType == BlockedActivity.TYPE_SHORTS -> "YouTube Shorts\nis blocked."
        blockedType == BlockedActivity.TYPE_URL -> "This site\nis blocked."
        blockedType == BlockedActivity.TYPE_TAMPER -> "Nice try."
        else -> "This app\nis blocked."
    }

    val submessage = when {
        scheduleLabel != null -> "Allowed $scheduleLabel"
        blockedType == BlockedActivity.TYPE_SHORTS ->
            "Endless scrolling won't make you better.\nGo create something."
        blockedType == BlockedActivity.TYPE_URL ->
            "This site is a distraction.\nFocus on what matters."
        blockedType == BlockedActivity.TYPE_TAMPER ->
            "DeepFocus can't be disabled from this phone.\n\n" +
                    "The only way out is ADB from a computer.\n\n" +
                    "You set this up on purpose. Stay focused."
        else -> "You blocked this for a reason.\nStay focused."
    }

    // Same left-hand margin and type as the launcher pages, so being
    // blocked reads as one more quiet page rather than an alarm.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = Ink.Primary,
            style = DeepFocusType.pageTitle,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = submessage,
            color = Ink.Muted,
            style = DeepFocusType.body,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = dismissLabel,
            color = Ink.Primary,
            style = DeepFocusType.body,
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(vertical = 16.dp),
        )
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun BlockedScreenPreview() {
    DeepFocusTheme {
        BlockedScreen(blockedType = BlockedActivity.TYPE_APP, onDismiss = {})
    }
}
