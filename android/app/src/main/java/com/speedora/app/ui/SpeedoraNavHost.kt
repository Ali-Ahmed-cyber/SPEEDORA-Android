package com.speedora.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.speedora.app.R
import com.speedora.app.core.model.FileCategory
import com.speedora.app.ui.screens.DiscoveryScreen
import com.speedora.app.ui.screens.FavoritesScreen
import com.speedora.app.ui.screens.FileManagerScreen
import com.speedora.app.ui.screens.HistoryScreen
import com.speedora.app.ui.screens.HomeScreen
import com.speedora.app.ui.screens.MigrationScreen
import com.speedora.app.ui.screens.QrScannerScreen
import com.speedora.app.ui.screens.ReceiveScreen
import com.speedora.app.ui.screens.SendPickerScreen
import com.speedora.app.ui.screens.SettingsScreen
import com.speedora.app.ui.screens.SplashScreen
import com.speedora.app.ui.screens.TransferScreen
import com.speedora.app.ui.screens.TrustedDevicesScreen
import kotlinx.coroutines.launch

object SpeedoraRoutes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val SEND = "send"
    const val RECEIVE = "receive"
    const val FILES = "files"
    const val DISCOVERY = "discovery"
    const val QR_SCAN = "qr_scan"
    const val TRANSFER = "transfer"
    const val HISTORY = "history"
    const val MIGRATION = "migration"
    const val FAVORITES = "favorites"
    const val TRUSTED = "trusted"
    const val SETTINGS = "settings"
}

@Composable
fun SpeedoraNavHost(viewModel: SpeedoraViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()
    val incomingOffer by viewModel.incomingOffer.collectAsStateWithLifecycle()
    val isListening by viewModel.isListeningForReceive.collectAsStateWithLifecycle()
    val activePort by viewModel.activeReceivePort.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    val trustedDevices by viewModel.trustedDevices.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val peers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val filteredFiles by viewModel.filteredFiles.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val safFolderUris by viewModel.safFolderUris.collectAsStateWithLifecycle()
    val activeFolderItems by viewModel.activeFolderItems.collectAsStateWithLifecycle()
    val isLoadingFiles by viewModel.isLoadingFiles.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isGridMode by viewModel.isGridMode.collectAsStateWithLifecycle()
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val isScanningDuplicates by viewModel.isScanningDuplicates.collectAsStateWithLifecycle()
    val storageSummary by viewModel.storageSummary.collectAsStateWithLifecycle()
    val currentQr by viewModel.currentQrString.collectAsStateWithLifecycle()
    val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()

    LaunchedEffect(statusToast) {
        statusToast?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusToast()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(
        SpeedoraRoutes.HOME,
        SpeedoraRoutes.SEND,
        SpeedoraRoutes.RECEIVE,
        SpeedoraRoutes.FILES
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == SpeedoraRoutes.HOME,
                        onClick = { navController.navigate(SpeedoraRoutes.HOME) { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == SpeedoraRoutes.SEND,
                        onClick = { navController.navigate(SpeedoraRoutes.SEND) { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_send)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == SpeedoraRoutes.RECEIVE,
                        onClick = { navController.navigate(SpeedoraRoutes.RECEIVE) { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_receive)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == SpeedoraRoutes.FILES,
                        onClick = { navController.navigate(SpeedoraRoutes.FILES) { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_files)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SpeedoraRoutes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(SpeedoraRoutes.SPLASH) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(SpeedoraRoutes.HOME) {
                            popUpTo(SpeedoraRoutes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(SpeedoraRoutes.HOME) {
                HomeScreen(
                    deviceName = settings.deviceName,
                    isDiscovering = isDiscovering,
                    isAdvertising = isListening,
                    discoveredPeers = peers,
                    activeTransfer = transferProgress,
                    recentHistory = recentHistory,
                    trustedDevices = trustedDevices,
                    onSendClick = { navController.navigate(SpeedoraRoutes.SEND) },
                    onReceiveClick = { navController.navigate(SpeedoraRoutes.RECEIVE) },
                    onDiscoveryClick = { navController.navigate(SpeedoraRoutes.DISCOVERY) },
                    onQrClick = { navController.navigate(SpeedoraRoutes.QR_SCAN) },
                    onFilesClick = { navController.navigate(SpeedoraRoutes.FILES) },
                    onAppsClick = {
                        viewModel.setCategory(FileCategory.APPS)
                        navController.navigate(SpeedoraRoutes.SEND)
                    },
                    onHistoryClick = { navController.navigate(SpeedoraRoutes.HISTORY) },
                    onMigrationClick = { navController.navigate(SpeedoraRoutes.MIGRATION) },
                    onFavoritesClick = { navController.navigate(SpeedoraRoutes.FAVORITES) },
                    onTrustedClick = { navController.navigate(SpeedoraRoutes.TRUSTED) },
                    onSettingsClick = { navController.navigate(SpeedoraRoutes.SETTINGS) },
                    onActiveTransferClick = { navController.navigate(SpeedoraRoutes.TRANSFER) }
                )
            }

            composable(SpeedoraRoutes.SEND) {
                SendPickerScreen(
                    activeCategory = activeCategory,
                    files = filteredFiles,
                    installedApps = installedApps,
                    safFolderUris = safFolderUris,
                    activeFolderItems = activeFolderItems,
                    selection = selection,
                    isLoading = isLoadingFiles,
                    searchQuery = searchQuery,
                    sortOrder = sortOrder,
                    isGridMode = isGridMode,
                    hasMediaPermission = viewModel.container.mediaStoreRepository.hasMediaPermissionFor(activeCategory),
                    installedAppsRepository = viewModel.container.installedAppsRepository,
                    onCategorySelected = viewModel::setCategory,
                    onSearchChange = viewModel::setSearchQuery,
                    onSortChange = viewModel::setSortOrder,
                    onToggleGrid = viewModel::toggleGridMode,
                    onSelectAll = viewModel::selectAllVisible,
                    onToggleFile = viewModel::toggleFileSelection,
                    onToggleApp = viewModel::toggleAppSelection,
                    onRemoveSelected = viewModel::removeSelectedById,
                    onClearSelection = viewModel::clearSelection,
                    onOpenSafFolder = viewModel::openSafFolder,
                    onPermissionsGranted = viewModel::refreshCurrentCategory,
                    onContinueToDevicePicker = { navController.navigate(SpeedoraRoutes.DISCOVERY) }
                )
            }

            composable(SpeedoraRoutes.DISCOVERY) {
                DiscoveryScreen(
                    capabilities = capabilities,
                    missingPermissions = viewModel.container.capabilityInspector.missingDiscoveryPermissions(),
                    peers = peers,
                    isDiscovering = isDiscovering,
                    selectedCount = selection.totalCount,
                    onStartDiscovery = viewModel::startDiscovery,
                    onStopDiscovery = viewModel::stopDiscovery,
                    onSelectPeer = { peer ->
                        viewModel.sendToPeer(peer) { navController.navigate(SpeedoraRoutes.TRANSFER) }
                    },
                    onConnectManualIp = { host, port ->
                        viewModel.connectManualIp(host, port) { navController.navigate(SpeedoraRoutes.TRANSFER) }
                    },
                    onOpenQrScanner = { navController.navigate(SpeedoraRoutes.QR_SCAN) }
                )
            }

            composable(SpeedoraRoutes.QR_SCAN) {
                QrScannerScreen(
                    onQrScanned = { raw ->
                        navController.popBackStack()
                        viewModel.connectByQrText(raw) { navController.navigate(SpeedoraRoutes.TRANSFER) }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(SpeedoraRoutes.RECEIVE) {
                ReceiveScreen(
                    deviceName = settings.deviceName,
                    isListening = isListening,
                    activePort = activePort,
                    qrPayloadText = currentQr,
                    incomingOffer = incomingOffer,
                    onStartListening = viewModel::startReceivingMode,
                    onStopListening = viewModel::stopReceivingMode,
                    onRefreshQr = { viewModel.generateTemporaryQrPayload(settings.deviceName, activePort) },
                    onRespondOffer = { accepted ->
                        viewModel.container.transferEngine.respondToIncomingOffer(accepted)
                        if (accepted) navController.navigate(SpeedoraRoutes.TRANSFER)
                    }
                )
            }

            composable(SpeedoraRoutes.TRANSFER) {
                TransferScreen(
                    snapshot = transferProgress,
                    onPause = viewModel.container.transferEngine::pauseActiveTransfer,
                    onResume = viewModel.container.transferEngine::resumeActiveTransfer,
                    onCancel = viewModel.container.transferEngine::cancelActiveTransfer,
                    onBackHome = {
                        navController.navigate(SpeedoraRoutes.HOME) {
                            popUpTo(SpeedoraRoutes.HOME) { inclusive = true }
                        }
                    }
                )
            }

            composable(SpeedoraRoutes.FILES) {
                FileManagerScreen(
                    activeCategory = activeCategory,
                    files = filteredFiles,
                    storageSummary = storageSummary,
                    duplicates = duplicates,
                    isScanningDuplicates = isScanningDuplicates,
                    searchQuery = searchQuery,
                    mediaStoreRepository = viewModel.container.mediaStoreRepository,
                    onCategorySelected = viewModel::setCategory,
                    onSearchChange = viewModel::setSearchQuery,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onScanDuplicates = viewModel::scanDuplicates,
                    onShareFile = { item ->
                        context.startActivity(
                            android.content.Intent.createChooser(viewModel.shareFileIntent(item), item.name)
                        )
                    },
                    onRefresh = viewModel::refreshCurrentCategory
                )
            }

            composable(SpeedoraRoutes.MIGRATION) {
                MigrationScreen(
                    onStartMigration = { p, v, a, d, dl, c ->
                        viewModel.startSelectiveMigration(p, v, a, d, dl, c) {
                            navController.navigate(SpeedoraRoutes.DISCOVERY)
                        }
                    }
                )
            }

            composable(SpeedoraRoutes.HISTORY) {
                HistoryScreen(
                    entries = allHistory,
                    onClearHistory = {
                        scope.launch { viewModel.container.database.transferHistoryDao().clearAll() }
                    }
                )
            }

            composable(SpeedoraRoutes.TRUSTED) {
                TrustedDevicesScreen(
                    devices = trustedDevices,
                    onRename = { id, newName ->
                        scope.launch { viewModel.container.database.trustedDeviceDao().rename(id, newName) }
                    },
                    onToggleAutoAccept = { id, autoAccept ->
                        scope.launch { viewModel.container.database.trustedDeviceDao().setAutoAccept(id, autoAccept) }
                    },
                    onForget = { id ->
                        scope.launch { viewModel.container.database.trustedDeviceDao().remove(id) }
                    }
                )
            }

            composable(SpeedoraRoutes.FAVORITES) {
                FavoritesScreen(
                    favorites = favorites,
                    onRemoveFavorite = { uriStr ->
                        scope.launch { viewModel.container.database.favoriteFileDao().removeFavorite(uriStr) }
                    }
                )
            }

            composable(SpeedoraRoutes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    storageSummary = storageSummary,
                    onLanguageChange = { code -> scope.launch { viewModel.container.settingsDataStore.setLanguage(code) } },
                    onDarkThemeChange = { dark -> scope.launch { viewModel.container.settingsDataStore.setDarkTheme(dark) } },
                    onAnimationIntensityChange = { v -> scope.launch { viewModel.container.settingsDataStore.setAnimationIntensity(v) } },
                    onReducedMotionChange = { r -> scope.launch { viewModel.container.settingsDataStore.setReducedMotion(r) } },
                    onBatterySaverChange = { b -> scope.launch { viewModel.container.settingsDataStore.setBatterySavingMode(b) } },
                    onHapticsChange = { h -> scope.launch { viewModel.container.settingsDataStore.setHapticFeedback(h) } },
                    onSoundsChange = { s -> scope.launch { viewModel.container.settingsDataStore.setSoundEffects(s) } },
                    onDeviceNameChange = { n -> scope.launch { viewModel.container.settingsDataStore.setDeviceName(n) } },
                    onDownloadTreeChange = { u -> scope.launch { viewModel.container.settingsDataStore.setCustomDownloadTreeUri(u) } },
                    onRequireApprovalChange = { req -> scope.launch { viewModel.container.settingsDataStore.setRequireIncomingApproval(req) } },
                    onAllowTrustedAutoChange = { allow -> scope.launch { viewModel.container.settingsDataStore.setAllowTrustedAutoAccept(allow) } }
                )
            }
        }
    }
}
