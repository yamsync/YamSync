package io.github.audiz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ThinVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barWidth: Dp = 6.dp,
    onDraggingChange: ((Boolean) -> Unit)? = null
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val height = size.height
                    if (height > 0) {
                        currentOnDraggingChange?.invoke(true)
                        var currentFraction = (1f - (down.position.y / height)).coerceIn(0f, 1f)
                        currentOnValueChange(currentFraction)
                        val pointerId = down.id

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null || !change.pressed) {
                                    break
                                }
                                change.consume()
                                currentFraction = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                                currentOnValueChange(currentFraction)
                            }
                        } finally {
                            currentOnDraggingChange?.invoke(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(barWidth / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
expect fun DesktopVolumeControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 28.dp,
    bottomPlayerPadding: Dp = 0.dp,
    hoverDelayMs: Long = 120L,
    shuffleIconSize: Dp? = null,
)

@Composable
internal fun VolumeButtonIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    buttonSize: Dp,
    shuffleIconSize: Dp?,
) {
    if (shuffleIconSize == null) {
        Icon(icon, contentDescription, Modifier.size(buttonSize * 0.85f), tint)
        return
    }

    // Shuffle occupies 16×16 units of its 24-unit viewport.
    // Keep the speaker slightly larger than shuffle to balance its narrower silhouette.
    val speaker = if (icon == Icons.AutoMirrored.Filled.VolumeMute) {
        Icons.AutoMirrored.Filled.VolumeOff
    } else {
        icon
    }
    val isQuiet = speaker == Icons.AutoMirrored.Filled.VolumeDown
    val glyphWidth = if (isQuiet) 13.5f else 18f
    val glyphHeight = when (speaker) {
        Icons.AutoMirrored.Filled.VolumeDown -> 16f
        Icons.AutoMirrored.Filled.VolumeOff -> 18f
        else -> 17.54f
    }
    Icon(
        imageVector = speaker,
        contentDescription = contentDescription,
        modifier = Modifier.size(shuffleIconSize).graphicsLayer {
            // The quiet speaker tapers to a point and looks smaller at equal bounds.
            scaleX = 1.1f * (if (isQuiet) 16.5f else 16f) / glyphWidth
            scaleY = 1.1f * (if (isQuiet) 18f else 16f) / glyphHeight
        },
        tint = tint,
    )
}
