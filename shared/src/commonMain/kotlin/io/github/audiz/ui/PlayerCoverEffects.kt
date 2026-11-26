package io.github.audiz.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.audiz.dsp.AudioVisualizer
import io.github.audiz.player.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun AnimatedPlayerCover(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onCoverClick: (() -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null,
    spectrumModifier: Modifier = Modifier,
) {
    val coverCornerRadius = 24.dp
    val coverBitmap = rememberCoverBitmap(state.coverUri, 600)
    // Палитра считается по пикселям обложки — вне UI-потока
    val ambilightPalette by produceState<AmbilightPalette?>(null, coverBitmap) {
        value = coverBitmap?.let { bitmap ->
            withContext(Dispatchers.Default) { extractAmbilightPalette(bitmap) }
        }
    }

    // «Дыхание» подсветки: только во время воспроизведения и на общей сетке ~30 fps
    val isActivelyPlaying = state.isActivelyPlaying
    val breathingAlphaState = remember { mutableFloatStateOf(0.50f) }
    LaunchedEffect(isActivelyPlaying) {
        if (!isActivelyPlaying) {
            breathingAlphaState.floatValue = 0.50f
            return@LaunchedEffect
        }
        val ticker = DecorFrameTicker()
        var startNs = 0L
        while (isActive) {
            ticker.awaitFrame { frameNs, _ ->
                if (startNs == 0L) startNs = frameNs
                val halfCycles = (frameNs - startNs) / 3_200_000_000.0
                val phase = (halfCycles % 2.0).toFloat()
                val progress = if (phase < 1f) phase else 2f - phase
                breathingAlphaState.floatValue = 0.65f + 0.25f * FastOutSlowInEasing.transform(progress)
            }
        }
    }
    val breathingAlpha by breathingAlphaState

    Box(modifier, contentAlignment = Alignment.Center) {
        val glowLeft = ambilightPalette?.left ?: MaterialTheme.colorScheme.primary
        val glowRight = ambilightPalette?.right ?: MaterialTheme.colorScheme.secondary

        // Ореол из 24 градиентных слоёв растеризуется один раз в bitmap (при смене размера
        // или цветов), а каждый кадр выводится одной текстурой с «дышащей» прозрачностью.
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val maxDistance = 46.dp.toPx()
                    val glowImage = if (w > 0f && h > 0f) {
                        renderAmbilightGlow(
                            density = this,
                            width = w,
                            height = h,
                            maxDistance = maxDistance,
                            baseCornerRadius = coverCornerRadius.toPx(),
                            glowLeft = glowLeft,
                            glowRight = glowRight
                        )
                    } else null
                    onDrawBehind {
                        if (glowImage != null) {
                            drawImage(
                                image = glowImage,
                                topLeft = Offset(-maxDistance, -maxDistance),
                                alpha = breathingAlpha.coerceIn(0f, 1f)
                            )
                        }
                    }
                }
        )

        FullPlayerCover(
            state = state,
            bitmap = coverBitmap,
            modifier = Modifier.fillMaxSize(),
            coverCornerRadius = coverCornerRadius,
            onClick = onCoverClick,
        ) {
            CoverSpectrumVisualizer(
                isPlaying = state.isActivelyPlaying,
                glowLeft = glowLeft,
                glowRight = glowRight,
                onOpenEqualizer = onOpenEqualizer,
                coverCornerRadius = coverCornerRadius,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .then(spectrumModifier)
            )
        }
    }
}

/**
 * Растеризует Ambilight-ореол (24 градиентных слоя с гауссовым спадом) в bitmap
 * размером обложки плюс [maxDistance] с каждой стороны.
 */
private fun renderAmbilightGlow(
    density: Density,
    width: Float,
    height: Float,
    maxDistance: Float,
    baseCornerRadius: Float,
    glowLeft: Color,
    glowRight: Color
): ImageBitmap {
    val imageW = (width + 2 * maxDistance).roundToInt().coerceAtLeast(1)
    val imageH = (height + 2 * maxDistance).roundToInt().coerceAtLeast(1)
    val image = ImageBitmap(imageW, imageH)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(imageW.toFloat(), imageH.toFloat())) {
        val steps = 24
        for (i in steps downTo 1) {
            val d = (i.toFloat() / steps) * maxDistance
            val factor = d / maxDistance
            val alpha = (0.13f * exp(-2.2f * factor * factor)).coerceIn(0f, 1f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(glowLeft.copy(alpha = alpha), glowRight.copy(alpha = alpha)),
                    startX = maxDistance - d,
                    endX = maxDistance + width + d
                ),
                topLeft = Offset(maxDistance - d, maxDistance - d),
                size = Size(width + 2 * d, height + 2 * d),
                cornerRadius = CornerRadius(baseCornerRadius + d, baseCornerRadius + d)
            )
        }
    }
    return image
}

data class AmbilightPalette(
    val left: Color,
    val right: Color,
    val center: Color
)

/**
 * 🎨 Безопасное извлечение насыщенных (vibrant) цветов из обложки альбома.
 * Игнорирует белые/черные/серые рамки и находит истинные акцентные цвета арта.
 */
fun extractAmbilightPalette(bitmap: androidx.compose.ui.graphics.ImageBitmap?): AmbilightPalette? {
    if (bitmap == null) return null
    return runCatching {
        val pixelMap = bitmap.toPixelMap()
        val w = pixelMap.width
        val h = pixelMap.height
        if (w <= 0 || h <= 0) return null

        fun findVibrantInRegion(minX: Int, maxX: Int): Color {
            var bestScore = 0f
            var bestColor = Color.Transparent
            val stepX = ((maxX - minX) / 12).coerceAtLeast(1)
            val stepY = (h / 12).coerceAtLeast(1)

            for (x in minX until maxX step stepX) {
                for (y in 0 until h step stepY) {
                    val c = pixelMap[x, y]
                    val rf = c.red
                    val gf = c.green
                    val bf = c.blue

                    val maxVal = maxOf(rf, gf, bf)
                    val minVal = minOf(rf, gf, bf)
                    val delta = maxVal - minVal
                    val saturation = if (maxVal > 0.05f) delta / maxVal else 0f
                    val brightness = maxVal

                    if (brightness in 0.15f..0.98f && saturation > 0.20f) {
                        val score = saturation * brightness
                        if (score > bestScore) {
                            bestScore = score
                            bestColor = c
                        }
                    }
                }
            }
            return if (bestScore > 0f) bestColor else pixelMap[minX + (maxX - minX) / 2, h / 2]
        }

        val leftColor = findVibrantInRegion(0, w / 2)
        val rightColor = findVibrantInRegion(w / 2, w)
        val centerColor = pixelMap[w / 2, h / 2]

        AmbilightPalette(
            left = leftColor,
            right = rightColor,
            center = centerColor
        )
    }.getOrNull()
}

/**
 * Спектр вдоль нижнего края обложки с плавным уменьшением высоты и прозрачности по краям.
 * Цвета следуют палитре обложки; клик открывает эквалайзер.
 */
@Composable
fun CoverSpectrumVisualizer(
    isPlaying: Boolean,
    glowLeft: Color,
    glowRight: Color,
    onOpenEqualizer: (() -> Unit)?,
    coverCornerRadius: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    var frame by remember {
        mutableStateOf(AudioVisualizer.advanceSpectrumFrame(0.016f, isPlaying))
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            frame = AudioVisualizer.advanceSpectrumFrame(0.016f, false)
        } else {
            val ticker = DecorFrameTicker()
            while (isActive) {
                ticker.awaitFrame { _, dt ->
                    frame = AudioVisualizer.advanceSpectrumFrame(dt.coerceIn(0.005f, 0.05f), true)
                }
            }
        }
    }

    val numBars = AudioVisualizer.SPECTRUM_BAND_COUNT
    val heightEnvelope = remember(numBars) {
        FloatArray(numBars) { index ->
            sin(PI * (index + 0.5) / numBars).toFloat().pow(0.8f)
        }
    }
    val edgeOpacity = remember(numBars) {
        FloatArray(numBars) { index ->
            val position = (index + 0.5f) / numBars
            val fade = (minOf(position, 1f - position) / 0.14f).coerceIn(0f, 1f)
            fade * fade * (3f - 2f * fade)
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f / 0.28f)
            .clip(RoundedCornerShape(bottomStart = coverCornerRadius, bottomEnd = coverCornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x08000000),
                        Color(0x33000000),
                        Color(0x80000000)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onOpenEqualizer != null,
                onClick = { onOpenEqualizer?.invoke() }
            )
            .pointerHoverIcon(if (onOpenEqualizer != null) PointerIcon.Hand else PointerIcon.Default)
    ) {
        val bottomInset = maxOf(maxWidth * 0.045f, coverCornerRadius * 0.45f)
        val spectrumHeight = maxWidth * 0.20f
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(spectrumHeight + bottomInset)
                .align(Alignment.BottomCenter)
        ) {
            val totalW = size.width
            val maxH = size.height - bottomInset.toPx()
            if (totalW <= 0f || maxH <= 0f) return@Canvas
            val reflectionTop = maxH + 1.dp.toPx()
            val reflectionScale = ((size.height - reflectionTop) / maxH).coerceIn(0f, 0.30f)

            // Кадр спектра читается только здесь: обновление каждый кадр перерисовывает
            // Canvas, не вызывая рекомпозицию BoxWithConstraints.
            val barLevels = frame.levels
            val capLevels = frame.peakCaps

            val sideInset = maxOf(totalW * 0.035f, coverCornerRadius.toPx() / 3f)
            val availableW = totalW - 2f * sideInset
            if (availableW <= 0f) return@Canvas
            val preferredSpacing = if (numBars >= 25) 2.dp.toPx() else 4.dp.toPx()
            val spacing = minOf(preferredSpacing, availableW / (numBars * 2f))
            val barW = (availableW - (numBars - 1) * spacing) / numBars
            val totalBarsW = availableW
            val startX = sideInset
            val peakH = 2.dp.toPx()

            val barBrush = Brush.horizontalGradient(
                colors = listOf(
                    lerp(glowLeft, Color(0xFFFFF3D4), 0.55f).copy(alpha = 0.98f),
                    lerp(glowRight, Color(0xFFFFF3D4), 0.55f).copy(alpha = 0.98f)
                ),
                startX = startX,
                endX = startX + totalBarsW
            )

            for (i in 0 until numBars) {
                val level = if (i < barLevels.size) barLevels[i] else AudioVisualizer.RESTING_LEVEL
                val envelope = heightEnvelope[i]
                val opacity = edgeOpacity[i]
                val h = (maxH * level.coerceIn(0.08f, 1f)).coerceAtLeast(minOf(barW, maxH)) * envelope
                val x = startX + i * (barW + spacing)
                val y = maxH - h
                val barRadius = CornerRadius(minOf(1.dp.toPx(), barW * 0.2f))

                drawRoundRect(
                    brush = barBrush,
                    topLeft = Offset(x, y),
                    size = Size(barW, h),
                    cornerRadius = barRadius,
                    alpha = opacity
                )

                val capLevel = if (i < capLevels.size) capLevels[i].coerceAtLeast(level) else level
                val taperedPeakH = peakH * envelope
                val targetPeakY = (maxH - (maxH * capLevel.coerceIn(0.08f, 1f) * envelope))
                    .coerceIn(0f, (maxH - taperedPeakH).coerceAtLeast(0f))
                val peakY = targetPeakY.coerceAtMost(y - 2.dp.toPx() * envelope).coerceAtLeast(0f)
                drawRect(
                    color = Color.White.copy(alpha = 0.94f * opacity),
                    topLeft = Offset(x, peakY),
                    size = Size(barW, taperedPeakH)
                )

                if (reflectionScale > 0f) {
                    drawRoundRect(
                        brush = barBrush,
                        topLeft = Offset(x, reflectionTop),
                        size = Size(barW, h * reflectionScale),
                        cornerRadius = barRadius,
                        alpha = opacity * 0.32f
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.22f * opacity),
                        topLeft = Offset(x, reflectionTop + (maxH - peakY - taperedPeakH) * reflectionScale),
                        size = Size(barW, taperedPeakH * reflectionScale)
                    )
                }
            }
        }
    }
}
