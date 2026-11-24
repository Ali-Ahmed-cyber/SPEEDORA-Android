package com.speedora.app.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.speedora.app.core.model.ConnectionQuality
import com.speedora.app.core.model.PairingState
import com.speedora.app.core.model.PeerDevice
import com.speedora.app.core.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local Wi-Fi mDNS / Network Service Discovery manager (_speedora._tcp.).
 * Serializes NsdManager.resolveService calls to prevent Android's concurrent resolve failures.
 */
class NsdDiscoveryManager(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = "_speedora._tcp."
    }

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null

    private val discoveredMap = ConcurrentHashMap<String, PeerDevice>()
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    fun registerService(deviceName: String, port: Int) {
        unregisterService()
        val mgr = nsdManager ?: return
        val safeServiceName = "SPEEDORA-${deviceName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24)}"
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = safeServiceName
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("dev", deviceName.take(32))
            setAttribute("ver", "1")
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                registeredServiceName = null
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching {
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun unregisterService() {
        val listener = registrationListener ?: return
        runCatching { nsdManager?.unregisterService(listener) }
        registrationListener = null
        registeredServiceName = null
    }

    fun startDiscovery() {
        stopDiscovery()
        val mgr = nsdManager ?: return
        acquireMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("_speedora")) return
                if (service.serviceName == registeredServiceName) return
                resolveQueue.add(service)
                processNextResolve()
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredMap.remove(service.serviceName)
                _peers.value = discoveredMap.values.toList()
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        runCatching {
            mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        resolveQueue.clear()
        resolving.set(false)
        releaseMulticastLock()
    }

    private fun processNextResolve() {
        if (!resolving.compareAndSet(false, true)) return
        val next = resolveQueue.poll()
        if (next == null) {
            resolving.set(false)
            return
        }
        val mgr = nsdManager ?: run {
            resolving.set(false)
            return
        }

        @Suppress("DEPRECATION")
        mgr.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolving.set(false)
                processNextResolve()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                if (host != null && serviceInfo.serviceName != registeredServiceName) {
                    val attrName = serviceInfo.attributes["dev"]?.toString(Charsets.UTF_8)
                    val displayName = attrName?.takeIf { it.isNotBlank() } ?: serviceInfo.serviceName
                    discoveredMap[serviceInfo.serviceName] = PeerDevice(
                        id = "nsd:${serviceInfo.serviceName}",
                        name = displayName,
                        hostAddress = host,
                        port = serviceInfo.port,
                        transport = TransportType.LOCAL_WIFI_NSD,
                        quality = ConnectionQuality.HIGH_BANDWIDTH,
                        pairingState = PairingState.DISCOVERED
                    )
                    _peers.value = discoveredMap.values.toList()
                }
                resolving.set(false)
                processNextResolve()
            }
        })
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager?.createMulticastLock("speedora_nsd")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
    }
}
