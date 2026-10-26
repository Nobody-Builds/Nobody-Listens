@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.CollapsibleCommonTopBar
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.viewmodel.AccountsViewModel
import com.nobodybuilds.listens.presentation.viewmodel.ExternalServiceAccount
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun AccountsScreen(
    onBackClick: () -> Unit,
    onOpenNeteaseDashboard: () -> Unit = {},
    onOpenQqMusicDashboard: () -> Unit = {},
    onOpenNavidromeDashboard: () -> Unit = {},
    onOpenJellyfinDashboard: () -> Unit = {},
    onOpenYouTubeMusicDashboard: () -> Unit = {},
    onOpenJioSaavnDashboard: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp
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
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                    (
                        lazyListState.firstVisibleItemIndex > 0 ||
                            lazyListState.firstVisibleItemScrollOffset > 0
                        )
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx
            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(modifier = Modifier.nestedScroll(nestedScrollConnection).fillMaxSize()) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AccountsHeroSection(
                    connectedCount = uiState.connectedAccounts.size,
                    disconnectedCount = uiState.disconnectedServices.size
                )
            }

            if (uiState.connectedAccounts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.presentation_batch_b_accounts_linked_services),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                items(
                    items = uiState.connectedAccounts,
                    key = { it.service.name }
                ) { account ->
                    ConnectedAccountCard(
                        account = account,
                        onManage = {
                            openService(
                                context = context,
                                service = account.service,
                                onOpenNeteaseDashboard = onOpenNeteaseDashboard,
                                onOpenQqMusicDashboard = onOpenQqMusicDashboard,
                                onOpenNavidromeDashboard = onOpenNavidromeDashboard,
                                onOpenJellyfinDashboard = onOpenJellyfinDashboard,
                                onOpenYouTubeMusicDashboard = onOpenYouTubeMusicDashboard,
                                onOpenJioSaavnDashboard = onOpenJioSaavnDashboard,
                                preferNeteaseDashboard = true
                            )
                        },
                        onLogout = { viewModel.logout(account.service) },
                        painter = if (account.service == ExternalServiceAccount.NETEASE) {
                            painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_)
                        } else if (account.service == ExternalServiceAccount.QQ_MUSIC) {
                            painterResource(R.drawable.qq_music)
                        } else if (account.service == ExternalServiceAccount.TELEGRAM) {
                            painterResource(R.drawable.telegram)
                        } else null
                    )
                }
            } else {
                item {
                    EmptyAccountsCard(
                        disconnectedServices = uiState.disconnectedServices,
                        onConnect = { service ->
                            openService(
                                context = context,
                                service = service,
                                onOpenNeteaseDashboard = onOpenNeteaseDashboard,
                                onOpenQqMusicDashboard = onOpenQqMusicDashboard,
                                onOpenNavidromeDashboard = onOpenNavidromeDashboard,
                                onOpenJellyfinDashboard = onOpenJellyfinDashboard,
                                onOpenYouTubeMusicDashboard = onOpenYouTubeMusicDashboard,
                                onOpenJioSaavnDashboard = onOpenJioSaavnDashboard,
                                preferNeteaseDashboard = false
                            )
                        }
                    )
                }
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_accounts_row_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBackClick,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp
        )
    }
}

@Composable
private fun AccountsHeroSection(
    connectedCount: Int,
    disconnectedCount: Int
) {
    val connectedHeroTitle = stringResource(R.string.presentation_batch_b_accounts_connected_hero_title)
    val connectedHeroBody = stringResource(R.string.presentation_batch_b_accounts_connected_hero_body)
    val statActive = stringResource(R.string.presentation_batch_b_accounts_stat_active)
    val statAvailable = stringResource(R.string.presentation_batch_b_accounts_stat_available)
    val sectionShape = AbsoluteSmoothCornerShape(30.dp, 60)
    Card(
        shape = sectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = connectedHeroTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = connectedHeroBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroStatTile(
                    title = statActive,
                    value = connectedCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                HeroStatTile(
                    title = statAvailable,
                    value = (connectedCount + disconnectedCount).toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AbsoluteSmoothCornerShape(18.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
