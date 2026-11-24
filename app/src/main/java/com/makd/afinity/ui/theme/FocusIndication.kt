package com.makd.afinity.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
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
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SmallControlMaxDp = 64f
private const val WideControlMinWidthDp = 260f

private const val SmallControlFocusScale = 1.14f
private const val DefaultCardFocusScale = 1.06f
private const val WideControlFocusScale = 1.03f

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
    private var lastWidthDp = 0f
    private var lastHeightDp = 0f
    private var scaleAnimatable: Animatable<Float, AnimationVector1D> = Animatable(1f)
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
                    val target =
                        if (focused && !hasExplicitScaleAncestor) {
                            resolveFocusScale(lastWidthDp, lastHeightDp, focusedScale)
                        } else {
                            1f
                        }
                    animateScaleTo(target)
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
        scaleAnimatable = Animatable(1f)
    }

    private fun animateScaleTo(target: Float) {
        if (!isAttached) return
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            scaleAnimatable.animateTo(target, FocusScaleAnimationSpec)
        }
    }

    override fun ContentDrawScope.draw() {
        val widthDp = size.width.toDp().value
        val heightDp = size.height.toDp().value
        val sizeChanged = widthDp != lastWidthDp || heightDp != lastHeightDp
        lastWidthDp = widthDp
        lastHeightDp = heightDp
        if (isFocused && sizeChanged && !hasActiveFocusScaleAncestor()) {
            val target = resolveFocusScale(widthDp, heightDp, focusedScale)
            if (scaleAnimatable.targetValue != target) {
                animateScaleTo(target)
            }
        }

        val currentScale = scaleAnimatable.value
        if (currentScale != 1f) {
            scale(currentScale, currentScale) { this@draw.drawContent() }
        } else {
            drawContent()
        }
    }
}

fun Modifier.focusScale(focusedScale: Float = Float.NaN): Modifier =
    this then FocusScaleElement(focusedScale = focusedScale)

@Suppress("UNUSED_PARAMETER")
fun Modifier.focusOutline(
    shape: Shape = RoundedCornerShape(12.dp),
    strokeWidth: Dp = 3.dp,
    color: Color = Color.Unspecified,
    focusedScale: Float = Float.NaN,
): Modifier = focusScale(focusedScale = focusedScale)

private data class FocusScaleElement(val focusedScale: Float) :
    ModifierNodeElement<FocusScaleNode>() {

    override fun create(): FocusScaleNode = FocusScaleNode(focusedScale = focusedScale)

    override fun update(node: FocusScaleNode) {
        val changed = node.focusedScale.toRawBits() != focusedScale.toRawBits()
        node.focusedScale = focusedScale
        if (changed && node.isFocused) {
            node.refreshFocusScale()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusScale"
        properties["focusedScale"] = focusedScale
    }
}

private class FocusScaleNode(var focusedScale: Float) :
    Modifier.Node(), FocusEventModifierNode, LayoutModifierNode, TraversableNode {

    override val traverseKey: Any = FocusScaleTraverseKey

    var isFocused: Boolean = false
        private set

    private var lastWidthDp = 0f
    private var lastHeightDp = 0f
    private var scaleAnimatable: Animatable<Float, AnimationVector1D> = Animatable(1f)
    private var animationJob: Job? = null

    override fun onFocusEvent(focusState: FocusState) {
        val focused = focusState.isFocused
        if (isFocused != focused) {
            isFocused = focused
            invalidatePlacement()
            refreshFocusScale()
        }
    }

    fun refreshFocusScale() {
        val target =
            if (isFocused) resolveFocusScale(lastWidthDp, lastHeightDp, focusedScale) else 1f
        if (!isAttached) return
        if (scaleAnimatable.targetValue == target) return
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            scaleAnimatable.animateTo(target, FocusScaleAnimationSpec)
        }
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
        isFocused = false
        scaleAnimatable = Animatable(1f)
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val widthDp = placeable.width.toDp().value
        val heightDp = placeable.height.toDp().value
        val sizeChanged = widthDp != lastWidthDp || heightDp != lastHeightDp
        lastWidthDp = widthDp
        lastHeightDp = heightDp
        if (isFocused && sizeChanged) {
            refreshFocusScale()
        }
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(x = 0, y = 0, zIndex = if (isFocused) 1f else 0f) {
                val s = scaleAnimatable.value
                scaleX = s
                scaleY = s
            }
        }
    }
}
