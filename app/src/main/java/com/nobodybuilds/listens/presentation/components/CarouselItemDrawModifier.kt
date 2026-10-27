package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.*
import kotlin.math.*
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement

/* ================================================================================================
   DRAW INFO & MODIFIER DE ITEM (clip y translate con strategy)
   ================================================================================================ */

@ExperimentalMaterial3Api
sealed interface CarouselItemDrawInfo {
    val size: Float
    val minSize: Float
    val maxSize: Float
    val maskRect: Rect
}

@OptIn(ExperimentalMaterial3Api::class)
internal class CarouselItemDrawInfoImpl : CarouselItemDrawInfo {
    var sizeState by mutableFloatStateOf(0f)
    var minSizeState by mutableFloatStateOf(0f)
    var maxSizeState by mutableFloatStateOf(0f)
    var maskRectState by mutableStateOf(Rect.Zero)
    override val size: Float get() = sizeState
    override val minSize: Float get() = minSizeState
    override val maxSize: Float get() = maxSizeState
    override val maskRect: Rect get() = maskRectState
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.carouselItem(
    index: Int,
    state: CarouselState,
    strategy: () -> Strategy,
    carouselItemDrawInfo: CarouselItemDrawInfoImpl,
    clipShape: Shape,
): Modifier = this then CarouselItemModifierNodeElement(
    index = index,
    state = state,
    strategy = strategy,
    carouselItemDrawInfo = carouselItemDrawInfo,
    clipShape = clipShape
)

@OptIn(ExperimentalMaterial3Api::class)
private data class CarouselItemModifierNodeElement(
    val index: Int,
    val state: CarouselState,
    val strategy: () -> Strategy,
    val carouselItemDrawInfo: CarouselItemDrawInfoImpl,
    val clipShape: Shape,
) : ModifierNodeElement<CarouselItemModifierNode>() {
    override fun create(): CarouselItemModifierNode {
        return CarouselItemModifierNode(
            index = index,
            state = state,
            strategy = strategy,
            carouselItemDrawInfo = carouselItemDrawInfo,
            clipShape = clipShape
        )
    }

    override fun update(node: CarouselItemModifierNode) {
        node.index = index
        node.state = state
        node.strategy = strategy
        node.carouselItemDrawInfo = carouselItemDrawInfo
        node.clipShape = clipShape
        node.invalidateMeasurement()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class CarouselItemModifierNode(
    var index: Int,
    var state: CarouselState,
    var strategy: () -> Strategy,
    var carouselItemDrawInfo: CarouselItemDrawInfoImpl,
    var clipShape: Shape,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val strategyResult = strategy()
        if (!strategyResult.isValid) return layout(0, 0) {}

        val isVertical = state.pagerState.layoutInfo.orientation == Orientation.Vertical
        val isRtl = layoutDirection == LayoutDirection.Rtl

        val mainAxisSize = strategyResult.itemMainAxisSize
        val itemConstraints =
            if (isVertical) {
                constraints.copy(
                    minHeight = mainAxisSize.roundToInt(),
                    maxHeight = mainAxisSize.roundToInt()
                )
            } else {
                constraints.copy(
                    minWidth = mainAxisSize.roundToInt(),
                    maxWidth = mainAxisSize.roundToInt()
                )
            }

        val placeable = measurable.measure(itemConstraints)
        val itemZIndex =
            if (index == state.pagerState.currentPage) 1f
            else if (index == 0) 0f
            else 1f / index.toFloat()

        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, zIndex = itemZIndex) {
                // --- keylines e interpolación
                val scrollOffset = calculateCurrentScrollOffset(state, strategyResult)
                val maxScrollOffset = calculateMaxScrollOffset(state, strategyResult)
                val keylines =
                    strategyResult.getKeylineListForScrollOffset(scrollOffset, maxScrollOffset)
                val roundedKeylines =
                    strategyResult.getKeylineListForScrollOffset(
                        scrollOffset, maxScrollOffset, roundToNearestStep = true
                    )

                val itemSizeWithSpacing =
                    strategyResult.itemMainAxisSize + strategyResult.itemSpacing
                val unadjustedCenter =
                    (index * itemSizeWithSpacing) +
                            (strategyResult.itemMainAxisSize / 2f) - scrollOffset

                val before = keylines.getKeylineBefore(unadjustedCenter)
                val after = keylines.getKeylineAfter(unadjustedCenter)
                val progress = getProgress(before, after, unadjustedCenter)
                val ik = lerp(before, after, progress) // interpolated keyline
                val isOutOfKeylineBounds = before == after

                // --- centro local (coords del layer) y dimensiones de máscara
                val centerLocalX =
                    if (isVertical) size.width / 2f else strategyResult.itemMainAxisSize / 2f
                val centerLocalY =
                    if (isVertical) strategyResult.itemMainAxisSize / 2f else size.height / 2f
                val halfMaskW = if (isVertical) size.width / 2f else ik.size / 2f
                val halfMaskH = if (isVertical) ik.size / 2f else size.height / 2f

                // --- rect base (local)
                var left = centerLocalX - halfMaskW
                var right = centerLocalX + halfMaskW
                var top = centerLocalY - halfMaskH
                var bottom = centerLocalY + halfMaskH

                // --- recorte por desbordes contra el viewport del carrusel
                //     (evita que el contenedor "aplaste" el arco del peek)
                val containerSize = strategyResult.availableSpace
                if (!isVertical) {
                    val centerInContainer = ik.offset
                    val overflowLeft = kotlin.math.max(0f, 0f - (centerInContainer - halfMaskW))
                    val overflowRight =
                        kotlin.math.max(0f, (centerInContainer + halfMaskW) - containerSize)
                    left += overflowLeft
                    right -= overflowRight
                } else {
                    val centerInContainer = ik.offset
                    val overflowTop = kotlin.math.max(0f, 0f - (centerInContainer - halfMaskH))
                    val overflowBottom =
                        kotlin.math.max(0f, (centerInContainer + halfMaskH) - containerSize)
                    top += overflowTop
                    bottom -= overflowBottom
                }

                // --- limitar además al propio layer (seguro)
                val layerBounds = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                val maskRect = Rect(left, top, right, bottom).intersect(layerBounds)

                // --- actualizar info para la máscara (para MaskScope, etc.)
                carouselItemDrawInfo.sizeState = ik.size
                carouselItemDrawInfo.minSizeState = roundedKeylines.minBy { it.size }.size
                carouselItemDrawInfo.maxSizeState = roundedKeylines.firstFocal.size
                carouselItemDrawInfo.maskRectState = maskRect

                // --- CLIP: siempre activado con la forma redondeada
                clip = true
                shape = clipShape

                // --- traslación final (pegado de bordes)
                var translation = ik.offset - unadjustedCenter
                if (isOutOfKeylineBounds) {
                    val outOfBoundsOffset =
                        (unadjustedCenter - ik.unadjustedOffset) / ik.size
                    translation += outOfBoundsOffset
                }
                if (isVertical) translationY = translation
                else translationX = if (isRtl) -translation else translation
            }
        }
    }
}
