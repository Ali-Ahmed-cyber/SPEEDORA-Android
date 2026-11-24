package com.speedora.app.data.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.speedora.app.core.model.ConnectionQuality
import com.speedora.app.core.model.PairingState
import com.speedora.app.core.model.PeerDevice
import com.speedora.app.core.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiDirectConnectionState(
    val isDiscovering: Boolean = false,
    val isConnected: Boolean = false,
    val isGroupOwner: Boolean = false,
    val groupOwnerAddress: String? = null,
    val statusMessage: String? = null
)

class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    private val _connectionState = MutableStateFlow(WifiDirectConnectionState())
    val connectionState: StateFlow<WifiDirectConnectionState> = _connectionState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val ch = channel ?: return
                    manager?.requestPeers(ch) { peerList ->
                        _peers.value = peerList.deviceList.map { it.toPeerDevice() }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val ch = channel ?: return
                    @Suppress("DEPRECATION")
                    val netInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }
                    if (netInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(ch) { info: WifiP2pInfo ->
                            _connectionState.value = _connectionState.value.copy(
                                isConnected = info.groupFormed,
                                isGroupOwner = info.isGroupOwner,
                                groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                            )
                        }
                    } else {
                        _connectionState.value = _connectionState.value.copy(
                            isConnected = false,
                            groupOwnerAddress = null
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(defaultTransferPort: Int) {
        val mgr = manager ?: run {
            _connectionState.value = _connectionState.value.copy(
                statusMessage = "Wi-Fi Direct غير مدعوم على هذا الجهاز"
            )
            return
        }
        if (channel == null) {
            channel = mgr.initialize(context, Looper.getMainLooper(), null)
        }
        registerReceiverIfNeeded()
        _connectionState.value = _connectionState.value.copy(isDiscovering = true, statusMessage = null)

        mgr.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionState.value = _connectionState.value.copy(isDiscovering = true)
            }

            override fun onFailure(reason: Int) {
                _connectionState.value = _connectionState.value.copy(
                    isDiscovering = false,
                    statusMessage = "تعذر بدء اكتشاف Wi-Fi Direct (رمز $reason)"
                )
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(macAddress: String, onResult: (Boolean, String?) -> Unit) {
        val mgr = manager ?: return onResult(false, "Wi-Fi Direct unavailable")
        val ch = channel ?: mgr.initialize(context, Looper.getMainLooper(), null).also { channel = it }
        registerReceiverIfNeeded()

        val config = WifiP2pConfig().apply {
            deviceAddress = macAddress
            wps.setup = WpsInfo.PBC
        }
        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onResult(true, null)
            override fun onFailure(reason: Int) = onResult(false, "فشل الاتصال عبر Wi-Fi Direct ($reason)")
        })
    }

    @SuppressLint("MissingPermission")
    fun createGroupForReceiver(onResult: (Boolean, String?) -> Unit) {
        val mgr = manager ?: return onResult(false, "Wi-Fi Direct unavailable")
        val ch = channel ?: mgr.initialize(context, Looper.getMainLooper(), null).also { channel = it }
        registerReceiverIfNeeded()
        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onResult(true, null)
            override fun onFailure(reason: Int) = onResult(false, "تعذر إنشاء مجموعة Wi-Fi Direct ($reason)")
        })
    }

    fun stopDiscovery() {
        val ch = channel
        if (ch != null) {
            runCatching { manager?.stopPeerDiscovery(ch, null) }
        }
        unregisterReceiverIfNeeded()
        _connectionState.value = _connectionState.value.copy(isDiscovering = false)
    }

    fun disconnectGroup() {
        val ch = channel ?: return
        runCatching { manager?.removeGroup(ch, null) }
        _connectionState.value = WifiDirectConnectionState()
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    private fun WifiP2pDevice.toPeerDevice(): PeerDevice =
        PeerDevice(
            id = "p2p:${deviceAddress}",
            name = deviceName.ifBlank { "Android Device" },
            hostAddress = null,
            port = 45892,
            transport = TransportType.WIFI_DIRECT,
            quality = ConnectionQuality.HIGH_BANDWIDTH,
            pairingState = when (status) {
                WifiP2pDevice.CONNECTED -> PairingState.PAIRED_TEMPORARY
                WifiP2pDevice.INVITED -> PairingState.CONNECTING
                else -> PairingState.DISCOVERED
            },
            macAddress = deviceAddress
        )
}
