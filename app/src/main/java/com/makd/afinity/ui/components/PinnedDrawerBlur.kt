package com.makd.afinity.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

val DefaultPinnedDrawerBlurRadius = 20.dp
const val DefaultPinnedDrawerTintAlpha = 0.66f

internal fun computeBlurredLayerSizePx(widthPx: Int, heightPx: Int, blurPaddingPx: Int): IntSize {
    if (widthPx <= 0 || heightPx <= 0) return IntSize.Zero
    val pad = blurPaddingPx.coerceAtLeast(0)
    return IntSize(widthPx + pad * 2, heightPx + pad * 2)
}

internal fun computeClampedTintAlpha(alpha: Float): Float = alpha.coerceIn(0f, 1f)

@Stable
class PinnedDrawerBlurState
internal constructor(
    internal val contentLayer: GraphicsLayer,
    internal val blurLayer: GraphicsLayer,
    internal val enabledProvider: () -> Boolean,
    val blurRadius: Dp,
) {
    private val isEnabledState = derivedStateOf { enabledProvider() }

    val isEnabled: Boolean
        get() = isEnabledState.value

    internal var hasRecordedContent: Boolean = false
    internal var hasBackdropDrawnOnce: Boolean = false
    internal var sourcePositionInRoot: Offset = Offset.Zero
    internal var backdropPositionInRoot: Offset = Offset.Zero
    internal var invalidateSource: (() -> Unit)? = null
    internal var invalidateBackdrop: (() -> Unit)? = null

    private var cachedQuantizedRadiusPx: Float = -1f
    private var cachedRenderEffect: RenderEffect? = null

    internal val nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (isEnabled && (consumed.x != 0f || consumed.y != 0f)) {
                    notifyContentChanged()
                }
                return Offset.Zero
            }
        }

    internal fun notifyContentChanged() {
        if (isEnabled) {
            invalidateSource?.invoke()
            invalidateBackdrop?.invoke()
        }
    }

    internal fun clearCachedEffect() {
        if (blurLayer.renderEffect != null) {
            blurLayer.renderEffect = null
        }
        cachedQuantizedRadiusPx = -1f
        cachedRenderEffect = null
    }

    internal fun getOrCreateRenderEffect(radiusPx: Float): RenderEffect? {
        val quantized = quantizeBlurRadiusPx(radiusPx)
        if (quantized < MIN_BLUR_RADIUS_PX) return null
        if (cachedQuantizedRadiusPx == quantized && cachedRenderEffect != null) {
            return cachedRenderEffect
        }
        return AndroidRenderEffect.createBlurEffect(quantized, quantized, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
            .also {
                cachedQuantizedRadiusPx = quantized
                cachedRenderEffect = it
            }
    }
}

@Composable
fun rememberPinnedDrawerBlurState(
    enabled: () -> Boolean,
    blurRadius: Dp = DefaultPinnedDrawerBlurRadius,
): PinnedDrawerBlurState {
    val currentEnabled = rememberUpdatedState(enabled)
    val contentLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    return remember(contentLayer, blurLayer, blurRadius) {
        PinnedDrawerBlurState(
            contentLayer = contentLayer,
            blurLayer = blurLayer,
            enabledProvider = { currentEnabled.value() },
            blurRadius = blurRadius,
        )
    }
}

fun Modifier.pinnedDrawerBlurSource(state: PinnedDrawerBlurState): Modifier =
    this.nestedScroll(state.nestedScrollConnection).then(PinnedDrawerBlurSourceElement(state))

fun Modifier.pinnedDrawerBlurBackdrop(
    state: PinnedDrawerBlurState,
    shape: Shape,
    containerColor: Color,
    fallbackBackgroundColor: Color,
    tintAlpha: Float = DefaultPinnedDrawerTintAlpha,
): Modifier =
    this.then(
        PinnedDrawerBlurBackdropElement(
            state = state,
            shape = shape,
            containerColor = containerColor,
            fallbackBackgroundColor = fallbackBackgroundColor,
            tintAlpha = tintAlpha,
        ),
    )

private data class PinnedDrawerBlurSourceElement(private val state: PinnedDrawerBlurState) :
    ModifierNodeElement<PinnedDrawerBlurSourceNode>() {
    override fun create(): PinnedDrawerBlurSourceNode = PinnedDrawerBlurSourceNode(state)

    override fun update(node: PinnedDrawerBlurSourceNode) {
        if (node.state !== state) {
            node.state.invalidateSource = null
            node.state.hasRecordedContent = false
            node.state.hasBackdropDrawnOnce = false
            node.state.invalidateBackdrop?.invoke()
            node.state = state
            if (node.isAttached) {
                state.invalidateSource = { node.invalidateDraw() }
            }
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pinnedDrawerBlurSource"
        properties["state"] = state
    }
}

private class PinnedDrawerBlurSourceNode(var state: PinnedDrawerBlurState) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    override fun onAttach() {
        state.invalidateSource = { invalidateDraw() }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val newPosition = coordinates.positionInRoot()
        if (state.sourcePositionInRoot != newPosition) {
            state.sourcePositionInRoot = newPosition
            if (state.isEnabled) {
                state.invalidateBackdrop?.invoke()
            }
        }
    }

    override fun onDetach() {
        state.invalidateSource = null
        state.hasRecordedContent = false
        state.hasBackdropDrawnOnce = false
        state.invalidateBackdrop?.invoke()
    }

    override fun ContentDrawScope.draw() {
        if (!state.isEnabled) {
            state.hasRecordedContent = false
            state.hasBackdropDrawnOnce = false
            drawContent()
            return
        }

        state.contentLayer.record { this@draw.drawContent() }
        state.hasRecordedContent = true
        drawLayer(state.contentLayer)
        if (state.hasBackdropDrawnOnce) {
            state.invalidateBackdrop?.invoke()
        }
    }
}

private data class PinnedDrawerBlurBackdropElement(
    private val state: PinnedDrawerBlurState,
    private val shape: Shape,
    private val containerColor: Color,
    private val fallbackBackgroundColor: Color,
    private val tintAlpha: Float,
) : ModifierNodeElement<PinnedDrawerBlurBackdropNode>() {
    override fun create(): PinnedDrawerBlurBackdropNode =
        PinnedDrawerBlurBackdropNode(
            state = state,
            shape = shape,
            containerColor = containerColor,
            fallbackBackgroundColor = fallbackBackgroundColor,
            tintAlpha = tintAlpha,
        )

    override fun update(node: PinnedDrawerBlurBackdropNode) {
        val stateChanged = node.state !== state
        val appearanceChanged =
            node.shape != shape ||
                node.containerColor != containerColor ||
                node.fallbackBackgroundColor != fallbackBackgroundColor ||
                node.tintAlpha != tintAlpha
        if (stateChanged) {
            node.state.invalidateBackdrop = null
            node.state.hasBackdropDrawnOnce = false
            node.state.clearCachedEffect()
            node.state = state
            if (node.isAttached) {
                state.invalidateBackdrop = { node.invalidateDraw() }
            }
        }
        if (appearanceChanged) {
            node.shape = shape
            node.containerColor = containerColor
            node.fallbackBackgroundColor = fallbackBackgroundColor
            node.tintAlpha = tintAlpha
            node.invalidateClipPathCache()
        }
        if (stateChanged || appearanceChanged) {
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pinnedDrawerBlurBackdrop"
        properties["state"] = state
        properties["shape"] = shape
        properties["containerColor"] = containerColor
        properties["fallbackBackgroundColor"] = fallbackBackgroundColor
        properties["tintAlpha"] = tintAlpha
    }
}

private class PinnedDrawerBlurBackdropNode(
    var state: PinnedDrawerBlurState,
    var shape: Shape,
    var containerColor: Color,
    var fallbackBackgroundColor: Color,
    var tintAlpha: Float,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var cachedSize = androidx.compose.ui.geometry.Size.Unspecified
    private var cachedLayoutDirection: LayoutDirection? = null
    private var cachedShape: Shape? = null
    private var hasCachedClipPath = false
    private val cachedPath = Path()

    internal fun invalidateClipPathCache() {
        cachedSize = androidx.compose.ui.geometry.Size.Unspecified
        cachedLayoutDirection = null
        cachedShape = null
        hasCachedClipPath = false
    }

    private fun getOrCreateClipPath(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Path {
        if (
            hasCachedClipPath &&
                cachedSize == size &&
                cachedLayoutDirection == layoutDirection &&
                cachedShape == shape
        ) {
            return cachedPath
        }
        val outline = shape.createOutline(size, layoutDirection, density)
        cachedPath.reset()
        cachedPath.addOutline(outline)
        cachedSize = size
        cachedLayoutDirection = layoutDirection
        cachedShape = shape
        hasCachedClipPath = true
        return cachedPath
    }

    override fun onAttach() {
        state.invalidateBackdrop = { invalidateDraw() }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val newPosition = coordinates.positionInRoot()
        if (state.backdropPositionInRoot != newPosition) {
            state.backdropPositionInRoot = newPosition
            if (state.isEnabled) {
                invalidateDraw()
            }
        }
    }

    override fun onDetach() {
        state.invalidateBackdrop = null
        state.hasBackdropDrawnOnce = false
        state.clearCachedEffect()
        invalidateClipPathCache()
    }

    override fun ContentDrawScope.draw() {
        val clipPath = getOrCreateClipPath(size, layoutDirection, this)
        val cardWidthPx = size.width.roundToInt()
        val cardHeightPx = size.height.roundToInt()
        val blurRadiusPx = state.blurRadius.toPx()
        val effect = state.getOrCreateRenderEffect(blurRadiusPx)

        if (
            !state.isEnabled ||
                !state.hasRecordedContent ||
                cardWidthPx <= 0 ||
                cardHeightPx <= 0 ||
                effect == null
        ) {
            state.clearCachedEffect()
            state.hasBackdropDrawnOnce = state.isEnabled
            clipPath(clipPath) {
                drawRect(color = containerColor)
                this@draw.drawContent()
            }
            return
        }

        val blurPaddingPx = ceil(blurRadiusPx).toInt().coerceAtLeast(0)
        val layerSize = computeBlurredLayerSizePx(cardWidthPx, cardHeightPx, blurPaddingPx)
        val offset = state.sourcePositionInRoot - state.backdropPositionInRoot

        state.blurLayer.clip = true
        state.blurLayer.renderEffect = effect
        state.blurLayer.record(size = layerSize) {
            drawRect(color = fallbackBackgroundColor)
            translate(
                left = offset.x + blurPaddingPx.toFloat(),
                top = offset.y + blurPaddingPx.toFloat(),
            ) {
                drawLayer(state.contentLayer)
            }
        }

        val clampedTintAlpha = computeClampedTintAlpha(tintAlpha)
        clipPath(clipPath) {
            translate(left = -blurPaddingPx.toFloat(), top = -blurPaddingPx.toFloat()) {
                drawLayer(state.blurLayer)
            }
            drawRect(color = containerColor.copy(alpha = clampedTintAlpha))
            this@draw.drawContent()
        }

        state.hasBackdropDrawnOnce = true
    }
}
