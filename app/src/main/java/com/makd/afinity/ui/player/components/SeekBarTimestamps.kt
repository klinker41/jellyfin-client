package com.makd.afinity.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.makd.afinity.ui.components.formatClock

internal const val SEEK_BAR_CURRENT_TIME_TEST_TAG = "seek_bar_current_time"
internal const val SEEK_BAR_END_TIME_TEST_TAG = "seek_bar_end_time"

@Immutable
internal data class SeekBarTimeLabels(
    val current: String,
    val end: String,
)

internal fun formatSeekBarTimeLabels(
    positionMs: Long,
    durationMs: Long,
    showRemainingTime: Boolean,
): SeekBarTimeLabels {
    val safePosition = positionMs.coerceAtLeast(0L)
    val safeDuration = durationMs.coerceAtLeast(0L)
    val endLabel =
        if (showRemainingTime) {
            "-${formatClock((safeDuration - safePosition).coerceAtLeast(0L))}"
        } else {
            formatClock(safeDuration)
        }
    return SeekBarTimeLabels(
        current = formatClock(safePosition),
        end = endLabel,
    )
}

@Composable
internal fun SeekBarTimestamps(
    positionMs: Long,
    durationMs: Long,
    showRemainingTime: Boolean,
    onToggleRemainingTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = formatSeekBarTimeLabels(positionMs, durationMs, showRemainingTime)
    val timeLabelStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFeatureSettings = "tnum")

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = labels.current,
            color = Color.White,
            style = timeLabelStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(SEEK_BAR_CURRENT_TIME_TEST_TAG),
        )
        Text(
            text = labels.end,
            color = Color.White.copy(alpha = 0.8f),
            style = timeLabelStyle,
            maxLines = 1,
            softWrap = false,
            modifier =
                Modifier.testTag(SEEK_BAR_END_TIME_TEST_TAG)
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onToggleRemainingTime,
                    ),
        )
    }
}
