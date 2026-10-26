
package com.nobodybuilds.listens.presentation.screens

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.BackupModuleSelectionDialog
import com.nobodybuilds.listens.presentation.viewmodel.SetupEvent
import com.nobodybuilds.listens.presentation.viewmodel.SetupViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalPermissionsApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SetupScreen(
    setupViewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val uiState by setupViewModel.uiState.collectAsStateWithLifecycle()
    val currentPath by setupViewModel.currentPath.collectAsStateWithLifecycle()
    val directoryChildren by setupViewModel.currentDirectoryChildren.collectAsStateWithLifecycle()
    val availableStorages by setupViewModel.availableStorages.collectAsStateWithLifecycle()
    val selectedStorageIndex by setupViewModel.selectedStorageIndex.collectAsStateWithLifecycle()
    val isExplorerPriming by setupViewModel.isExplorerPriming.collectAsStateWithLifecycle()
    val isExplorerReady by setupViewModel.isExplorerReady.collectAsStateWithLifecycle()
    val isCurrentDirectoryResolved by setupViewModel.isCurrentDirectoryResolved.collectAsStateWithLifecycle()
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    
    var showCornerRadiusOverlay by remember { mutableStateOf(false) }
    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedBackupUri = uri
        if (uri != null) {
            setupViewModel.inspectBackupFile(uri)
        }
    }

    // Re-check permissions when the screen is resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                setupViewModel.checkPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pages = remember {
        buildSetupPages(Build.VERSION.SDK_INT)
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    com.nobodybuilds.listens.presentation.haptics.HapticPagerEffect(pagerState, userDragOnly = true)
    val scope = rememberCoroutineScope()
    val currentPage = pages[pagerState.currentPage]
    val musicFoldersViewModel: com.nobodybuilds.listens.presentation.folders.MusicFoldersViewModel = hiltViewModel()
    val includedMusicFolders by musicFoldersViewModel.includedFolders.collectAsStateWithLifecycle()
    // Continue stays disabled on the folders page until at least one folder is chosen.
    val folderGateSatisfied = currentPage != SetupPage.DirectorySelection || includedMusicFolders.isNotEmpty()
    val isNextButtonEnabled = isPermissionGateSatisfied(context, currentPage, uiState) && folderGateSatisfied
    var previousPageIndex by remember { mutableStateOf(0) }
    val navigateToPage: (Int) -> Unit = { targetPage ->
        scope.launch {
            val boundedPage = targetPage.coerceIn(0, pages.lastIndex)
            if (boundedPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(boundedPage)
            }
        }
    }

    val directorySelectionPageIndex = remember(pages) { pages.indexOf(SetupPage.DirectorySelection) }
    val batteryOptimizationPageIndex = remember(pages) { pages.indexOf(SetupPage.BatteryOptimization) }
    val finishPageIndex = remember(pages) { pages.indexOf(SetupPage.Finish) }

    LaunchedEffect(Unit) {
        setupViewModel.events.collectLatest { event ->
            when (event) {
                is SetupEvent.Message -> {
                    Toast.makeText(context, event.value, Toast.LENGTH_LONG).show()
                }
                is SetupEvent.RestoreCompleted -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    val targetPageIndex = when {
                        batteryOptimizationPageIndex >= 0 && !isIgnoringBatteryOptimizationsNow(context) ->
                            batteryOptimizationPageIndex
                        finishPageIndex >= 0 -> finishPageIndex
                        else -> null
                    }

                    if (targetPageIndex != null) {
                        pagerState.animateScrollToPage(targetPageIndex)
                    } else {
                        onSetupComplete()
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > previousPageIndex) {
            val blockedPageIndex = firstBlockedForwardPageIndex(
                pages = pages,
                fromPageIndex = previousPageIndex,
                toPageIndex = pagerState.currentPage,
                context = context,
                uiState = uiState
            )
            if (blockedPageIndex != null) {
                setupViewModel.checkPermissions(context)
                pagerState.scrollToPage(blockedPageIndex)
                previousPageIndex = blockedPageIndex
                Toast.makeText(context, context.getString(R.string.toast_grant_permission_first), Toast.LENGTH_SHORT).show()
                return@LaunchedEffect
            }
        }
        previousPageIndex = pagerState.currentPage

        if (pagerState.currentPage == directorySelectionPageIndex) {
            setupViewModel.loadMusicDirectories()
        }
    }
    BackHandler {
        if (pagerState.currentPage > 0) {
            navigateToPage(pagerState.currentPage - 1)
        }
    }
    Scaffold(
        bottomBar = {
            SetupBottomBar(
                pagerState = pagerState,
                animated = (pagerState.currentPage != 0),
                isNextButtonEnabled = isNextButtonEnabled,
                isFinishButtonEnabled = uiState.allPermissionsGranted,
                onNextClicked = {
                    val page = pages[pagerState.currentPage]
                    if (page == SetupPage.DirectorySelection && includedMusicFolders.isEmpty()) {
                        Toast.makeText(context, "Add at least one music folder", Toast.LENGTH_SHORT).show()
                    } else if (isPermissionGateSatisfied(context, page, uiState)) {
                        navigateToPage(pagerState.currentPage + 1)
                    } else {
                        setupViewModel.checkPermissions(context)
                        Toast.makeText(context, context.getString(R.string.toast_grant_permission_first), Toast.LENGTH_SHORT).show()
                    }
                },
                onFinishClicked = {
                    if (allRequiredPermissionsGrantedNow(context)) {
                        setupViewModel.setSetupComplete()
                        onSetupComplete()
                    } else {
                        setupViewModel.checkPermissions(context)
                        Toast.makeText(context, context.getString(R.string.toast_grant_all_permissions), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            // Keep setup progression deterministic; free swiping was allowing users
            // to jump across optional pages after the first permission dialog.
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { pageIndex ->
            val page = pages[pageIndex]
            val pageOffset = pagerState.currentPageOffsetFraction

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 1f - pageOffset.coerceIn(0f, 1f)
                        translationX = size.width * pageOffset
                    },
                contentAlignment = Alignment.Center
            ) {
                when (page) {
                    SetupPage.Welcome -> WelcomePage()
                    SetupPage.MediaPermission -> MediaPermissionPage(
                        uiState = uiState,
                        onPermissionStateUpdated = { setupViewModel.checkPermissions(context) }
                    )
                    SetupPage.BackupRestore -> BackupRestorePage(
                        uiState = uiState,
                        onImportClicked = { backupPickerLauncher.launch("*/*") },
                        onSkip = {
                            setupViewModel.clearRestorePlan()
                            selectedBackupUri = null
                            navigateToPage(pagerState.currentPage + 1)
                        }
                    )
                    SetupPage.DirectorySelection -> com.nobodybuilds.listens.presentation.folders.MusicFoldersSetupPage(
                        viewModel = musicFoldersViewModel,
                        hasMediaPermission = uiState.mediaPermissionGranted
                    )
                    SetupPage.NotificationsPermission -> NotificationsPermissionPage(
                        uiState = uiState,
                        onPermissionStateUpdated = { setupViewModel.checkPermissions(context) }
                    )
                    SetupPage.AlarmsPermission -> AlarmsPermissionPage(
                        uiState = uiState,
                        onSkip = {
                            navigateToPage(pagerState.currentPage + 1)
                        }
                    )
                    SetupPage.BatteryOptimization -> BatteryOptimizationPage(
                        onSkip = {
                            navigateToPage(pagerState.currentPage + 1)
                        }
                    )
                    SetupPage.ThemeSelection -> ThemeSelectionPage(
                        uiState = uiState,
                        onModeSelected = setupViewModel::setAppThemeMode
                    )
                    SetupPage.Finish -> FinishPage()
                    SetupPage.LibraryLayout -> LibraryLayoutPage(
                        uiState = uiState,
                        onModeSelected = setupViewModel::setLibraryNavigationMode,
                        onSkip = {
                            navigateToPage(pagerState.currentPage + 1)
                        }
                    )
                    SetupPage.NavBarLayout -> NavBarLayoutPage(
                        uiState = uiState,
                        onModeSelected = setupViewModel::setNavBarStyle,
                        onCustomizeRadius = { showCornerRadiusOverlay = true },
                        onSkip = {
                            navigateToPage(pagerState.currentPage + 1)
                        }
                    )
                }
            }
        }
    }

    val restorePlan = uiState.restorePlan
    if (restorePlan != null && selectedBackupUri != null) {
        BackupModuleSelectionDialog(
            plan = restorePlan,
            inProgress = uiState.isRestoringBackup,
            onDismiss = {
                setupViewModel.clearRestorePlan()
                selectedBackupUri = null
            },
            onBack = {
                setupViewModel.clearRestorePlan()
                selectedBackupUri = null
            },
            onSelectionChanged = setupViewModel::updateRestorePlanSelection,
            onConfirm = {
                val uri = selectedBackupUri ?: return@BackupModuleSelectionDialog
                selectedBackupUri = null
                setupViewModel.restoreFromPlan(uri)
            }
        )
    }

    // Overlay for Corner Radius Customization
    AnimatedVisibility(
        visible = showCornerRadiusOverlay,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        BackHandler {
            showCornerRadiusOverlay = false
        }
        NavBarCornerRadiusContent(
            initialRadius = uiState.navBarCornerRadius.toFloat(),
            onRadiusChange = { setupViewModel.setNavBarCornerRadius(it) },
            onDone = { showCornerRadiusOverlay = false },
            onBack = { showCornerRadiusOverlay = false },
            isFullWidth = uiState.navBarStyle == "full_width"
        )
    }
}
