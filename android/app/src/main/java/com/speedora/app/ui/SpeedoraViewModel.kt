package com.speedora.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.speedora.app.SpeedoraApplication
import com.speedora.app.core.model.DeviceCapabilities
import com.speedora.app.core.model.DuplicateGroup
import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.IncomingTransferOffer
import com.speedora.app.core.model.InstalledAppItem
import com.speedora.app.core.model.LocalFileItem
import com.speedora.app.core.model.PeerDevice
import com.speedora.app.core.model.QrPairingPayload
import com.speedora.app.core.model.SortOrder
import com.speedora.app.core.model.TransferProgressSnapshot
import com.speedora.app.core.model.TransportType
import com.speedora.app.core.security.SpeedoraCrypto
import com.speedora.app.data.files.DeviceStorageSummary
import com.speedora.app.data.files.FileCategorizer
import com.speedora.app.data.files.SelectionSnapshot
import com.speedora.app.data.local.FavoriteFileEntity
import com.speedora.app.data.local.SpeedoraSettings
import com.speedora.app.data.local.TransferHistoryEntity
import com.speedora.app.data.local.TrustedDeviceEntity
import com.speedora.app.data.network.QrDecodeResult
import com.speedora.app.data.network.QrPairingCodec
import com.speedora.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

class SpeedoraViewModel(
    application: Application,
    val container: AppContainer
) : AndroidViewModel(application) {

    val settings: StateFlow<SpeedoraSettings> = container.settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SpeedoraSettings())

    val selection: StateFlow<SelectionSnapshot> = container.selectionManager.selection
    val transferProgress: StateFlow<TransferProgressSnapshot?> = container.transferEngine.progress
    val incomingOffer: StateFlow<IncomingTransferOffer?> = container.transferEngine.incomingOffer
    val isListeningForReceive: StateFlow<Boolean> = container.transferEngine.isListening
    val activeReceivePort: StateFlow<Int> = container.transferEngine.activePort

    val recentHistory: StateFlow<List<TransferHistoryEntity>> = container.database.transferHistoryDao()
        .observeRecent(6).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHistory: StateFlow<List<TransferHistoryEntity>> = container.database.transferHistoryDao()
        .observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trustedDevices: StateFlow<List<TrustedDeviceEntity>> = container.database.trustedDeviceDao()
        .observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteFileEntity>> = container.database.favoriteFileDao()
        .observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredPeers: StateFlow<List<PeerDevice>> = combine(
        container.wifiDirectManager.peers,
        container.nsdDiscoveryManager.peers
    ) { p2p, nsd ->
        (nsd + p2p).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _capabilities = MutableStateFlow(container.capabilityInspector.inspect())
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    private val _activeCategory = MutableStateFlow(FileCategory.PHOTOS)
    val activeCategory: StateFlow<FileCategory> = _activeCategory.asStateFlow()

    private val _categoryFiles = MutableStateFlow<List<LocalFileItem>>(emptyList())
    val categoryFiles: StateFlow<List<LocalFileItem>> = _categoryFiles.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _safFolderUris = MutableStateFlow<List<Uri>>(emptyList())
    val safFolderUris: StateFlow<List<Uri>> = _safFolderUris.asStateFlow()

    private val _activeFolderItems = MutableStateFlow<List<LocalFileItem>>(emptyList())
    val activeFolderItems: StateFlow<List<LocalFileItem>> = _activeFolderItems.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _isGridMode = MutableStateFlow(true)
    val isGridMode: StateFlow<Boolean> = _isGridMode.asStateFlow()

    private val _duplicates = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicates: StateFlow<List<DuplicateGroup>> = _duplicates.asStateFlow()

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    private val _storageSummary = MutableStateFlow(container.mediaStoreRepository.getStorageSummary())
    val storageSummary: StateFlow<DeviceStorageSummary> = _storageSummary.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _currentQrString = MutableStateFlow<String?>(null)
    val currentQrString: StateFlow<String?> = _currentQrString.asStateFlow()

    val filteredFiles: StateFlow<List<LocalFileItem>> = combine(
        _categoryFiles,
        _searchQuery,
        _sortOrder,
        favorites
    ) { files, query, sort, favs ->
        val favSet = favs.mapTo(HashSet()) { it.uriString }
        val searched = FileCategorizer.filterByQuery(files, query)
        val sorted = FileCategorizer.sortFiles(searched, sort)
        sorted.map { it.copy(isFavorite = it.uri.toString() in favSet) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        _safFolderUris.value = container.safDocumentRepository.getPersistedTreeUris()
    }

    fun clearStatusToast() { _statusToast.value = null }
    fun postMessage(msg: String) { _statusToast.value = msg }

    fun setCategory(category: FileCategory) {
        _activeCategory.value = category
        refreshCurrentCategory()
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun toggleGridMode() { _isGridMode.value = !_isGridMode.value }

    fun refreshCurrentCategory() {
        val cat = _activeCategory.value
        viewModelScope.launch {
            _isLoadingFiles.value = true
            try {
                when (cat) {
                    FileCategory.APPS -> {
                        _installedApps.value = container.installedAppsRepository.getLaunchableApps()
                    }
                    FileCategory.FOLDERS -> {
                        _safFolderUris.value = container.safDocumentRepository.getPersistedTreeUris()
                        val firstTree = _safFolderUris.value.firstOrNull()
                        _activeFolderItems.value = if (firstTree != null) {
                            container.safDocumentRepository.listDirectory(firstTree)
                        } else emptyList()
                    }
                    else -> {
                        _categoryFiles.value = container.mediaStoreRepository.queryCategory(cat)
                    }
                }
            } finally {
                _isLoadingFiles.value = false
            }
        }
    }

    fun openSafFolder(treeUri: Uri, persist: Boolean = false) {
        viewModelScope.launch {
            if (persist) {
                container.safDocumentRepository.persistTreePermission(treeUri)
                _safFolderUris.value = container.safDocumentRepository.getPersistedTreeUris()
            }
            _isLoadingFiles.value = true
            try {
                _activeFolderItems.value = container.safDocumentRepository.listDirectory(treeUri)
            } finally {
                _isLoadingFiles.value = false
            }
        }
    }

    fun toggleFileSelection(item: LocalFileItem) = container.selectionManager.toggle(item)
    fun selectAllVisible() = container.selectionManager.addAll(filteredFiles.value)
    fun removeSelectedById(id: String) = container.selectionManager.removeById(id)
    fun clearSelection() = container.selectionManager.clear()

    fun toggleAppSelection(app: InstalledAppItem) {
        val existingId = "apk:${app.packageName}"
        if (container.selectionManager.isSelected(existingId)) {
            container.selectionManager.removeById(existingId)
            return
        }
        if (!app.isExportable) {
            _statusToast.value = app.restrictionReason ?: "لا يمكن تصدير هذا التطبيق"
            return
        }
        viewModelScope.launch {
            val exported = container.installedAppsRepository.exportApkAsLocalFileItem(app)
            if (exported != null) {
                container.selectionManager.toggle(exported)
            } else {
                _statusToast.value = "تعذر تجهيز ملف APK للتطبيق ${app.appName}"
            }
        }
    }

    fun toggleFavorite(item: LocalFileItem) {
        viewModelScope.launch {
            val dao = container.database.favoriteFileDao()
            val uriStr = item.uri.toString()
            if (dao.isFavorite(uriStr)) {
                dao.removeFavorite(uriStr)
            } else {
                dao.addFavorite(
                    FavoriteFileEntity(
                        uriString = uriStr,
                        fileName = item.name,
                        sizeBytes = item.sizeBytes,
                        mimeType = item.mimeType,
                        categoryName = item.category.name,
                        addedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun scanDuplicates() {
        viewModelScope.launch {
            _isScanningDuplicates.value = true
            try {
                val all = container.mediaStoreRepository.queryCategory(FileCategory.ALL, limit = 1500)
                _duplicates.value = container.mediaStoreRepository.findDuplicatesBySha256(all)
            } finally {
                _isScanningDuplicates.value = false
            }
        }
    }

    fun startDiscovery() {
        _capabilities.value = container.capabilityInspector.inspect()
        _isDiscovering.value = true
        container.nsdDiscoveryManager.startDiscovery()
        if (container.capabilityInspector.missingDiscoveryPermissions().isEmpty()) {
            container.wifiDirectManager.startDiscovery(activeReceivePort.value)
        }
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        container.nsdDiscoveryManager.stopDiscovery()
        container.wifiDirectManager.stopDiscovery()
    }

    fun startReceivingMode() {
        container.transferEngine.startReceiveServer()
        viewModelScope.launch {
            val port = activeReceivePort.value
            val devName = settings.value.deviceName
            container.nsdDiscoveryManager.registerService(devName, port)
            generateTemporaryQrPayload(devName, port)
        }
    }

    fun stopReceivingMode() {
        container.nsdDiscoveryManager.unregisterService()
        container.transferEngine.stopReceiveServer()
        _currentQrString.value = null
    }

    fun generateTemporaryQrPayload(deviceName: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val localIp = findLocalWifiIpv4Address() ?: "0.0.0.0"
            val ephemeral = SpeedoraCrypto.generateEphemeralKeyPair()
            val payload = QrPairingPayload(
                version = 1,
                sessionId = SpeedoraCrypto.generateSessionId(),
                deviceName = deviceName,
                host = localIp,
                port = port,
                transport = TransportType.LOCAL_WIFI_NSD.name,
                ephemeralPublicKeyBase64 = SpeedoraCrypto.encodePublicKeyBase64(ephemeral.public),
                expiresAtEpochMillis = System.currentTimeMillis() + QrPairingCodec.DEFAULT_TTL_MILLIS
            )
            _currentQrString.value = QrPairingCodec.encode(payload)
        }
    }

    fun sendToPeer(peer: PeerDevice, onNavigateToTransfer: () -> Unit) {
        val selected = selection.value.items
        if (selected.isEmpty()) {
            _statusToast.value = "يرجى تحديد ملف واحد على الأقل قبل الإرسال"
            return
        }
        onNavigateToTransfer()
        viewModelScope.launch {
            val result = container.transferEngine.sendFiles(peer, selected)
            if (result.isSuccess) {
                container.database.trustedDeviceDao().upsert(
                    TrustedDeviceEntity(
                        deviceId = peer.id,
                        displayName = peer.name,
                        originalName = peer.name,
                        lastTransportType = peer.transport.name,
                        lastKeyFingerprint = transferProgress.value?.sasCode.orEmpty(),
                        autoAcceptIncoming = false,
                        pairedAtMillis = System.currentTimeMillis(),
                        lastConnectedMillis = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun connectByQrText(rawQr: String, onNavigateToTransfer: () -> Unit) {
        when (val decoded = QrPairingCodec.decode(rawQr)) {
            is QrDecodeResult.Expired -> _statusToast.value = "انتهت صلاحية رمز QR المؤقت، أنشئ رمزاً جديداً"
            is QrDecodeResult.Invalid -> _statusToast.value = decoded.reason
            is QrDecodeResult.Valid -> {
                val p = decoded.payload
                val peer = PeerDevice(
                    id = "qr:${p.sessionId}",
                    name = p.deviceName,
                    hostAddress = p.host,
                    port = p.port,
                    transport = runCatching { TransportType.valueOf(p.transport) }.getOrDefault(TransportType.LOCAL_WIFI_NSD),
                    quality = com.speedora.app.core.model.ConnectionQuality.HIGH_BANDWIDTH
                )
                sendToPeer(peer, onNavigateToTransfer)
            }
        }
    }

    fun connectManualIp(host: String, port: Int, onNavigateToTransfer: () -> Unit) {
        val trimmed = host.trim()
        if (trimmed.isBlank() || port !in 1..65535) {
            _statusToast.value = "أدخل عنوان IP ومنفذ صحيحين"
            return
        }
        val peer = PeerDevice(
            id = "manual:$trimmed:$port",
            name = "$trimmed:$port",
            hostAddress = trimmed,
            port = port,
            transport = TransportType.MANUAL_IP,
            quality = com.speedora.app.core.model.ConnectionQuality.STANDARD_LOCAL
        )
        sendToPeer(peer, onNavigateToTransfer)
    }

    fun startSelectiveMigration(
        includePhotos: Boolean,
        includeVideos: Boolean,
        includeAudio: Boolean,
        includeDocs: Boolean,
        includeDownloads: Boolean,
        includeContacts: Boolean,
        onReadyToPickDevice: () -> Unit
    ) {
        viewModelScope.launch {
            container.selectionManager.clear()
            val batch = mutableListOf<LocalFileItem>()
            if (includePhotos) batch += container.mediaStoreRepository.queryCategory(FileCategory.PHOTOS, 500)
            if (includeVideos) batch += container.mediaStoreRepository.queryCategory(FileCategory.VIDEOS, 200)
            if (includeAudio) batch += container.mediaStoreRepository.queryCategory(FileCategory.MUSIC, 500)
            if (includeDocs) batch += container.mediaStoreRepository.queryCategory(FileCategory.DOCUMENTS, 500)
            if (includeDownloads) batch += container.mediaStoreRepository.queryCategory(FileCategory.DOWNLOADS, 300)
            if (includeContacts && container.contactsMigrationRepository.hasContactsPermission()) {
                container.contactsMigrationRepository.exportContactsToVcf()?.let { batch += it }
            }
            if (batch.isEmpty()) {
                _statusToast.value = "لم يتم العثور على عناصر صالحة للنقل ضمن الفئات المحددة"
            } else {
                container.selectionManager.addAll(batch)
                onReadyToPickDevice()
            }
        }
    }

    fun shareFileIntent(item: LocalFileItem): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun findLocalWifiIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }

    class Factory(private val app: SpeedoraApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpeedoraViewModel(app, app.container) as T
        }
    }
}
