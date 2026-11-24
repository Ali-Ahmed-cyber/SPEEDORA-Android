package com.speedora.app.data.network

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.speedora.app.core.model.DeviceCapabilities

class DeviceCapabilityInspector(private val context: Context) {

    fun inspect(): DeviceCapabilities {
        val pm = context.packageManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter

        return DeviceCapabilities(
            wifiDirectSupported = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT),
            wifiEnabled = wifiManager?.isWifiEnabled == true,
            nsdSupported = true,
            bluetoothSupported = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) && btAdapter != null,
            bluetoothEnabled = btAdapter?.isEnabled == true,
            androidSdkVersion = Build.VERSION.SDK_INT
        )
    }

    fun missingDiscoveryPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPerm(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                missing.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) {
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        return missing
    }

    fun missingBluetoothPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPerm(Manifest.permission.BLUETOOTH_SCAN)) missing.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) missing.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!hasPerm(Manifest.permission.BLUETOOTH_ADVERTISE)) missing.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        return missing
    }

    private fun hasPerm(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
