package com.twilighty.lmusic.presentation.components.subcomps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.semantics.Role
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbChip
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbIconButtonStyle
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.model.MusicFolder
import com.twilighty.lmusic.ui.theme.GoogleSansRounded
import java.io.File
import androidx.compose.ui.res.stringResource

val defaultShape = RoundedCornerShape(26.dp) // Fallback shape
val genHeight = 42.dp

@Composable
fun LibraryActionRow(
    onMainActionClick: () -> Unit,
    iconRotation: Float,
    onSortClick: () -> Unit,
    onLocateClick: () -> Unit = {},
    showSortButton: Boolean,
    showLocateButton: Boolean = false,
    showImportButton: Boolean = true,
    isPlaylistTab: Boolean,
    onImportM3uClick: () -> Unit = {},
    isFoldersTab: Boolean,
    modifier: Modifier = Modifier,
    // Breadcrumb parameters
    currentFolder: MusicFolder?,
    folderRootPath: String,
    folderRootLabel: String,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    isShuffleEnabled: Boolean = false,
    // Storage Filter
    showStorageFilterButton: Boolean = false,
    currentStorageFilter: com.twilighty.lmusic.data.model.StorageFilter = com.twilighty.lmusic.data.model.StorageFilter.ALL,
    onStorageFilterClick: () -> Unit = {},
    /** Optional: the current sort's name, shown as a Daylight chip ("Recently added ⌄"). */
    sortLabel: String? = null,
    /** Optional: a count line shown at the start ("1,284 songs"). */
    countLabel: String? = null,
) {
    val shouldShowImport = isPlaylistTab && showImportButton

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = isFoldersTab,
            label = "ActionRowContent",
            transitionSpec = {
                if (targetState) {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                } else {
                    slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                }
            },
            modifier = Modifier.weight(1f)
        ) { isFolders ->
            if (isFolders) {
                Breadcrumbs(
                    currentFolder = currentFolder,
                    rootPath = folderRootPath,
                    rootLabel = folderRootLabel,
                    onFolderClick = onFolderClick,
                    onNavigateBack = onNavigateBack
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = if (isPlaylistTab) Icons.AutoMirrored.Rounded.PlaylistAdd else Icons.Rounded.Shuffle
                    val text = if (isPlaylistTab) {
                        stringResource(R.string.library_action_new)
                    } else {
                        stringResource(R.string.shortcut_shuffle_short)
                    }
                    val contentDesc = if (isPlaylistTab) {
                        stringResource(R.string.cd_create_new_playlist)
                    } else {
                        stringResource(R.string.cd_shuffle_play)
                    }
                    // Daylight primary pill (ink), small.
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(NbShapes.pill)
                            .background(Nb.ink)
                            .clickable(role = Role.Button, onClick = onMainActionClick)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDesc,
                            tint = Nb.onInk,
                            modifier = Modifier.size(16.dp).rotate(iconRotation)
                        )
                        Text(
                            modifier = Modifier.animateContentSize(),
                            text = text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = NbType.label.copy(fontWeight = FontWeight.Bold),
                            color = Nb.onInk
                        )
                    }

                    AnimatedVisibility(
                        visible = shouldShowImport,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start, clip = false),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start, clip = false)
                    ) {
                        NbChip(
                            text = stringResource(R.string.import_file),
                            selected = false,
                            onClick = onImportM3uClick,
                            icon = painterResource(R.drawable.rounded_upload_file_24)
                        )
                    }

                    if (countLabel != null) {
                        Text(
                            text = countLabel,
                            style = NbType.caption,
                            color = Nb.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (showSortButton) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedVisibility(
                    visible = showLocateButton,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                    NbIconButton(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = stringResource(R.string.cd_locate_current_song),
                        onClick = onLocateClick,
                        style = NbIconButtonStyle.Outline,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                }

                AnimatedVisibility(
                    visible = showStorageFilterButton,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                    val finalIcon = when (currentStorageFilter) {
                        com.twilighty.lmusic.data.model.StorageFilter.ALL -> Icons.Rounded.Dataset
                        com.twilighty.lmusic.data.model.StorageFilter.ONLINE -> Icons.Rounded.Cloud
                        com.twilighty.lmusic.data.model.StorageFilter.OFFLINE -> Icons.Rounded.PhoneAndroid
                        com.twilighty.lmusic.data.model.StorageFilter.DOWNLOADED -> Icons.Rounded.DownloadDone
                    }
                    val tooltipText = when (currentStorageFilter) {
                        com.twilighty.lmusic.data.model.StorageFilter.ALL -> stringResource(R.string.library_storage_filter_all_songs)
                        com.twilighty.lmusic.data.model.StorageFilter.ONLINE -> stringResource(R.string.library_storage_filter_online)
                        com.twilighty.lmusic.data.model.StorageFilter.OFFLINE -> stringResource(R.string.library_storage_filter_offline)
                        com.twilighty.lmusic.data.model.StorageFilter.DOWNLOADED -> stringResource(R.string.library_storage_filter_downloaded)
                    }
                    val tooltipState = rememberTooltipState()

                    @OptIn(ExperimentalMaterial3Api::class)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(tooltipText) } },
                        state = tooltipState
                    ) {
                        NbIconButton(
                            imageVector = finalIcon,
                            contentDescription = tooltipText,
                            onClick = onStorageFilterClick,
                            style = if (currentStorageFilter == com.twilighty.lmusic.data.model.StorageFilter.ALL)
                                NbIconButtonStyle.Outline else NbIconButtonStyle.Filled,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                }

                if (sortLabel != null) {
                    // Daylight sort chip: "<sort> ⌄"
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .widthIn(max = 168.dp)
                            .clip(NbShapes.pill)
                            .background(Nb.canvas)
                            .border(1.dp, Nb.borderStrong, NbShapes.pill)
                            .clickable(role = Role.Button, onClick = onSortClick)
                            .padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = sortLabel,
                            style = NbType.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = Nb.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_sort_options),
                            tint = Nb.text,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    NbIconButton(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.cd_sort_options),
                        onClick = onSortClick,
                        style = NbIconButtonStyle.Outline,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                }
            }
        }
    }
}

@Composable
fun Breadcrumbs(
    currentFolder: MusicFolder?,
    rootPath: String,
    rootLabel: String,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val rowState = rememberLazyListState()
    val pathSegments = remember(currentFolder?.path, rootPath, rootLabel) {
        val path = currentFolder?.path ?: rootPath
        val normalizedRoot = rootPath.removeSuffix("/")
        val normalizedPath = path.removeSuffix("/")
        val relativePath = normalizedPath
            .removePrefix(normalizedRoot)
            .removePrefix("/")

        if (!normalizedPath.startsWith(normalizedRoot) || relativePath.isEmpty() || normalizedPath == normalizedRoot) {
            listOf(rootLabel to rootPath)
        } else {
            listOf(rootLabel to rootPath) + relativePath.split("/").scan("") { acc, segment ->
                "$acc/$segment"
            }.drop(1).map {
                val file = File(rootPath, it)
                file.name to file.path
            }
        }
    }

    val showStartFade by remember { derivedStateOf { rowState.canScrollBackward } }
    val showEndFade by remember { derivedStateOf { rowState.canScrollForward } }

    LaunchedEffect(pathSegments.size) {
        if (pathSegments.isNotEmpty()) {
            rowState.animateScrollToItem(pathSegments.lastIndex + 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NbIconButton(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.auth_cd_back),
            onClick = onNavigateBack,
            style = NbIconButtonStyle.Tonal,
            size = 36.dp,
            iconSize = 18.dp,
            enabled = currentFolder != null
        )
        Spacer(Modifier.width(8.dp))

        LazyRow(
            state = rowState,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                // 1. Forzamos que el contenido se dibuje en una capa separada.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // 2. Dibujamos el contenido original (el LazyRow).
                    drawContent()

                    // 3. Dibujamos los gradientes que actúan como "máscaras de borrado".
                    val gradientWidth = 24.dp.toPx()

                    // Máscara para el borde IZQUIERDO
                    if (showStartFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                // Gradiente de transparente a opaco (negro)
                                colors = listOf(Color.Transparent, Color.Black),
                                endX = gradientWidth
                            ),
                            // DstIn mantiene el contenido del LazyRow solo donde esta capa es opaca.
                            blendMode = BlendMode.DstIn
                        )
                    }

                    // Máscara para el borde DERECHO
                    if (showEndFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                // Gradiente de opaco (negro) a transparente
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = this.size.width - gradientWidth
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            item { Spacer(modifier = Modifier.width(12.dp)) }

            items(pathSegments.size, key = { pathSegments[it].second }) { index ->
                val (name, path) = pathSegments[index]
                val isLast = index == pathSegments.lastIndex
                Text(
                    text = name,
                    style = NbType.label.copy(fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal),
                    color = if (isLast) Nb.text else Nb.textMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !isLast) { onFolderClick(path) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                if (!isLast) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.width(12.dp)) }
        }
    }
}
