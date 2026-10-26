@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbChip
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbIconButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbType
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.statusBarsPadding
import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.navigation.navigateToTopLevelSafely
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.data.model.LibraryTabId
import com.nobodybuilds.listens.data.model.toLibraryTabIdOrNull
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch
import com.nobodybuilds.listens.data.service.wear.PhoneWatchTransferState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryTopBar(
    screenState: LibraryScreenState,
    navController: NavController,
    headerContainerColor: Color,
    isCompactNavigation: Boolean,
    currentTab: LibraryTabId,
    currentTabTitle: String,
    currentTabIndex: Int,
    tabTitles: List<String>,
    pagerState: PagerState,
    tabRowState: LazyListState,
    scope: CoroutineScope,
    isSendingToWatch: Boolean,
    activeWatchTransfer: PhoneWatchTransferState?
) {
    var showWatchTransferDialog by screenState.showWatchTransferDialog
    var showPlaylistCreationTypeDialog by screenState.showPlaylistCreationTypeDialog
    var showReorderTabsSheet by screenState.showReorderTabsSheet
    var showTabSwitcherSheet by screenState.showTabSwitcherSheet
    Column(
        modifier = Modifier
            .background(headerContainerColor)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f)) {
                if (isCompactNavigation) {
                    LibraryNavigationPill(
                        modifier = Modifier,
                        title = currentTabTitle,
                        isExpanded = showTabSwitcherSheet,
                        showIcon = !isSendingToWatch,
                        iconRes = currentTab.iconRes(),
                        pageIndex = pagerState.currentPage,
                        compressForWatchTransfer = isSendingToWatch,
                        onClick = {
                            showTabSwitcherSheet = true
                        },
                        onArrowClick = { showTabSwitcherSheet = true }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.presentation_batch_d_library_title),
                        style = NbType.h1,
                        color = Nb.text,
                        maxLines = 1
                    )
                }
            }
            if (isSendingToWatch) {
                val watchTransferProgress = activeWatchTransfer?.progress ?: 0f
                val watchTransferPercent = (watchTransferProgress * 100f).toInt().coerceIn(0, 100)
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(Nb.muted)
                        .clickable(enabled = activeWatchTransfer != null) {
                            showWatchTransferDialog = true
                        }
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_watch_arrow_down_24),
                        contentDescription = stringResource(R.string.presentation_batch_d_watch_transfer_cd),
                        tint = Nb.text,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_sync_percent, watchTransferPercent),
                        style = NbType.label,
                        color = Nb.text
                    )
                }
            }
            NbIconButton(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                onClick = { navController.navigateToTopLevelSafely(Screen.Search.route) },
                style = NbIconButtonStyle.Tonal
            )
            NbIconButton(
                imageVector = Icons.Rounded.Add,
                contentDescription = "New playlist",
                onClick = { showPlaylistCreationTypeDialog = true },
                style = NbIconButtonStyle.Tonal
            )
            NbIconButton(
                painter = painterResource(R.drawable.rounded_settings_24),
                contentDescription = stringResource(R.string.presentation_batch_d_open_settings_cd),
                onClick = { navController.navigateSafely(Screen.Settings.route) },
                style = NbIconButtonStyle.Tonal
            )
        }
        if (!isCompactNavigation) {
            // Daylight pill tabs (chips): ink when selected, outlined otherwise.
            androidx.compose.foundation.lazy.LazyRow(
                state = tabRowState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp)
            ) {
                items(tabTitles.size, key = { "lib_tab_${tabTitles[it]}" }) { index ->
                    val tabId = tabTitles[index].toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
                    NbChip(
                        text = tabId.title,
                        selected = currentTabIndex == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    targetPageForTabIndex(
                                        currentPage = pagerState.currentPage,
                                        targetTabIndex = index,
                                        tabCount = tabTitles.size,
                                        compactMode = isCompactNavigation
                                    )
                                )
                            }
                        }
                    )
                }
                item(key = "lib_tab_edit") {
                    NbIconButton(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.presentation_batch_d_reorder_tabs_cd),
                        onClick = { showReorderTabsSheet = true },
                        style = NbIconButtonStyle.Plain,
                        size = 36.dp,
                        iconSize = 18.dp,
                        tint = Nb.textMuted
                    )
                }
            }
        } else {
            CompactLibraryPagerIndicator(
                currentIndex = currentTabIndex,
                pageCount = tabTitles.size,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun CompactLibraryPagerIndicator(
    currentIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    val safeIndex = positiveMod(currentIndex, pageCount)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == safeIndex
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 10.dp,
                label = "LibraryCompactPagerIndicatorWidth"
            )
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.35f,
                label = "LibraryCompactPagerIndicatorAlpha"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(4.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}
