package eu.kanade.presentation.kodi

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// KMK -->
/**
 * App-wide indication: the normal ripple plus a clear outline around whatever has focus, so every
 * clickable element in the app can be seen and picked with a D-pad / gamepad.
 */
object KodiFocusIndication : IndicationNodeFactory {
    private val ripple = ripple()

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        KodiFocusNode(interactionSource, ripple.create(interactionSource))

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

private class KodiFocusNode(
    private val interactionSource: InteractionSource,
    rippleNode: DelegatableNode,
) : DelegatingNode(),
    DrawModifierNode {

    private var focused = false

    init {
        delegate(rippleNode)
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) {
            drawRoundRect(
                color = Color(0xFFE0E0E0),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
// KMK <--
