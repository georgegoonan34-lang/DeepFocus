package com.deepfocus.app.presentation.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_TYPE = "blocked_type"
        const val TYPE_APP = "app"
        const val TYPE_SHORTS = "shorts"
        const val TYPE_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedType = intent.getStringExtra(EXTRA_BLOCKED_TYPE) ?: TYPE_APP

        setContent {
            DeepFocusTheme {
                BlockedScreen(
                    blockedType = blockedType,
                    onGoBack = { goHome() }
                )
            }
        }
    }

    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        // Go back to home screen
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
    onGoBack: () -> Unit
) {
    val message = when (blockedType) {
        BlockedActivity.TYPE_SHORTS -> "YouTube Shorts\nis blocked."
        BlockedActivity.TYPE_URL -> "This site\nis blocked."
        else -> "This app\nis blocked."
    }

    val submessage = when (blockedType) {
        BlockedActivity.TYPE_SHORTS -> "Endless scrolling won't make you better.\nGo create something."
        BlockedActivity.TYPE_URL -> "This site is a distraction.\nFocus on what matters."
        else -> "You blocked this for a reason.\nStay focused."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 32.sp,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = submessage,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(64.dp))

        TextButton(onClick = onGoBack) {
            Text(
                text = "Go Home",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
