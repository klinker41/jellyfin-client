package com.makd.afinity.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Suppress("ComposeCompositionLocalUsage") val LocalFocusRingColor = compositionLocalOf { Purple80 }

private val DefaultFocusStrokeWidth = 3.dp
private val DefaultFocusHaloExtra = 1.5.dp
private val DefaultFocusHaloColor = Color.Black.copy(alpha = 0.45f)

class FocusRingIndication(
    private val delegate: IndicationNodeFactory,
    private val ringColor: Color,
    private val strokeWidth: Dp = DefaultFocusStrokeWidth,
    private val cornerRadius: Dp = 12.dp,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusRingIndicationNode(
            delegateNode = delegate.create(interactionSource),
            interactionSource = interactionSource,
            ringColor = ringColor,
            strokeWidth = strokeWidth,
            cornerRadius = cornerRadius,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FocusRingIndication) return false
        return delegate == other.delegate &&
            ringColor == other.ringColor &&
            strokeWidth == other.strokeWidth &&
            cornerRadius == other.cornerRadius
    }

    override fun hashCode(): Int {
        var result = delegate.hashCode()
        result = 31 * result + ringColor.hashCode()
        result = 31 * result + strokeWidth.hashCode()
        result = 31 * result + cornerRadius.hashCode()
        return result
    }
}

private class FocusRingIndicationNode(
    delegateNode: DelegatableNode,
    private val interactionSource: InteractionSource,
    private val ringColor: Color,
    private val strokeWidth: Dp,
    private val cornerRadius: Dp,
) : DelegatingNode(), DrawModifierNode {

    private var isFocused = false

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
                    invalidateDraw()
                }
            }
        }
    }

    override fun onDetach() {
        isFocused = false
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!isFocused) return

        val strokePx = strokeWidth.toPx()
        val haloPx = DefaultFocusHaloExtra.toPx()
        val insetPx = (strokePx + haloPx) / 2f
        if (size.width <= insetPx * 2f || size.height <= insetPx * 2f) return

        val radiusPx = cornerRadius.toPx().coerceAtLeast(0f)
        val rectTopLeft = Offset(insetPx, insetPx)
        val rectSize = Size(size.width - insetPx * 2f, size.height - insetPx * 2f)
        val radius = CornerRadius(radiusPx, radiusPx)

        drawRoundRect(
            color = DefaultFocusHaloColor,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = radius,
            style = Stroke(width = strokePx + haloPx),
        )
        drawRoundRect(
            color = ringColor,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = radius,
            style = Stroke(width = strokePx),
        )
    }
}

fun Modifier.focusOutline(
    shape: Shape = RoundedCornerShape(12.dp),
    strokeWidth: Dp = DefaultFocusStrokeWidth,
    color: Color = Color.Unspecified,
): Modifier = this then FocusOutlineElement(shape = shape, strokeWidth = strokeWidth, color = color)

private data class FocusOutlineElement(
    val shape: Shape,
    val strokeWidth: Dp,
    val color: Color,
) : ModifierNodeElement<FocusOutlineNode>() {

    override fun create(): FocusOutlineNode =
        FocusOutlineNode(shape = shape, strokeWidth = strokeWidth, color = color)

    override fun update(node: FocusOutlineNode) {
        val changed = node.shape != shape || node.strokeWidth != strokeWidth || node.color != color
        node.shape = shape
        node.strokeWidth = strokeWidth
        node.color = color
        if (changed) {
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusOutline"
        properties["shape"] = shape
        properties["strokeWidth"] = strokeWidth
        properties["color"] = color
    }
}

private class FocusOutlineNode(
    var shape: Shape,
    var strokeWidth: Dp,
    var color: Color,
) :
    Modifier.Node(),
    FocusEventModifierNode,
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {

    private var isFocused = false

    override fun onFocusEvent(focusState: FocusState) {
        val focused = focusState.isFocused
        if (isFocused != focused) {
            isFocused = focused
            invalidateDraw()
        }
    }

    override fun onDetach() {
        isFocused = false
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!isFocused) return

        val resolvedColor =
            if (color == Color.Unspecified) currentValueOf(LocalFocusRingColor) else color
        val strokePx = strokeWidth.toPx()
        val haloPx = DefaultFocusHaloExtra.toPx()
        val insetPx = (strokePx + haloPx) / 2f

        inset(insetPx, insetPx) {
            if (size.width <= 0f || size.height <= 0f) return@inset
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                color = DefaultFocusHaloColor,
                style = Stroke(width = strokePx + haloPx),
            )
            drawOutline(
                outline = outline,
                color = resolvedColor,
                style = Stroke(width = strokePx),
            )
        }
    }
}
