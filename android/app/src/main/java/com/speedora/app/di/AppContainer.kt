package com.speedora.app.di

import android.content.Context
import com.speedora.app.data.files.ContactsMigrationRepository
import com.speedora.app.data.files.InstalledAppsRepository
import com.speedora.app.data.files.MediaStoreRepository
import com.speedora.app.data.files.SafDocumentRepository
import com.speedora.app.data.files.SelectionManager
import com.speedora.app.data.local.SettingsDataStore
import com.speedora.app.data.local.SpeedoraDatabase
import com.speedora.app.data.network.BluetoothFallbackManager
import com.speedora.app.data.network.DeviceCapabilityInspector
import com.speedora.app.data.network.NsdDiscoveryManager
import com.speedora.app.data.network.WifiDirectManager
import com.speedora.app.transfer.TransferEngine

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database = SpeedoraDatabase.getInstance(appContext)
    val settingsDataStore = SettingsDataStore(appContext)

    val mediaStoreRepository = MediaStoreRepository(appContext)
    val installedAppsRepository = InstalledAppsRepository(appContext)
    val safDocumentRepository = SafDocumentRepository(appContext)
    val contactsMigrationRepository = ContactsMigrationRepository(appContext)
    val selectionManager = SelectionManager()

    val capabilityInspector = DeviceCapabilityInspector(appContext)
    val wifiDirectManager = WifiDirectManager(appContext)
    val nsdDiscoveryManager = NsdDiscoveryManager(appContext)
    val bluetoothFallbackManager = BluetoothFallbackManager(appContext)

    val transferEngine = TransferEngine(
        context = appContext,
        settingsDataStore = settingsDataStore,
        historyDao = database.transferHistoryDao(),
        trustedDeviceDao = database.trustedDeviceDao()
    )
}
