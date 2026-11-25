package com.makd.afinity.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SmallControlMaxDp = 64f
private const val WideControlMinWidthDp = 260f

private const val SmallControlFocusScale = 1.14f
private const val DefaultCardFocusScale = 1.06f
private const val WideControlFocusScale = 1.03f

private val DefaultFocusStrokeWidth = 3.dp
private val DefaultFocusHaloExtra = 2.dp
private val DefaultFocusShape = RoundedCornerShape(12.dp)
private val DefaultFocusBorderColor = Color.White
private val DefaultFocusHaloColor = Color.Black.copy(alpha = 0.65f)
private const val FocusSheenTopAlpha = 0.24f
private const val FocusSheenBottomAlpha = 0.08f

private val FocusScaleAnimationSpec =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

private object FocusScaleTraverseKey

internal fun resolveFocusScale(
    widthDp: Float,
    heightDp: Float,
    explicitScale: Float = Float.NaN,
): Float {
    if (!explicitScale.isNaN()) return explicitScale.coerceAtLeast(1f)
    return when {
        widthDp > 0f &&
            heightDp > 0f &&
            widthDp <= SmallControlMaxDp &&
            heightDp <= SmallControlMaxDp -> SmallControlFocusScale
        widthDp >= WideControlMinWidthDp -> WideControlFocusScale
        else -> DefaultCardFocusScale
    }
}

private fun DrawScope.drawFocusHighlight(
    shape: Shape,
    strokeWidth: Dp,
    borderColor: Color,
    alpha: Float,
) {
    val clampedAlpha = alpha.coerceIn(0f, 1f)
    if (clampedAlpha <= 0f || size.width <= 0f || size.height <= 0f) return

    val fillOutline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = fillOutline,
        brush =
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.White.copy(alpha = FocusSheenTopAlpha * clampedAlpha),
                        Color.White.copy(alpha = FocusSheenBottomAlpha * clampedAlpha),
                    ),
            ),
    )

    val strokePx = strokeWidth.toPx()
    val haloPx = DefaultFocusHaloExtra.toPx()
    if (strokePx <= 0f) return

    val insetPx = strokePx / 2f
    inset(insetPx, insetPx) {
        if (size.width <= 0f || size.height <= 0f) return@inset
        val strokeOutline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = strokeOutline,
            color = DefaultFocusHaloColor.copy(alpha = DefaultFocusHaloColor.alpha * clampedAlpha),
            style = Stroke(width = strokePx + haloPx),
        )
        drawOutline(
            outline = strokeOutline,
            color = borderColor.copy(alpha = borderColor.alpha * clampedAlpha),
            style = Stroke(width = strokePx),
        )
    }
}

class FocusScaleIndication(
    private val delegate: IndicationNodeFactory,
    private val focusedScale: Float = Float.NaN,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusScaleIndicationNode(
            delegateNode = delegate.create(interactionSource),
            interactionSource = interactionSource,
            focusedScale = focusedScale,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FocusScaleIndication) return false
        return delegate == other.delegate &&
            focusedScale.toRawBits() == other.focusedScale.toRawBits()
    }

    override fun hashCode(): Int {
        var result = delegate.hashCode()
        result = 31 * result + focusedScale.toRawBits()
        return result
    }
}

private class FocusScaleIndicationNode(
    delegateNode: DelegatableNode,
    private val interactionSource: InteractionSource,
    private val focusedScale: Float,
) : DelegatingNode(), DrawModifierNode {

    private var isFocused = false
    private var focusProgress: Animatable<Float, AnimationVector1D> = Animatable(0f)
    private var animationJob: Job? = null

    init {
        delegate(delegateNode)
    }

    override fun onAttach() {
        coroutineScope.launch {
            val activeFocus = mutableSetOf<FocusInteraction.Focus>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> activeFocus.add(interaction)
                    is FocusInteraction.Unfocus -> activeFocus.remove(interaction.focus)
                }
                val focused = activeFocus.isNotEmpty()
                if (isFocused != focused) {
                    isFocused = focused
                    val hasExplicitScaleAncestor = focused && hasActiveFocusScaleAncestor()
                    val target = if (focused && !hasExplicitScaleAncestor) 1f else 0f
                    animateProgressTo(target)
                }
            }
        }
    }

    private fun hasActiveFocusScaleAncestor(): Boolean =
        (findNearestAncestor(FocusScaleTraverseKey) as? FocusScaleNode)?.isFocused == true

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
        isFocused = false
        focusProgress = Animatable(0f)
    }

    private fun animateProgressTo(target: Float) {
        if (!isAttached) return
        if (focusProgress.targetValue == target) return
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            focusProgress.animateTo(target, FocusScaleAnimationSpec)
        }
    }

    override fun ContentDrawScope.draw() {
        val progress = focusProgress.value.coerceIn(0f, 1f)
        if (progress <= 0f || hasActiveFocusScaleAncestor()) {
            drawContent()
            return
        }

        val widthDp = size.width.toDp().value
        val heightDp = size.height.toDp().value
        val targetScale = resolveFocusScale(widthDp, heightDp, focusedScale)
        val currentScale = 1f + (targetScale - 1f) * progress
        val fallbackShape =
            if (
                widthDp > 0f &&
                    heightDp > 0f &&
                    widthDp <= SmallControlMaxDp &&
                    heightDp <= SmallControlMaxDp &&
                    abs(widthDp - heightDp) <= 4f
            ) {
                CircleShape
            } else {
                DefaultFocusShape
            }

        if (currentScale != 1f) {
            scale(currentScale, currentScale) {
                this@draw.drawContent()
                drawFocusHighlight(
                    shape = fallbackShape,
                    strokeWidth = DefaultFocusStrokeWidth,
                    borderColor = DefaultFocusBorderColor,
                    alpha = progress,
                )
            }
        } else {
            drawContent()
            drawFocusHighlight(
                shape = fallbackShape,
                strokeWidth = DefaultFocusStrokeWidth,
                borderColor = DefaultFocusBorderColor,
                alpha = progress,
            )
        }
    }
}

fun Modifier.focusScale(focusedScale: Float = Float.NaN): Modifier =
    this then
        FocusScaleElement(
            shape = DefaultFocusShape,
            strokeWidth = DefaultFocusStrokeWidth,
            color = Color.Unspecified,
            focusedScale = focusedScale,
        )

fun Modifier.focusOutline(
    shape: Shape = DefaultFocusShape,
    strokeWidth: Dp = DefaultFocusStrokeWidth,
    color: Color = Color.Unspecified,
    focusedScale: Float = Float.NaN,
): Modifier =
    this then
        FocusScaleElement(
            shape = shape,
            strokeWidth = strokeWidth,
            color = color,
            focusedScale = focusedScale,
        )

private data class FocusScaleElement(
    val shape: Shape,
    val strokeWidth: Dp,
    val color: Color,
    val focusedScale: Float,
) : ModifierNodeElement<FocusScaleNode>() {

    override fun create(): FocusScaleNode =
        FocusScaleNode(
            shape = shape,
            strokeWidth = strokeWidth,
            color = color,
            focusedScale = focusedScale,
        )

    override fun update(node: FocusScaleNode) {
        val visualChanged =
            node.shape != shape ||
                node.strokeWidth != strokeWidth ||
                node.color != color ||
                node.focusedScale.toRawBits() != focusedScale.toRawBits()
        node.shape = shape
        node.strokeWidth = strokeWidth
        node.color = color
        node.focusedScale = focusedScale
        if (visualChanged) {
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusScale"
        properties["shape"] = shape
        properties["strokeWidth"] = strokeWidth
        properties["color"] = color
        properties["focusedScale"] = focusedScale
    }
}

private class FocusScaleNode(
    var shape: Shape,
    var strokeWidth: Dp,
    var color: Color,
    var focusedScale: Float,
) : Modifier.Node(), FocusEventModifierNode, LayoutModifierNode, DrawModifierNode, TraversableNode {

    override val traverseKey: Any = FocusScaleTraverseKey

    var isFocused: Boolean = false
        private set

    private var focusProgress: Animatable<Float, AnimationVector1D> = Animatable(0f)
    private var animationJob: Job? = null

    override fun onFocusEvent(focusState: FocusState) {
        val focused = focusState.isFocused
        if (isFocused != focused) {
            isFocused = focused
            invalidatePlacement()
            invalidateDraw()
            refreshFocusProgress()
        }
    }

    private fun refreshFocusProgress() {
        val target = if (isFocused) 1f else 0f
        if (!isAttached) return
        if (focusProgress.targetValue == target) return
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            focusProgress.animateTo(target, FocusScaleAnimationSpec)
        }
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
        isFocused = false
        focusProgress = Animatable(0f)
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        // Lift the focused node in z-order without scaling the layout layer so LayoutCoordinates
        // remain invariant across focus transitions and never trigger BringIntoView scroll bounce.
        return layout(placeable.width, placeable.height) {
            placeable.place(x = 0, y = 0, zIndex = if (isFocused) 1f else 0f)
        }
    }

    override fun ContentDrawScope.draw() {
        val progress = focusProgress.value.coerceIn(0f, 1f)
        if (progress <= 0f) {
            drawContent()
            return
        }

        val widthDp = size.width.toDp().value
        val heightDp = size.height.toDp().value
        val targetScale = resolveFocusScale(widthDp, heightDp, focusedScale)
        val currentScale = 1f + (targetScale - 1f) * progress
        val borderColor = if (color == Color.Unspecified) DefaultFocusBorderColor else color

        if (currentScale != 1f) {
            scale(currentScale, currentScale) {
                this@draw.drawContent()
                drawFocusHighlight(
                    shape = shape,
                    strokeWidth = strokeWidth,
                    borderColor = borderColor,
                    alpha = progress,
                )
            }
        } else {
            drawContent()
            drawFocusHighlight(
                shape = shape,
                strokeWidth = strokeWidth,
                borderColor = borderColor,
                alpha = progress,
            )
        }
    }
}
