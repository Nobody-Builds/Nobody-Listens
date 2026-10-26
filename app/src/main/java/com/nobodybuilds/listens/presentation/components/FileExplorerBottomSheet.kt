@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobodybuilds.listens.presentation.screens.TabAnimation
import com.nobodybuilds.listens.presentation.viewmodel.DirectoryEntry
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import com.nobodybuilds.listens.utils.StorageInfo
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileExplorerDialog(
    visible: Boolean,
    currentPath: File,
    directoryChildren: List<DirectoryEntry>,
    availableStorages: List<StorageInfo>,
    selectedStorageIndex: Int,
    isLoading: Boolean,
    isPriming: Boolean = false,
    isReady: Boolean = true,
    isCurrentDirectoryResolved: Boolean = true,
    isAtRoot: Boolean,
    rootDirectory: File,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onToggleAllowed: (File) -> Unit,
    onRefresh: () -> Unit,
    onStorageSelected: (Int) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    hint: String? = null,
    /** Non-null switches the explorer to "pick folders" mode; holds the picked folder paths. */
    pickSelectedPaths: Set<String>? = null
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = {
                if (!isAtRoot) {
                    onNavigateUp()
                } else {
                    onDismiss()
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "file_explorer_dialog"
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    FileExplorerContent(
                        currentPath = currentPath,
                        directoryChildren = directoryChildren,
                        availableStorages = availableStorages,
                        selectedStorageIndex = selectedStorageIndex,
                        isLoading = isLoading,
                        isPriming = isPriming,
                        isReady = isReady,
                        isCurrentDirectoryResolved = isCurrentDirectoryResolved,
                        isAtRoot = isAtRoot,
                        rootDirectory = rootDirectory,
                        onNavigateTo = onNavigateTo,
                        onNavigateUp = onNavigateUp,
                        onNavigateHome = onNavigateHome,
                        onToggleAllowed = onToggleAllowed,
                        onRefresh = onRefresh,
                        onStorageSelected = onStorageSelected,
                        onDone = onDone,
                        onDismiss = onDismiss,
                        title = title ?: "Excluded folders",
                        hint = hint,
                        pickSelectedPaths = pickSelectedPaths,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerContent(
    currentPath: File,
    directoryChildren: List<DirectoryEntry>,
    availableStorages: List<StorageInfo>,
    selectedStorageIndex: Int,
    isLoading: Boolean,
    isPriming: Boolean,
    isReady: Boolean,
    isCurrentDirectoryResolved: Boolean,
    isAtRoot: Boolean,
    rootDirectory: File,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onToggleAllowed: (File) -> Unit,
    onRefresh: () -> Unit,
    onStorageSelected: (Int) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Excluded folders",
    leadingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    hint: String? = null,
    pickSelectedPaths: Set<String>? = null
) {
    val listState = rememberLazyListState()
    val safeSelectedStorageIndex = remember(availableStorages, selectedStorageIndex) {
        if (availableStorages.isEmpty()) {
            0
        } else {
            selectedStorageIndex.coerceIn(0, availableStorages.lastIndex)
        }
    }
    val showLoadingState = remember(
        directoryChildren,
        isLoading,
        isPriming,
        isReady,
        isCurrentDirectoryResolved
    ) {
        directoryChildren.isEmpty() && (
            isLoading || isPriming || !isReady || !isCurrentDirectoryResolved
        )
    }
    val loadingMessage = remember(isPriming, isReady) {
        if (isPriming || !isReady) {
            "Preparing folders…"
        } else {
            "Loading folders…"
        }
    }
    val loadingHint = remember(isPriming, isReady) {
        if (isPriming || !isReady) {
            "This can take a moment while Nobody Listens scans the available subfolders."
        } else {
            null
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 22.sp,
//                            textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_close)
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        modifier = Modifier.padding(end = 6.dp),
                        onClick = onRefresh,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_refresh)
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars,
        floatingActionButton = {
            MediumExtendedFloatingActionButton(
                modifier = Modifier.padding(end = 12.dp),
                onClick = onDone,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Done,
                    contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_done),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = stringResource(R.string.presentation_batch_g_file_explorer_done))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(7.2.dp)
        ) {
            // Only show storage tabs if there's more than one storage
            if (availableStorages.size > 1) {
                PrimaryTabRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    selectedTabIndex = safeSelectedStorageIndex,
                    containerColor = Color.Transparent,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(
                                selectedTabIndex = safeSelectedStorageIndex,
                                matchContentSize = true
                            ),
                            height = 3.dp,
                            color = Color.Transparent
                        )
                    },
                    divider = {}
                ) {
                    availableStorages.forEachIndexed { index, storage ->
                        TabAnimation(
                            index = index,
                            title = storage.displayName,
                            selectedIndex = safeSelectedStorageIndex,
                            onClick = { onStorageSelected(index) }
                        ) {
                            Text(
                                text = storage.displayName,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                fontFamily = GoogleSansRounded
                            )
                        }
                    }
                }
            }

            Text(
                text = hint ?: stringResource(R.string.presentation_batch_g_file_explorer_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .padding(horizontal = 18.dp)
            )

            FileExplorerHeader(
                modifier = Modifier.padding(horizontal = 18.dp),
                currentPath = currentPath,
                rootDirectory = rootDirectory,
                isAtRoot = isAtRoot,
                onNavigateUp = onNavigateUp,
                onNavigateHome = onNavigateHome,
                onNavigateTo = onNavigateTo,
                navigationEnabled = true
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp)
            ) {
                AnimatedContent(
                    targetState = Triple(
                        currentPath,
                        directoryChildren,
                        listOf(isLoading, isPriming, isReady, isCurrentDirectoryResolved)
                    ),
                    label = "directory_content",
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(200))
                    }
                ) { (_, children, _) ->
                    when {
                        showLoadingState -> ExplorerLoadingState(
                            message = loadingMessage,
                            supportingText = loadingHint
                        )

                        children.isEmpty() -> ExplorerEmptyState(text = stringResource(R.string.presentation_batch_g_file_explorer_empty_folders))

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Padding horizontal moved to parent Box
                                    .clip(
                                        RoundedCornerShape(
                                            topEnd = 20.dp,
                                            topStart = 20.dp
                                        )
                                    ),
                                contentPadding = PaddingValues(
                                    bottom = 24.dp,
                                    end = if (listState.canScrollForward || listState.canScrollBackward) 24.dp else 0.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                state = listState
                            ) {
                                items(children, key = { it.file.absolutePath }) { directoryEntry ->
                                    val displayCount = directoryEntry.totalAudioCount

                                    FileExplorerItem(
                                        file = directoryEntry.file,
                                        audioCount = displayCount,
                                        displayName = directoryEntry.displayName,
                                        isBlocked = directoryEntry.isBlocked,
                                        onNavigate = { onNavigateTo(directoryEntry.file) },
                                        onToggleAllowed = { onToggleAllowed(directoryEntry.file) },
                                        navigationEnabled = true,
                                        pickMode = pickSelectedPaths != null,
                                        isPicked = pickSelectedPaths?.let { picked ->
                                            isPathPicked(directoryEntry.file.absolutePath, picked)
                                        } ?: false
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(76.dp)) }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                        )
                )
                
                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = 6.dp, 
                            bottom = 88.dp // Match bottom content padding roughly (24) + FAB height (56) + spacing (8)
                        )
                )
            }
        }
    }
}
