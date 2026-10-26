package com.nobodybuilds.listens.presentation.components

import androidx.annotation.FloatRange
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import kotlin.math.*
import androidx.compose.ui.graphics.Path
import com.nobodybuilds.listens.data.preferences.CarouselStyle
import kotlinx.coroutines.flow.distinctUntilChanged

/* ================================================================================================
   PUBLIC API
   ================================================================================================ */

/** Estado del carrusel (idéntico al de M3, pero standalone) */
@ExperimentalMaterial3Api
class CarouselState(
    currentItem: Int = 0,
    @FloatRange(from = -0.5, to = 0.5) currentItemOffsetFraction: Float = 0f,
    itemCount: () -> Int,
) : ScrollableState {
    internal var pagerState: CarouselPagerState =
        CarouselPagerState(currentItem, currentItemOffsetFraction, itemCount)

    override val isScrollInProgress: Boolean get() = pagerState.isScrollInProgress
    val currentItem: Int get() = pagerState.currentPage

    override fun dispatchRawDelta(delta: Float): Float = pagerState.dispatchRawDelta(delta)

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ) { pagerState.scroll(scrollPriority, block) }

    suspend fun scrollToItem(item: Int) = pagerState.scrollToPage(item, 0f)

    @Suppress("UNUSED_PARAMETER")
    suspend fun animateScrollToItem(item: Int, animationSpec: AnimationSpec<Float> = spring()) {
        if ((item == pagerState.currentPage && pagerState.currentPageOffsetFraction == 0f) || pagerState.pageCount == 0) return
        val targetPage = if (pagerState.pageCount > 0) item.coerceIn(0, pagerState.pageCount - 1) else 0
        pagerState.animateScrollToPage(targetPage)
    }

    companion object {
        val Saver: Saver<CarouselState, *> =
            listSaver(
                save = {
                    listOf(
                        it.pagerState.currentPage,
                        it.pagerState.currentPageOffsetFraction,
                        it.pagerState.pageCount
                    )
                },
                restore = {
                    CarouselState(
                        currentItem = it[0] as Int,
                        currentItemOffsetFraction = it[1] as Float,
                        itemCount = { it[2] as Int }
                    )
                }
            )
    }
}

@ExperimentalMaterial3Api
@Composable
fun rememberCarouselState(initialItem: Int = 0, itemCount: () -> Int): CarouselState {
    return rememberSaveable(saver = CarouselState.Saver) {
        CarouselState(
            currentItem = initialItem,
            currentItemOffsetFraction = 0f,
            itemCount = itemCount
        )
    }.apply { pagerState.pageCountState.value = itemCount }
}

/** API pública del carrusel tipo Multi-Browse con clip redondeado real. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundedHorizontalMultiBrowseCarousel(
    state: CarouselState,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    flingBehavior: TargetedFlingBehavior = PagerDefaults.flingBehavior(
        state = state.pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1)
    ),
    userScrollEnabled: Boolean = true,
    itemCornerRadius: Dp = 16.dp,
    suppressNoPeekSettleCorrection: Boolean = false,
    carouselStyle: String,
    carouselWidth: Dp,
    itemKey: ((itemIndex: Int) -> Any)? = null,
    content: @Composable CarouselItemScope.(itemIndex: Int) -> Unit,
) {
    val density = LocalDensity.current
    val carouselWidthPx = with(density) { carouselWidth.toPx() }

    val maxNonFocalItems = when (carouselStyle) {
        CarouselStyle.NO_PEEK -> 0
        CarouselStyle.ONE_PEEK -> 1
        CarouselStyle.TWO_PEEK -> 2
        else -> 1 // Default to one peek
    }

    if (carouselStyle == CarouselStyle.NO_PEEK && !suppressNoPeekSettleCorrection) {
        val settleEpsilon = 0.001f
        LaunchedEffect(state.pagerState) {
            snapshotFlow {
                state.pagerState.isScrollInProgress to state.pagerState.currentPageOffsetFraction
            }
                .distinctUntilChanged()
                .collect { (inProgress, offset) ->
                    if (inProgress || abs(offset) <= settleEpsilon) return@collect

                    val pageCount = state.pagerState.pageCount
                    if (pageCount == 0) return@collect

                    // NO_PEEK must never stay visually between two album arts.
                    val targetPage = state.pagerState.currentPage.coerceIn(0, pageCount - 1)
                    state.scrollToItem(targetPage)
                }
        }
    }

    RoundedCarousel(
        state = state,
        orientation = Orientation.Horizontal,
        keylineList = { _, spacingPx ->
            val itemCount = state.pagerState.pageCountState.value.invoke()
            when (carouselStyle) {
                CarouselStyle.NO_PEEK -> multiBrowseKeylineList(
                    density = density,
                    carouselMainAxisSize = carouselWidthPx,
                    preferredItemSize = carouselWidthPx,
                    itemSpacing = spacingPx,
                    itemCount = itemCount,
                    largeCounts = intArrayOf(1),
                    mediumCounts = intArrayOf(0),
                    smallCounts = intArrayOf(0)
                )
                CarouselStyle.ONE_PEEK -> multiBrowseKeylineList(
                    density = density,
                    carouselMainAxisSize = carouselWidthPx,
                    preferredItemSize = carouselWidthPx * 0.8f,
                    itemSpacing = spacingPx,
                    itemCount = itemCount,
                    alignment = CarouselAlignment.Start,
                    largeCounts = intArrayOf(1),
                    mediumCounts = intArrayOf(0),
                    smallCounts = intArrayOf(1)
                )
                CarouselStyle.TWO_PEEK -> {
                    // Manual keyline definition for [small, large, small]
                    val largeSize = carouselWidthPx * 0.6f // Main item is 60% of width
                    val smallSize = carouselWidthPx * 0.45f // Peek items are 45% of width
                    keylineListOf(
                        carouselMainAxisSize = carouselWidthPx,
                        itemSpacing = spacingPx,
                        carouselAlignment = CarouselAlignment.Center
                    ) {
                        add(smallSize) // Previous peek
                        add(largeSize) // Focused item
                        add(smallSize) // Next peek
                    }
                }
                else -> multiBrowseKeylineList( // Default to one peek
                    density = density,
                    carouselMainAxisSize = carouselWidthPx,
                    preferredItemSize = carouselWidthPx * 0.8f,
                    itemSpacing = spacingPx,
                    itemCount = itemCount,
                    alignment = CarouselAlignment.Start
                )
            }
        },
        contentPadding = PaddingValues(0.dp),
        maxNonFocalVisibleItemCount = maxNonFocalItems,
        modifier = modifier,
        itemSpacing = itemSpacing,
        flingBehavior = flingBehavior,
        userScrollEnabled = true, // Always allow user scrolling
        itemCornerRadius = itemCornerRadius,
        carouselStyle = carouselStyle, // Pass style down
        itemKey = itemKey,
        content = content
    )
}

/* ================================================================================================
   CORE (Carousel + PageSize + ItemScope con clip redondeado)
   ================================================================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundedCarousel(
    state: CarouselState,
    orientation: Orientation,
    keylineList: (availableSpace: Float, itemSpacing: Float) -> KeylineList,
    contentPadding: PaddingValues,
    maxNonFocalVisibleItemCount: Int,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    flingBehavior: TargetedFlingBehavior,
    userScrollEnabled: Boolean,
    itemCornerRadius: Dp,
    carouselStyle: String,
    itemKey: ((itemIndex: Int) -> Any)? = null,
    content: @Composable CarouselItemScope.(itemIndex: Int) -> Unit,
) {
    val beforeContentPadding = contentPadding.calculateBeforeContentPadding(orientation)
    val afterContentPadding = contentPadding.calculateAfterContentPadding(orientation)
    val pageSize = remember(keylineList) {
        CarouselPageSize(keylineList, beforeContentPadding, afterContentPadding)
    }
    val snapPosition = KeylineSnapPosition(pageSize)

    HorizontalPager(
        state = state.pagerState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        pageSize = pageSize,
        pageSpacing = itemSpacing,
        beyondViewportPageCount = maxNonFocalVisibleItemCount,
        snapPosition = snapPosition,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        key = itemKey,
        modifier = modifier.semantics { role = Role.Carousel }
    ) { page ->
        val carouselItemInfo = remember { CarouselItemDrawInfoImpl() }
        val scope = remember { CarouselItemScopeImpl(itemInfo = carouselItemInfo) }

        val cachedShape = remember(itemCornerRadius) {
            RoundedCornerShape(itemCornerRadius)
        }

        val clipShape = remember(cachedShape) {
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density
                ): Outline {
                    // 1) Limitar la máscara al tamaño del layer (item)
                    val layerBounds = Rect(0f, 0f, size.width, size.height)
                    // intersecta con bounds y da un respiro sub-px para que no se vea “cortado”
                    val rect = carouselItemInfo.maskRect.intersect(layerBounds).inflate(0.5f)

                    // 2) Creamos un outline redondeado del tamaño del rect ya intersectado
                    val localSize = Size(rect.width, rect.height)
                    val baseOutline = cachedShape.createOutline(localSize, layoutDirection, density)

                    // 3) Lo pasamos a Path y lo trasladamos a (left,top) del maskRect
                    val path = Path().apply {
                        addOutline(baseOutline)
                        translate(Offset(rect.left, rect.top))
                    }
                    return Outline.Generic(path)
                }
            }
        }

//        val clipShape = remember {
//            object : Shape {
//                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
//                    return Outline.Rectangle(carouselItemInfo.maskRect) // <-- RECTÁNGULO: causa el corte plano
//                }
//            }
//        }

        //val clipShape = rememberRoundedClipShape(carouselItemInfo, itemCornerRadius)

        val animatedAlpha by animateFloatAsState(
            targetValue = if (carouselStyle == CarouselStyle.ONE_PEEK && page > state.pagerState.currentPage + 1) 0f else 1f,
            animationSpec = tween(durationMillis = 200),
            label = "CarouselItemAlpha"
        )

        Box(
            modifier = Modifier
                .graphicsLayer { alpha = animatedAlpha }
                .carouselItem(
                    index = page,
                    state = state,
                    strategy = { pageSize.strategy },
                    carouselItemDrawInfo = carouselItemInfo,
                    clipShape = clipShape
                )
        ) {
            scope.content(page)
        }
    }
}

internal class CarouselPageSize(
    private val keylineList: (availableSpace: Float, itemSpacing: Float) -> KeylineList,
    private val beforeContentPadding: Float,
    private val afterContentPadding: Float,
) : PageSize {
    private var strategyState by mutableStateOf(Strategy.Empty)
    val strategy: Strategy get() = strategyState

    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int {
        val keylines = keylineList(availableSpace.toFloat(), pageSpacing.toFloat())
        strategyState = Strategy(
            defaultKeylines = keylines,
            availableSpace = availableSpace.toFloat(),
            itemSpacing = pageSpacing.toFloat(),
            beforeContentPadding = beforeContentPadding,
            afterContentPadding = afterContentPadding,
        )
        return if (strategy.isValid) strategy.itemMainAxisSize.roundToInt() else availableSpace
    }
}

/* ItemScope con máscara que sigue el maskRect + radius */
@ExperimentalMaterial3Api
sealed interface CarouselItemScope {
    val carouselItemDrawInfo: CarouselItemDrawInfo
    @Composable fun Modifier.maskClip(shape: Shape): Modifier
    @Composable fun Modifier.maskBorder(border: BorderStroke, shape: Shape): Modifier
    @Composable fun rememberMaskShape(shape: Shape): androidx.compose.foundation.shape.GenericShape
}

@ExperimentalMaterial3Api
private class CarouselItemScopeImpl(private val itemInfo: CarouselItemDrawInfo) : CarouselItemScope {
    override val carouselItemDrawInfo: CarouselItemDrawInfo get() = itemInfo

    @Composable
    override fun Modifier.maskClip(shape: Shape): Modifier =
        clip(rememberMaskShape(shape))

    @Composable
    override fun Modifier.maskBorder(border: BorderStroke, shape: Shape): Modifier =
        border(border, rememberMaskShape(shape))

    @Composable
    override fun rememberMaskShape(shape: Shape): androidx.compose.foundation.shape.GenericShape {
        val density = LocalDensity.current
        return remember(carouselItemDrawInfo, density) {
            androidx.compose.foundation.shape.GenericShape { size, direction ->
                val rect = carouselItemDrawInfo.maskRect.intersect(Rect(0f, 0f, size.width, size.height))
                addOutline(shape.createOutline(rect.size, direction, density))
                translate(Offset(rect.left, rect.top))
            }
        }
    }
}

/* Clip redondeado que se actualiza por frame según maskRect (sin hairlines) */
@Composable
private fun rememberRoundedClipShape(
    itemInfo: CarouselItemDrawInfoImpl,
    itemCornerRadius: Dp
): Shape {
    val density = LocalDensity.current
    return remember(itemInfo, itemCornerRadius, density) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val eps = 0.75f
                val r = with(density) { itemCornerRadius.toPx() } + eps
                val rect = itemInfo.maskRect
                val rr = androidx.compose.ui.geometry.RoundRect(
                    rect.left - eps, rect.top - eps, rect.right + eps, rect.bottom + eps,
                    CornerRadius(r, r)
                )
                return Outline.Rounded(rr)
            }
        }
    }
}

/* ================================================================================================
   PAGER STATE (compat) + animateScrollToPage helper
   ================================================================================================ */

private const val MinPageOffset = -0.5f
private const val MaxPageOffset = 0.5f

class CarouselPagerState(
    currentPage: Int,
    currentPageOffsetFraction: Float,
    updatedPageCount: () -> Int,
) : PagerState(currentPage, currentPageOffsetFraction) {
    var pageCountState = mutableStateOf(updatedPageCount)
    override val pageCount: Int get() = pageCountState.value.invoke()

    companion object {
        val Saver: Saver<CarouselPagerState, *> =
            listSaver(
                save = {
                    listOf(
                        it.currentPage,
                        it.currentPageOffsetFraction.coerceIn(MinPageOffset, MaxPageOffset),
                        it.pageCountState.value
                    )
                },
                restore = {
                    CarouselPagerState(
                        currentPage = it[0] as Int,
                        currentPageOffsetFraction = it[1] as Float,
                        updatedPageCount = { it[2] as Int }
                    )
                }
            )
    }
}



/* ================================================================================================
   HELPERS de Padding
   ================================================================================================ */

@Composable
private fun PaddingValues.calculateBeforeContentPadding(orientation: Orientation): Float {
    val dp = if (orientation == Orientation.Vertical) calculateTopPadding()
    else calculateStartPadding(LocalLayoutDirection.current)
    return with(LocalDensity.current) { dp.toPx() }
}

@Composable
private fun PaddingValues.calculateAfterContentPadding(orientation: Orientation): Float {
    val dp = if (orientation == Orientation.Vertical) calculateBottomPadding()
    else calculateEndPadding(LocalLayoutDirection.current)
    return with(LocalDensity.current) { dp.toPx() }
}

/* ---------- helpers de desplazamiento/steps ---------- */
