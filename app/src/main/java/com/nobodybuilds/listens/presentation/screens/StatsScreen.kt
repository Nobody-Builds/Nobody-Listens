@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import com.nobodybuilds.listens.presentation.components.CollapsibleCommonTopBar
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.viewmodel.StatsViewModel
import com.nobodybuilds.listens.utils.formatListeningDurationCompact
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.stats.displayNameRes
import com.nobodybuilds.listens.utils.shapes.RoundedStarShape
import com.nobodybuilds.listens.ui.theme.ExpTitleTypography

private const val PULL_TO_REFRESH_MIN_DURATION_MS = 3500L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    statsViewModel: StatsViewModel = hiltViewModel(),
    bottomInset: Dp = 0.dp,
    showBackButton: Boolean = false,
) {
    val uiState by statsViewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    // Smart features (experimental): insights card shown only when the toggle is on.
    val smartInsightsViewModel: com.nobodybuilds.listens.presentation.smart.SmartInsightsViewModel = hiltViewModel()
    val smartEnabled by smartInsightsViewModel.isEnabled.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 62.dp + statusBarHeight
    val maxTopBarHeight = 176.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }

    // Derived, not a LaunchedEffect keyed on the height: reading the height here recomposed the
    // whole screen on every frame of the top bar collapsing.
    val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
        derivedStateOf {
            1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val scrollingDown = delta < 0

                if (!scrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsume = !(scrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsume) Offset(0f, consumed) else Offset.Zero
            }
        }
    }


    @Suppress("UNUSED_VARIABLE")
    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
    val nbHeaderHeight = statusBarHeight + 76.dp + 52.dp
    val tabsHeight = 62.dp
    val showRangeTabIndicator = false
    val tabIndicatorExtraSpacing = if (showRangeTabIndicator) 8.dp else 0.dp
    val tabContentSpacing = 20.dp
    var showCustomRangePicker by rememberSaveable { mutableStateOf(false) }
    val customRangeLabel = remember(uiState.customStartMillis, uiState.customEndMillis) {
        com.nobodybuilds.listens.presentation.stats.formatCustomRangeLabel(uiState.customStartMillis, uiState.customEndMillis)
    }
    if (showCustomRangePicker) {
        com.nobodybuilds.listens.presentation.stats.StatsCustomRangeDialog(
            onDismiss = { showCustomRangePicker = false },
            onConfirm = { start, end ->
                showCustomRangePicker = false
                statsViewModel.onCustomRangeSelected(start, end)
            }
        )
    }
    var selectedTimelineMetric by rememberSaveable { mutableStateOf(TimelineMetric.ListeningTime) }
    var selectedCategoryDimension by rememberSaveable { mutableStateOf(CategoryDimension.Song) }
    val pullToRefreshState = rememberPullToRefreshState()
    var isPullRefreshAnimating by remember { mutableStateOf(false) }
    var isPullRefreshMinDelayActive by remember { mutableStateOf(false) }
    var hasPendingPullRefresh by remember { mutableStateOf(false) }
    val isRefreshingFromViewModel by rememberUpdatedState(uiState.isRefreshing)

    val pullRefreshHaptics = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current
    val onPullRefresh: () -> Unit = {
        if (hasPendingPullRefresh || uiState.isLoading) {
            Unit
        } else {
            pullRefreshHaptics.refreshTrigger()
            hasPendingPullRefresh = true
            isPullRefreshAnimating = true
            isPullRefreshMinDelayActive = true
            statsViewModel.requestStatsRefresh()
            coroutineScope.launch {
                delay(PULL_TO_REFRESH_MIN_DURATION_MS)
                isPullRefreshMinDelayActive = false
                if (!isRefreshingFromViewModel) {
                    isPullRefreshAnimating = false
                    hasPendingPullRefresh = false
                }
            }
        }
    }

    LaunchedEffect(uiState.isRefreshing, hasPendingPullRefresh, isPullRefreshMinDelayActive) {
        if (!hasPendingPullRefresh) return@LaunchedEffect
        if (uiState.isRefreshing) {
            isPullRefreshAnimating = true
        } else if (!isPullRefreshMinDelayActive) {
            isPullRefreshAnimating = false
            hasPendingPullRefresh = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshAnimating,
        onRefresh = onPullRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isPullRefreshAnimating,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = nbHeaderHeight + 4.dp)
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (uiState.isLoading && summary == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            } else {
                val showDailyRhythm = summary?.range == StatsTimeRange.DAY || summary?.range == StatsTimeRange.WEEK

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = nbHeaderHeight + 12.dp,
                        bottom = (if (showBackButton) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else bottomInset) + MiniPlayerHeight + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item(key = "hero_section") {
                        StatsHeroSection(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item(key = "lmusic_recap") {
                        com.nobodybuilds.listens.presentation.recap.RecapEntryCard(
                            onOpen = { period ->
                                navController.navigate(
                                    com.nobodybuilds.listens.presentation.navigation.Screen.Recap.createRoute(period.routeValue)
                                )
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item(key = "lmusic_insights") {
                        com.nobodybuilds.listens.presentation.stats.NobodyListensInsightsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    if (smartEnabled) {
                        item(key = "smart_insights") {
                            com.nobodybuilds.listens.presentation.smart.SmartInsightsCard(
                                range = summary?.range ?: uiState.selectedRange,
                                refreshKey = summary,
                                viewModel = smartInsightsViewModel,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                    item {
                        ListeningTimelineSection(
                            summary = summary,
                            selectedMetric = selectedTimelineMetric,
                            onMetricSelected = { selectedTimelineMetric = it },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        CategoryMetricsSection(
                            summary = summary,
                            selectedDimension = selectedCategoryDimension,
                            onDimensionSelected = { selectedCategoryDimension = it },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    item {
                        ListeningHabitsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        TopArtistsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        TopAlbumsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        TrackConcentrationCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        SongStatsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(5f)
            ) {
                val isScrolled by remember {
                    derivedStateOf { lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 4 }
                }
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                ) {
                    com.nobodybuilds.listens.presentation.nb.NbTopBar(
                        title = stringResource(R.string.presentation_batch_g_stats_title),
                        kicker = "Your listening",
                        navigationIcon = if (showBackButton) {
                            {
                                com.nobodybuilds.listens.presentation.nb.NbIconButton(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = { navController.popBackStack() }
                                )
                            }
                        } else null,
                        modifier = Modifier.height(76.dp),
                        actions = {
                            com.nobodybuilds.listens.presentation.nb.NbIconButton(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.presentation_batch_g_stats_cd_refresh),
                                onClick = statsViewModel::requestStatsRefresh,
                                enabled = !uiState.isLoading && !uiState.isRefreshing && !isPullRefreshAnimating,
                                style = com.nobodybuilds.listens.presentation.nb.NbIconButtonStyle.Tonal
                            )
                        }
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(uiState.availableRanges.size) { index ->
                            val range = uiState.availableRanges[index]
                            val selected = range == uiState.selectedRange
                            val label = if (range == StatsTimeRange.CUSTOM && customRangeLabel != null && selected) {
                                customRangeLabel
                            } else {
                                stringResource(range.displayNameRes())
                            }
                            com.nobodybuilds.listens.presentation.nb.NbChip(
                                text = label,
                                selected = selected,
                                onClick = {
                                    if (range == StatsTimeRange.CUSTOM) {
                                        showCustomRangePicker = true
                                    } else {
                                        statsViewModel.onRangeSelected(range)
                                    }
                                }
                            )
                        }
                    }
                    if (isScrolled) {
                        com.nobodybuilds.listens.presentation.nb.NbDivider()
                    }
                }
            }
        }
    }
}

// StatsTopBar removed, replaced by CollapsibleCommonTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsHeroSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val hasData = (summary?.totalDurationMs ?: 0L) > 0 || (summary?.totalPlayCount ?: 0) > 0
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Time Card - Primary Container
        HeroCard(
            title = stringResource(R.string.presentation_batch_g_stats_hero_listening),
            value = if (hasData) formatListeningDurationCompact(summary?.totalDurationMs ?: 0L) else "--",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        // Plays Card - Tertiary Container
        HeroCard(
            title = stringResource(R.string.presentation_batch_g_stats_hero_plays),
            value = if (hasData) "${summary?.totalPlayCount ?: 0}" else "--",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun HeroCard(
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = contentColor.copy(alpha = 0.85f)
        )
        Text(
            text = value,
            style = ExpTitleTypography.displayMedium.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

// Empty state component with M3 Expressive styling
@Composable
internal fun StatsEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedStarShape(sides = 8, curve = 0.1, rotation = 0f))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
