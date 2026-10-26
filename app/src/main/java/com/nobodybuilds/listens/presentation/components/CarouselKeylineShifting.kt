package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexed
import kotlin.math.*

internal fun getStartShiftDistance(
    startKeylineSteps: List<KeylineList>,
    beforeContentPadding: Float,
): Float {
    if (startKeylineSteps.isEmpty()) return 0f
    val dist = startKeylineSteps.last().first().unadjustedOffset -
            startKeylineSteps.first().first().unadjustedOffset
    return max(dist, beforeContentPadding)
}

internal fun getEndShiftDistance(
    endKeylineSteps: List<KeylineList>,
    afterContentPadding: Float,
): Float {
    if (endKeylineSteps.isEmpty()) return 0f
    val dist = endKeylineSteps.first().last().unadjustedOffset -
            endKeylineSteps.last().last().unadjustedOffset
    return max(dist, afterContentPadding)
}

internal fun getStartKeylineSteps(
    defaultKeylines: KeylineList,
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    beforeContentPadding: Float,
): List<KeylineList> {
    if (defaultKeylines.isEmpty()) return emptyList()
    val steps = mutableListOf<KeylineList>()
    steps += defaultKeylines

    if (defaultKeylines.isFirstFocalItemAtStartOfContainer()) {
        if (beforeContentPadding != 0f) {
            steps += createShiftedKeylineListForContentPadding(
                from = defaultKeylines,
                carouselMainAxisSize = carouselMainAxisSize,
                itemSpacing = itemSpacing,
                contentPadding = beforeContentPadding,
                pivot = defaultKeylines.firstFocal,
                pivotIndex = defaultKeylines.firstFocalIndex
            )
        }
        return steps
    }

    val startIndex = defaultKeylines.firstNonAnchorIndex
    val endIndex = defaultKeylines.firstFocalIndex
    val numberOfSteps = endIndex - startIndex

    if (numberOfSteps <= 0 && defaultKeylines.firstFocal.cutoff > 0) {
        steps += moveKeylineAndCreateShiftedKeylineList(
            from = defaultKeylines,
            srcIndex = 0,
            dstIndex = 0,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing
        )
        return steps
    }

    var i = 0
    while (i < numberOfSteps) {
        val prev = steps.last()
        val originalItemIndex = startIndex + i
        var dstIndex = defaultKeylines.lastIndex
        if (originalItemIndex > 0) {
            val originalNeighborBeforeSize = defaultKeylines[originalItemIndex - 1].size
            dstIndex = prev.firstIndexAfterFocalRangeWithSize(originalNeighborBeforeSize) - 1
        }
        steps += moveKeylineAndCreateShiftedKeylineList(
            from = prev,
            srcIndex = defaultKeylines.firstNonAnchorIndex,
            dstIndex = dstIndex,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing
        )
        i++
    }

    if (beforeContentPadding != 0f) {
        val last = steps.last()
        steps[steps.lastIndex] = createShiftedKeylineListForContentPadding(
            from = last,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            contentPadding = beforeContentPadding,
            pivot = last.firstFocal,
            pivotIndex = last.firstFocalIndex
        )
    }
    return steps
}

internal fun getEndKeylineSteps(
    defaultKeylines: KeylineList,
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    afterContentPadding: Float,
): List<KeylineList> {
    if (defaultKeylines.isEmpty()) return emptyList()
    val steps = mutableListOf<KeylineList>()
    steps += defaultKeylines

    if (defaultKeylines.isLastFocalItemAtEndOfContainer(carouselMainAxisSize)) {
        if (afterContentPadding != 0f) {
            steps += createShiftedKeylineListForContentPadding(
                from = defaultKeylines,
                carouselMainAxisSize = carouselMainAxisSize,
                itemSpacing = itemSpacing,
                contentPadding = -afterContentPadding,
                pivot = defaultKeylines.lastFocal,
                pivotIndex = defaultKeylines.lastFocalIndex
            )
        }
        return steps
    }

    val startIndex = defaultKeylines.lastFocalIndex
    val endIndex = defaultKeylines.lastNonAnchorIndex
    val numberOfSteps = endIndex - startIndex

    if (numberOfSteps <= 0 && defaultKeylines.lastFocal.cutoff > 0) {
        steps += moveKeylineAndCreateShiftedKeylineList(
            from = defaultKeylines,
            srcIndex = 0,
            dstIndex = 0,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing
        )
        return steps
    }

    var i = 0
    while (i < numberOfSteps) {
        val prev = steps.last()
        val originalItemIndex = endIndex - i
        var dstIndex = 0
        if (originalItemIndex < defaultKeylines.lastIndex) {
            val originalNeighborAfterSize = defaultKeylines[originalItemIndex + 1].size
            dstIndex = prev.lastIndexBeforeFocalRangeWithSize(originalNeighborAfterSize) + 1
        }
        val keylines = moveKeylineAndCreateShiftedKeylineList(
            from = prev,
            srcIndex = defaultKeylines.lastNonAnchorIndex,
            dstIndex = dstIndex,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing
        )
        steps += keylines
        i++
    }

    if (afterContentPadding != 0f) {
        val last = steps.last()
        steps[steps.lastIndex] = createShiftedKeylineListForContentPadding(
            from = last,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            contentPadding = -afterContentPadding,
            pivot = last.lastFocal,
            pivotIndex = last.lastFocalIndex
        )
    }
    return steps
}

/* ---------- helpers de shifting concreto ---------- */

private fun createShiftedKeylineListForContentPadding(
    from: KeylineList,
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    contentPadding: Float,
    pivot: Keyline,
    pivotIndex: Int,
): KeylineList {
    val numberOfNonAnchorKeylines = from.fastFilter { !it.isAnchor }.count()
    val sizeReduction = if (numberOfNonAnchorKeylines == 0) 0f else contentPadding / numberOfNonAnchorKeylines

    val newKeylines =
        keylineListOf(
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            pivotIndex = pivotIndex,
            pivotOffset = pivot.offset - (sizeReduction / 2f) + contentPadding
        ) {
            from.fastForEach { k -> add(k.size - abs(sizeReduction), k.isAnchor) }
        }

    // restaurar unadjustedOffset original para que Pager (que usa pageSize fijo) siga consistente
    return KeylineList(
        newKeylines.fastMapIndexed { i, k -> k.copy(unadjustedOffset = from[i].unadjustedOffset) }
    )
}

private fun moveKeylineAndCreateShiftedKeylineList(
    from: KeylineList,
    srcIndex: Int,
    dstIndex: Int,
    carouselMainAxisSize: Float,
    itemSpacing: Float,
): KeylineList {
    val pivotDir = if (srcIndex > dstIndex) 1 else -1
    val pivotDelta = (from[srcIndex].size - from[srcIndex].cutoff + itemSpacing) * pivotDir
    val newPivotIndex = from.pivotIndex + pivotDir
    val newPivotOffset = from.pivot.offset + pivotDelta

    return keylineListOf(
        carouselMainAxisSize = carouselMainAxisSize,
        itemSpacing = itemSpacing,
        pivotIndex = newPivotIndex,
        pivotOffset = newPivotOffset
    ) {
        from.toMutableList().move(srcIndex, dstIndex).fastForEach { k -> add(k.size, k.isAnchor) }
    }
}

private fun MutableList<Keyline>.move(srcIndex: Int, dstIndex: Int): MutableList<Keyline> {
    val k = removeAt(srcIndex)
    add(dstIndex, k)
    return this
}

/* ---------- interpolación en steps ---------- */

internal data class ShiftPointRange(
    val fromStepIndex: Int,
    val toStepIndex: Int,
    val steppedInterpolation: Float,
)

internal fun getStepInterpolationPoints(
    totalShiftDistance: Float,
    steps: List<KeylineList>,
    isShiftingLeft: Boolean,
): FloatArray {
    val points = FloatArray(max(1, steps.size))
    points[0] = 0f
    if (totalShiftDistance == 0f || steps.isEmpty()) return points

    for (i in 1 until steps.size) {
        val prev = steps[i - 1]
        val curr = steps[i]
        val distanceShifted =
            if (isShiftingLeft) {
                curr.first().unadjustedOffset - prev.first().unadjustedOffset
            } else {
                prev.last().unadjustedOffset - curr.last().unadjustedOffset
            }
        val stepPct = distanceShifted / totalShiftDistance
        val point = if (i == steps.lastIndex) 1f else points[i - 1] + stepPct
        points[i] = point
    }
    return points
}

internal fun getShiftPointRange(
    stepsCount: Int,
    shiftPoint: FloatArray,
    interpolation: Float,
): ShiftPointRange {
    var lower = shiftPoint[0]
    for (i in 1 until stepsCount) {
        val upper = shiftPoint[i]
        if (interpolation <= upper) {
            return ShiftPointRange(
                fromStepIndex = i - 1,
                toStepIndex = i,
                steppedInterpolation = lerp(0f, 1f, lower, upper, interpolation)
            )
        }
        lower = upper
    }
    return ShiftPointRange(fromStepIndex = 0, toStepIndex = 0, steppedInterpolation = 0f)
}

internal fun lerp(
    outputMin: Float,
    outputMax: Float,
    inputMin: Float,
    inputMax: Float,
    value: Float,
): Float {
    if (value <= inputMin) return outputMin
    if (value >= inputMax) return outputMax
    val t = (value - inputMin) / (inputMax - inputMin)
    return androidx.compose.ui.util.lerp(outputMin, outputMax, t)
}

// === Receiver scope para construir KeylineList ===
internal interface KeylineListScope {
    /**
     * Agrega un keyline (en orden de aparición visual).
     * @param size tamaño (px) del item en ese keyline
     * @param isAnchor true para anchors (usualmente extremos fuera de pantalla)
     */
    fun add(size: Float, isAnchor: Boolean = false)
}

internal class KeylineListScopeImpl : KeylineListScope {

    private data class TmpKeyline(val size: Float, val isAnchor: Boolean)

    // Estado interno que usamos tanto en pivot como en alignment:
    private var firstFocalIndex: Int = -1
    private var focalItemSize: Float = 0f
    private val tmpKeylines = mutableListOf<TmpKeyline>()

    override fun add(size: Float, isAnchor: Boolean) {
        tmpKeylines.add(TmpKeyline(size, isAnchor))
        // guardamos el primer índice del mayor tamaño (primer focal)
        if (size > focalItemSize) {
            focalItemSize = size
            firstFocalIndex = tmpKeylines.lastIndex
        }
    }

    /** Encuentra el último índice focal caminando hacia delante mientras el tamaño coincida */
    private fun findLastFocalIndex(): Int {
        var last = firstFocalIndex
        if (firstFocalIndex in 0..tmpKeylines.lastIndex) {
            while (
                last + 1 <= tmpKeylines.lastIndex &&
                tmpKeylines[last + 1].size == focalItemSize
            ) {
                last++
            }
        }
        return last
    }

    /** Geometría para calcular cutoff del lado izquierdo/derecho del carrusel */
    private fun isCutoffLeft(size: Float, center: Float): Boolean {
        val left = center - size / 2f
        val right = center + size / 2f
        return left < 0f && right > 0f
    }

    private fun isCutoffRight(size: Float, center: Float, carouselMainAxisSize: Float): Boolean {
        val left = center - size / 2f
        val right = center + size / 2f
        return left < carouselMainAxisSize && right > carouselMainAxisSize
    }

    /**
     * Construye la lista final a partir de un pivote ya decidido (índice y offset de centro).
     * Genera offsets/unadjustedOffsets/cutoff consistentes a ambos lados del pivote.
     */
    fun createWithPivot(
        carouselMainAxisSize: Float,
        itemSpacing: Float,
        pivotIndex: Int,
        pivotOffset: Float,
    ): KeylineList {
        val lastFocalIndex = findLastFocalIndex()
        val list = mutableListOf<Keyline>()

        // Pivote
        val pivot = tmpKeylines[pivotIndex]
        val pivotCutoff =
            when {
                isCutoffLeft(pivot.size, pivotOffset) ->
                    (pivotOffset - pivot.size / 2f)
                isCutoffRight(pivot.size, pivotOffset, carouselMainAxisSize) ->
                    (pivotOffset + pivot.size / 2f) - carouselMainAxisSize
                else -> 0f
            }
        list += Keyline(
            size = pivot.size,
            offset = pivotOffset,
            unadjustedOffset = pivotOffset,
            isFocal = pivotIndex in firstFocalIndex..lastFocalIndex,
            isAnchor = pivot.isAnchor,
            isPivot = true,
            cutoff = pivotCutoff
        )

        // Antes del pivote (de izq→der en la lista resultante; insertamos al principio)
        var offset = pivotOffset - (focalItemSize / 2f) - itemSpacing
        var unadj  = pivotOffset - (focalItemSize / 2f) - itemSpacing
        for (original in (pivotIndex - 1) downTo 0) {
            val tmp = tmpKeylines[original]
            val center = offset - (tmp.size / 2f)
            val unadjCenter = unadj - (focalItemSize / 2f)
            val cutoff = if (isCutoffLeft(tmp.size, center)) abs(center - tmp.size / 2f) else 0f

            list.add(
                0,
                Keyline(
                    size = tmp.size,
                    offset = center,
                    unadjustedOffset = unadjCenter,
                    isFocal = original in firstFocalIndex..lastFocalIndex,
                    isAnchor = tmp.isAnchor,
                    isPivot = false,
                    cutoff = cutoff
                )
            )
            // ¡sin '-=' ambiguo!
            offset = offset - tmp.size - itemSpacing
            unadj  = unadj  - focalItemSize - itemSpacing
        }

        // Después del pivote
        offset = pivotOffset + (focalItemSize / 2f) + itemSpacing
        unadj  = pivotOffset + (focalItemSize / 2f) + itemSpacing
        for (original in (pivotIndex + 1)..tmpKeylines.lastIndex) {
            val tmp = tmpKeylines[original]
            val center = offset + (tmp.size / 2f)
            val unadjCenter = unadj + (focalItemSize / 2f)
            val cutoff =
                if (isCutoffRight(tmp.size, center, carouselMainAxisSize))
                    (center + tmp.size / 2f) - carouselMainAxisSize
                else 0f

            list += Keyline(
                size = tmp.size,
                offset = center,
                unadjustedOffset = unadjCenter,
                isFocal = original in firstFocalIndex..lastFocalIndex,
                isAnchor = tmp.isAnchor,
                isPivot = false,
                cutoff = cutoff
            )
            offset = offset + tmp.size + itemSpacing
            unadj  = unadj  + focalItemSize + itemSpacing
        }

        return KeylineList(list)
    }

    /**
     * Alineación por `CarouselAlignment` (Start / Center / End) — calcula pivote y delega en pivot.
     */
    fun createWithAlignment(
        carouselMainAxisSize: Float,
        itemSpacing: Float,
        carouselAlignment: CarouselAlignment,
    ): KeylineList {
        val lastFocalIndex = findLastFocalIndex()
        val focalCount = (lastFocalIndex - firstFocalIndex) + 1

        // Elegimos como pivote el primer focal
        val pivotIndex = firstFocalIndex
        val pivotOffset =
            when (carouselAlignment) {
                CarouselAlignment.Center -> {
                    // si el número de focales es par, el spacing parte el centro: compénsalo
                    val spacingSplit = if (itemSpacing == 0f || focalCount % 2 == 0) 0f else itemSpacing / 2f
                    (carouselMainAxisSize / 2f) -
                            ((focalItemSize / 2f) * focalCount) -
                            spacingSplit
                }
                CarouselAlignment.End   -> carouselMainAxisSize - (focalItemSize / 2f)
                else /* Start */        -> (focalItemSize / 2f)
            }

        return createWithPivot(
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            pivotIndex = pivotIndex,
            pivotOffset = pivotOffset
        )
    }
}
