package com.makd.afinity.ui.components

import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.LinearGradient
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.utils.rememberTopBarScrollProgress
import com.makd.afinity.ui.utils.topBarScrollKey
import kotlin.math.ceil
import kotlin.math.roundToInt

val DefaultTopBarMaxBlurRadius = 20.dp
val DefaultTopBarBlurBottomExtension = 16.dp
const val DefaultTopBarMaxScrimAlpha = 0.25f
internal const val MIN_BLUR_RADIUS_PX = 0.5f
internal const val TOP_BAR_BLUR_DOWNSCALE = 0.25f
internal const val TOP_BAR_BLUR_FADE_START_FRACTION = 0.55f

internal fun computeTopBarBlurHeightPx(barHeightPx: Float, bottomExtensionPx: Float): Float =
    if (barHeightPx < 1f) 0f else barHeightPx + bottomExtensionPx.coerceAtLeast(0f)

internal fun computeTopBarBlurLayerSizePx(
    barWidthPx: Int,
    blurHeightPx: Float,
    maxRadiusAtFullScrollPx: Float,
    scale: Float = TOP_BAR_BLUR_DOWNSCALE,
): IntSize {
    if (barWidthPx <= 0 || blurHeightPx < 1f || scale <= 0f) return IntSize.Zero
    val scaledWidth = ceil(barWidthPx * scale).toInt().coerceAtLeast(1)
    val scaledBlurHeight = ceil(blurHeightPx * scale).toInt().coerceAtLeast(1)
    val scaledPad = ceil(maxRadiusAtFullScrollPx.coerceAtLeast(0f) * scale).toInt().coerceAtLeast(0)
    return IntSize(scaledWidth, scaledBlurHeight + scaledPad)
}

internal fun computeTopBarScrimAlpha(progress: Float, maxScrimAlpha: Float): Float =
    maxScrimAlpha.coerceIn(0f, 1f) * progress.coerceIn(0f, 1f)

internal fun quantizeBlurRadiusPx(radiusPx: Float): Float =
    if (radiusPx < MIN_BLUR_RADIUS_PX) 0f else (radiusPx * 2f).roundToInt() / 2f

private val SmoothstepPositions = floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f)
private val SmoothstepColors =
    intArrayOf(
        0xFFFFFFFF.toInt(),
        0xBDFFFFFF.toInt(),
        0x42FFFFFF.toInt(),
        0x00000000,
    )

private fun createSmoothstepMaskEffect(topY: Float, bottomY: Float): AndroidRenderEffect {
    val shader =
        LinearGradient(
            0f,
            topY,
            0f,
            maxOf(bottomY, topY + 1f),
            SmoothstepColors,
            SmoothstepPositions,
            Shader.TileMode.CLAMP,
        )
    return AndroidRenderEffect.createShaderEffect(shader)
}

@Stable
class TopBarBlurState
internal constructor(
    internal val contentLayer: GraphicsLayer,
    internal val blurLayer: GraphicsLayer,
    internal val progressProvider: () -> Float,
    internal val scrollKeyProvider: () -> Int,
    val maxBlurRadius: Dp,
) {
    private val isBlurActiveState = derivedStateOf { progressProvider() > 0f }

    val isBlurActive: Boolean
        get() = isBlurActiveState.value

    internal var hasRecordedContent: Boolean = false
    internal var sourcePositionInRoot: Offset = Offset.Zero
    internal var backdropPositionInRoot: Offset = Offset.Zero
    internal var invalidateBackdrop: (() -> Unit)? = null

    private var cachedBlurHeightPx: Float = -1f
    private var cachedMask: AndroidRenderEffect? = null
    private var cachedQuantizedRadiusPx: Float = -1f
    private var cachedRenderEffect: RenderEffect? = null

    internal fun clearCachedEffect() {
        if (blurLayer.renderEffect != null) {
            blurLayer.renderEffect = null
        }
        cachedQuantizedRadiusPx = -1f
        cachedRenderEffect = null
    }

    internal fun getOrCreateRenderEffect(
        scaledBlurHeightPx: Float,
        scaledRadiusPx: Float,
    ): RenderEffect? {
        val quantizedRadius = quantizeBlurRadiusPx(scaledRadiusPx)
        if (quantizedRadius < MIN_BLUR_RADIUS_PX || scaledBlurHeightPx < 1f) return null

        val mask =
            if (cachedMask == null || cachedBlurHeightPx != scaledBlurHeightPx) {
                createSmoothstepMaskEffect(
                        topY = scaledBlurHeightPx * TOP_BAR_BLUR_FADE_START_FRACTION,
                        bottomY = scaledBlurHeightPx,
                    )
                    .also {
                        cachedMask = it
                        cachedBlurHeightPx = scaledBlurHeightPx
                        cachedQuantizedRadiusPx = -1f
                        cachedRenderEffect = null
                    }
            } else {
                cachedMask!!
            }

        if (cachedQuantizedRadiusPx == quantizedRadius && cachedRenderEffect != null) {
            return cachedRenderEffect
        }

        val blur =
            AndroidRenderEffect.createBlurEffect(
                quantizedRadius,
                quantizedRadius,
                Shader.TileMode.CLAMP,
            )
        return AndroidRenderEffect.createBlendModeEffect(blur, mask, AndroidBlendMode.DST_IN)
            .asComposeRenderEffect()
            .also {
                cachedQuantizedRadiusPx = quantizedRadius
                cachedRenderEffect = it
            }
    }
}

@Composable
fun rememberTopBarBlurState(
    progress: () -> Float,
    scrollKey: () -> Int = { 0 },
    maxBlurRadius: Dp = DefaultTopBarMaxBlurRadius,
): TopBarBlurState {
    val currentProgress = rememberUpdatedState(progress)
    val currentScrollKey = rememberUpdatedState(scrollKey)
    val contentLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    return remember(contentLayer, blurLayer, maxBlurRadius) {
        TopBarBlurState(
            contentLayer = contentLayer,
            blurLayer = blurLayer,
            progressProvider = { currentProgress.value() },
            scrollKeyProvider = { currentScrollKey.value() },
            maxBlurRadius = maxBlurRadius,
        )
    }
}

@Composable
fun rememberDetailTopBarBlurState(
    listState: LazyListState,
    hasHeroSlot: Boolean = !isLandscapeWindow(),
): TopBarBlurState {
    val progressState =
        rememberTopBarScrollProgress(
            state = listState,
            hasHeroSlot = hasHeroSlot,
            rampItemIndex = 0,
        )
    return rememberTopBarBlurState(
        progress = { progressState.value },
        scrollKey = { listState.topBarScrollKey() },
    )
}

fun Modifier.topBarBlurSource(state: TopBarBlurState): Modifier =
    this.then(TopBarBlurSourceElement(state))

fun Modifier.topBarBlurBackdrop(
    state: TopBarBlurState,
    containerColor: Color,
    scrimColor: Color = containerColor,
    maxScrimAlpha: Float = DefaultTopBarMaxScrimAlpha,
    bottomExtension: Dp = DefaultTopBarBlurBottomExtension,
): Modifier =
    this.then(
        TopBarBlurBackdropElement(
            state,
            containerColor,
            scrimColor,
            maxScrimAlpha,
            bottomExtension,
        ),
    )

private data class TopBarBlurSourceElement(private val state: TopBarBlurState) :
    ModifierNodeElement<TopBarBlurSourceNode>() {
    override fun create(): TopBarBlurSourceNode = TopBarBlurSourceNode(state)

    override fun update(node: TopBarBlurSourceNode) {
        if (node.state !== state) {
            node.state.hasRecordedContent = false
            node.state.invalidateBackdrop?.invoke()
            node.state = state
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "topBarBlurSource"
        properties["state"] = state
    }
}

private class TopBarBlurSourceNode(var state: TopBarBlurState) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val newPosition = coordinates.positionInRoot()
        if (state.sourcePositionInRoot != newPosition) {
            state.sourcePositionInRoot = newPosition
            if (state.isBlurActive) {
                state.invalidateBackdrop?.invoke()
            }
        }
    }

    override fun onDetach() {
        state.hasRecordedContent = false
        state.invalidateBackdrop?.invoke()
    }

    override fun ContentDrawScope.draw() {
        if (!state.isBlurActive) {
            state.hasRecordedContent = false
            drawContent()
            return
        }

        // Subscribe to vertical scroll offset changes so contentLayer re-records even after
        // progress saturates at 1f.
        state.scrollKeyProvider()
        state.contentLayer.record { this@draw.drawContent() }
        state.hasRecordedContent = true
        drawLayer(state.contentLayer)
    }
}

private data class TopBarBlurBackdropElement(
    private val state: TopBarBlurState,
    private val containerColor: Color,
    private val scrimColor: Color,
    private val maxScrimAlpha: Float,
    private val bottomExtension: Dp,
) : ModifierNodeElement<TopBarBlurBackdropNode>() {
    override fun create(): TopBarBlurBackdropNode =
        TopBarBlurBackdropNode(state, containerColor, scrimColor, maxScrimAlpha, bottomExtension)

    override fun update(node: TopBarBlurBackdropNode) {
        val stateChanged = node.state !== state
        val appearanceChanged =
            node.containerColor != containerColor ||
                node.scrimColor != scrimColor ||
                node.maxScrimAlpha != maxScrimAlpha ||
                node.bottomExtension != bottomExtension
        if (stateChanged) {
            node.state.invalidateBackdrop = null
            node.state.clearCachedEffect()
            node.state = state
            if (node.isAttached) {
                state.invalidateBackdrop = { node.invalidateDraw() }
            }
        }
        if (appearanceChanged) {
            node.containerColor = containerColor
            node.scrimColor = scrimColor
            node.maxScrimAlpha = maxScrimAlpha
            node.bottomExtension = bottomExtension
        }
        if (stateChanged || appearanceChanged) {
            node.invalidateScrimCache()
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "topBarBlurBackdrop"
        properties["state"] = state
        properties["containerColor"] = containerColor
        properties["scrimColor"] = scrimColor
        properties["maxScrimAlpha"] = maxScrimAlpha
        properties["bottomExtension"] = bottomExtension
    }
}

private class TopBarBlurBackdropNode(
    var state: TopBarBlurState,
    var containerColor: Color,
    var scrimColor: Color,
    var maxScrimAlpha: Float,
    var bottomExtension: Dp,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var cachedScrimBrush: Brush? = null
    private var cachedScrimColor: Color = Color.Unspecified
    private var cachedScrimAlpha: Float = -1f
    private var cachedScrimHeightPx: Float = -1f

    internal fun invalidateScrimCache() {
        cachedScrimBrush = null
        cachedScrimColor = Color.Unspecified
        cachedScrimAlpha = -1f
        cachedScrimHeightPx = -1f
    }

    private fun getOrCreateScrimBrush(alpha: Float, heightPx: Float): Brush {
        val existing = cachedScrimBrush
        if (
            existing != null &&
                cachedScrimColor == scrimColor &&
                cachedScrimAlpha == alpha &&
                cachedScrimHeightPx == heightPx
        ) {
            return existing
        }
        return Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0.00f to scrimColor.copy(alpha = alpha),
                        0.55f to scrimColor.copy(alpha = alpha * 0.45f),
                        1.00f to Color.Transparent,
                    ),
                startY = 0f,
                endY = heightPx,
            )
            .also {
                cachedScrimBrush = it
                cachedScrimColor = scrimColor
                cachedScrimAlpha = alpha
                cachedScrimHeightPx = heightPx
            }
    }

    override fun onAttach() {
        state.invalidateBackdrop = { invalidateDraw() }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        state.backdropPositionInRoot = coordinates.positionInRoot()
    }

    override fun onDetach() {
        state.invalidateBackdrop = null
        state.clearCachedEffect()
        invalidateScrimCache()
    }

    override fun ContentDrawScope.draw() {
        val progress = state.progressProvider().coerceIn(0f, 1f)
        if (progress <= 0f || !state.hasRecordedContent) {
            state.clearCachedEffect()
            invalidateScrimCache()
            drawContent()
            return
        }

        // Subscribe to scroll offset changes so blurLayer refreshes the cropped strip even when
        // progress stays pinned at 1f.
        state.scrollKeyProvider()

        val barWidthPx = size.width.roundToInt()
        val blurHeightPx = computeTopBarBlurHeightPx(size.height, bottomExtension.toPx())
        if (barWidthPx <= 0 || blurHeightPx < 1f) {
            state.clearCachedEffect()
            invalidateScrimCache()
            drawContent()
            return
        }

        val scale = TOP_BAR_BLUR_DOWNSCALE
        val maxRadiusAtFullScrollPx = state.maxBlurRadius.toPx()
        val scaledBlurHeightPx = (blurHeightPx * scale).coerceAtLeast(1f)
        val scaledCurrentRadiusPx = maxRadiusAtFullScrollPx * progress * scale
        val effect = state.getOrCreateRenderEffect(scaledBlurHeightPx, scaledCurrentRadiusPx)
        val layerSize =
            computeTopBarBlurLayerSizePx(barWidthPx, blurHeightPx, maxRadiusAtFullScrollPx, scale)
        if (effect == null || layerSize == IntSize.Zero) {
            state.clearCachedEffect()
            invalidateScrimCache()
            drawContent()
            return
        }

        val offset = state.sourcePositionInRoot - state.backdropPositionInRoot

        state.blurLayer.clip = true
        state.blurLayer.renderEffect = effect
        state.blurLayer.record(size = layerSize) {
            drawRect(color = containerColor)
            translate(left = offset.x * scale, top = offset.y * scale) {
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
                    drawLayer(state.contentLayer)
                }
            }
        }

        val invScale = 1f / scale
        clipRect(left = 0f, top = 0f, right = size.width, bottom = blurHeightPx) {
            scale(scaleX = invScale, scaleY = invScale, pivot = Offset.Zero) {
                drawLayer(state.blurLayer)
            }
        }

        val scrimAlpha = computeTopBarScrimAlpha(progress, maxScrimAlpha)
        if (scrimAlpha > 0f) {
            drawRect(
                brush = getOrCreateScrimBrush(scrimAlpha, blurHeightPx),
                size = Size(size.width, blurHeightPx),
            )
        } else {
            invalidateScrimCache()
        }

        drawContent()
    }
}
