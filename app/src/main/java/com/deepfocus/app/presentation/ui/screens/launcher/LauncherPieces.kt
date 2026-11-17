package com.deepfocus.app.presentation.ui.screens.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepfocus.app.presentation.ui.theme.DeepFocusTheme
import com.deepfocus.app.presentation.ui.theme.DeepFocusType
import com.deepfocus.app.presentation.ui.theme.Ink
import kotlinx.coroutines.launch

/**
 * How many things on screen need the three pages to hold still right now:
 * an open sheet, the home page's edit mode. While it is above zero a
 * sideways swipe does nothing, so you can't slide away from half an edit.
 */
internal val LocalPagerHold = staticCompositionLocalOf { mutableIntStateOf(0) }

/** Keeps the pages from swiping for as long as the caller is on screen. */
@Composable
internal fun HoldPager() {
    val hold = LocalPagerHold.current
    DisposableEffect(hold) {
        hold.intValue++
        onDispose { hold.intValue-- }
    }
}

/**
 * The launcher's one kind of touch feedback. There are no ripples and no
 * highlights on a black page: the thing under your finger dims, then comes
 * back. [dim] is off for rows whose check dot already answers the tap.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.quietClick(
    enabled: Boolean = true,
    role: Role? = null,
    dim: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    // Down fast, back slowly: the dim is felt on the press and seen on the release.
    val strength by animateFloatAsState(
        targetValue = if (pressed && dim) 0.4f else 1f,
        animationSpec = if (pressed) tween(40) else tween(260),
        label = "press",
    )

    graphicsLayer { alpha = strength }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick?.let { hold ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    hold()
                }
            },
            onClick = onClick,
        )
}

/** A word you can tap. The quiet kind of button used all over the launcher. */
@Composable
internal fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink.Muted,
    enabled: Boolean = true,
    style: TextStyle = DeepFocusType.body,
) {
    Text(
        text = text,
        color = if (enabled) color else Ink.Faint,
        style = style,
        modifier = modifier
            .quietClick(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

/**
 * Lets a check dot tell a tap from everything else that changes it. Swiping
 * to another week swaps every dot at once; only the one you touched should
 * get the flourish.
 */
internal class TapArm {
    var armed = false
}

/**
 * A filled dot is "done", an empty ring is "not yet". Nothing drawn inside
 * either. Ticking it pops the dot in with a little overshoot and sends one
 * ring outwards; unticking just lets it go.
 *
 * With no [arm], every change is animated. With one, only changes that
 * follow a tap are.
 */
@Composable
internal fun CheckDot(
    checked: Boolean,
    ring: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    arm: TapArm? = null,
) {
    val fill = remember { Animatable(if (checked) 1f else 0f) }

    // 0 = the ring has just left the dot, 1 = it has faded out completely.
    val burst = remember { Animatable(1f) }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        // First time on screen: already drawn in the right state.
        if (fill.targetValue == target) return@LaunchedEffect

        val flourish = arm == null || arm.armed
        arm?.armed = false

        if (checked && flourish) {
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(560, easing = LinearOutSlowInEasing))
            }
            fill.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f))
        } else {
            burst.snapTo(1f)
            if (flourish) fill.animateTo(target, tween(140)) else fill.snapTo(target)
        }
    }

    // Canvas doesn't clip, so the ring is free to travel past the dot's own box.
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val hairline = 1.dp.toPx()
        val filled = fill.value

        if (filled < 1f) {
            drawCircle(
                color = ring,
                radius = radius - hairline / 2f,
                alpha = (1f - filled).coerceIn(0f, 1f),
                style = Stroke(hairline),
            )
        }
        if (filled > 0f) {
            drawCircle(color = Ink.Primary, radius = radius * filled)
        }

        val travelled = burst.value
        if (travelled < 1f) {
            drawCircle(
                color = Ink.Primary,
                radius = radius * (1f + 0.9f * travelled),
                alpha = (1f - travelled) * 0.55f,
                style = Stroke(hairline * (1.5f - travelled)),
            )
        }
    }
}

/**
 * A text field that is only a line. The underline is the field; it brightens
 * while you type in it. [startPadding] lines the text up with a list below.
 */
@Composable
internal fun LineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = 0.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = DeepFocusType.entry.copy(color = Ink.Primary),
        cursorBrush = SolidColor(Ink.Primary),
        interactionSource = interactionSource,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions { onImeAction() },
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Column {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(start = startPadding),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Ink.Faint,
                            style = DeepFocusType.entry,
                        )
                    }
                    innerTextField()
                }
                HorizontalDivider(color = if (focused) Ink.Muted else Ink.Rule)
            }
        },
    )
}

/**
 * A black sheet that slides up from the bottom edge, for the few things
 * that need a question answered: naming a habit, deleting one.
 *
 * It is drawn inside the page rather than as a system dialog, so it stays
 * pure black, rides up with the keyboard, and never brings the status bar
 * back. [content] gets a `close` to call when it is finished; the sheet
 * slides away first and only then reports [onDismiss].
 */
@Composable
internal fun QuietSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val dismiss by rememberUpdatedState(onDismiss)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    val close = {
        // Take the keyboard down with the sheet, not after it.
        focusManager.clearFocus()
        visible.targetState = false
    }

    // Idle and hidden only happens once the slide-out has finished.
    if (visible.isIdle && !visible.currentState) {
        LaunchedEffect(Unit) { dismiss() }
    }

    HoldPager()
    BackHandler(onBack = close)

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The page behind stays faintly visible, and a tap on it closes the sheet.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .pointerInput(Unit) { detectTapGestures { close() } },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
                        exit = slideOutVertically(tween(180)) { it },
                    )
                    .fillMaxWidth()
                    .background(Color.Black)
                    // Taps on the sheet's own blank space must not fall through to the page.
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                // Black on black: one hairline is the sheet's whole outline.
                HorizontalDivider(color = Ink.Faint)
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                            )
                        )
                        .padding(horizontal = PAGE_MARGIN)
                        .padding(top = 20.dp, bottom = 4.dp),
                ) {
                    content(close)
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 120)
@Composable
private fun CheckDotPreview() {
    DeepFocusTheme {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.Black)
                .padding(24.dp),
        ) {
            CheckDot(checked = true, ring = Ink.Faint)
            Spacer(modifier = Modifier.width(16.dp))
            CheckDot(checked = false, ring = Ink.Muted)
            Spacer(modifier = Modifier.width(16.dp))
            CheckDot(checked = false, ring = Ink.Rule)
            Spacer(modifier = Modifier.width(24.dp))
            TextLink(text = "Add habit", onClick = {})
        }
    }
}

@Preview(widthDp = 360, heightDp = 120)
@Composable
private fun LineFieldPreview() {
    var text by remember { mutableStateOf("") }
    DeepFocusTheme {
        Box(
            modifier = Modifier
                .background(Color.Black)
                .padding(24.dp),
        ) {
            LineField(value = text, onValueChange = { text = it }, placeholder = "Search")
        }
    }
}
