package com.makd.afinity.ui.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.ui.utils.LocalIsTelevision
import com.makd.afinity.ui.utils.requestFocusSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val SEEK_BAR_SCRUB_COMMIT_DEBOUNCE_MS = 450L

@Composable
internal fun PlayerOverlayBackdrop(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Transparent,
    contentAlignment: Alignment = Alignment.Center,
    handleBack: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (handleBack) {
        BackHandler(onBack = onDismiss)
    }
    Box(modifier = modifier.fillMaxSize().background(scrimColor)) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        )
        Box(
            modifier =
                Modifier.align(contentAlignment)
                    .onPreviewKeyEvent { keyEvent ->
                        if (
                            handleBack &&
                                keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.Escape
                        ) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    }
                    .focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup(),
            content = content,
        )
    }
}

@Composable
internal fun rememberPlayerOverlayInitialFocusRequester(
    key: Any? = Unit,
    enabled: Boolean = true,
): FocusRequester {
    val requester = remember(key) { FocusRequester() }
    val isTelevision = LocalIsTelevision.current
    val inputModeManager = LocalInputModeManager.current
    val shouldFocus = isTelevision || inputModeManager.inputMode == InputMode.Keyboard

    LaunchedEffect(key, enabled, shouldFocus) {
        if (enabled && shouldFocus) {
            requestFocusSafely(requester)
        }
    }

    return requester
}

@Stable
internal class PlayerSeekBarDpadController(
    private val coroutineScope: CoroutineScope,
    private val onPlayerEvent: (PlayerEvent) -> Unit,
    private val onKeepControlsVisible: () -> Unit,
    private val commitDebounceMs: Long = SEEK_BAR_SCRUB_COMMIT_DEBOUNCE_MS,
) {
    var isScrubbing: Boolean by mutableStateOf(false)
        private set

    var scrubPositionMs: Long by mutableLongStateOf(0L)
        private set

    private var commitJob: Job? = null

    fun handleKeyEvent(
        key: Key,
        type: KeyEventType,
        repeatCount: Int,
        currentPositionMs: Long,
        durationMs: Long,
        seekBackMs: Long,
        seekForwardMs: Long,
        isSyncPlay: Boolean,
        onTogglePlayPause: () -> Unit,
    ): Boolean =
        when (key) {
            Key.DirectionLeft,
            Key.DirectionRight -> {
                if (type == KeyEventType.KeyUp) {
                    true
                } else if (type == KeyEventType.KeyDown) {
                    onKeepControlsVisible()
                    if (durationMs > 0L && (!isSyncPlay || repeatCount == 0)) {
                        if (!isScrubbing) {
                            isScrubbing = true
                            scrubPositionMs = currentPositionMs.coerceIn(0L, durationMs)
                            onPlayerEvent(PlayerEvent.OnSeekBarDragStart)
                        }
                        val delta = if (key == Key.DirectionRight) seekForwardMs else -seekBackMs
                        scrubPositionMs = (scrubPositionMs + delta).coerceIn(0L, durationMs)
                        onPlayerEvent(PlayerEvent.OnSeekBarValueChange(scrubPositionMs))
                        commitJob?.cancel()
                        commitJob = coroutineScope.launch {
                            delay(commitDebounceMs)
                            commitIfScrubbing()
                        }
                    }
                    true
                } else {
                    false
                }
            }

            Key.DirectionCenter,
            Key.Enter,
            Key.NumPadEnter -> {
                if (type == KeyEventType.KeyUp) {
                    true
                } else if (type == KeyEventType.KeyDown) {
                    onKeepControlsVisible()
                    if (isScrubbing) {
                        commitIfScrubbing()
                    } else {
                        onTogglePlayPause()
                    }
                    true
                } else {
                    false
                }
            }

            Key.DirectionUp,
            Key.DirectionDown -> {
                if (type == KeyEventType.KeyDown) {
                    commitIfScrubbing()
                }
                false
            }

            else -> false
        }

    fun commitIfScrubbing() {
        commitJob?.cancel()
        commitJob = null
        if (!isScrubbing) return
        val target = scrubPositionMs
        isScrubbing = false
        onPlayerEvent(PlayerEvent.OnSeekBarDragFinished(target))
        onKeepControlsVisible()
    }

    fun cancelForTouchDrag() {
        commitJob?.cancel()
        commitJob = null
        isScrubbing = false
    }
}

@Composable
internal fun rememberPlayerSeekBarDpadController(
    onPlayerEvent: (PlayerEvent) -> Unit,
    onKeepControlsVisible: () -> Unit,
): PlayerSeekBarDpadController {
    val coroutineScope = rememberCoroutineScope()
    val currentOnPlayerEvent by rememberUpdatedState(onPlayerEvent)
    val currentOnKeepControlsVisible by rememberUpdatedState(onKeepControlsVisible)
    return remember(coroutineScope) {
        PlayerSeekBarDpadController(
            coroutineScope = coroutineScope,
            onPlayerEvent = { currentOnPlayerEvent(it) },
            onKeepControlsVisible = { currentOnKeepControlsVisible() },
        )
    }
}
