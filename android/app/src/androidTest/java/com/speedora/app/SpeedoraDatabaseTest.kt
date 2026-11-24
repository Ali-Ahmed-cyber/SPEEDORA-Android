package com.speedora.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.speedora.app.data.local.SpeedoraDatabase
import com.speedora.app.data.local.TransferHistoryEntity
import com.speedora.app.data.local.TrustedDeviceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeedoraDatabaseTest {

    private lateinit var db: SpeedoraDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpeedoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryHistoryAndTrustedDevices() = runBlocking {
        db.transferHistoryDao().insert(
            TransferHistoryEntity(
                sessionId = "s1",
                fileName = "archive.zip",
                fileSizeBytes = 4096L,
                mimeType = "application/zip",
                direction = "SEND",
                peerDeviceName = "Galaxy S24",
                peerEndpoint = "192.168.1.50:45892",
                transportType = "LOCAL_WIFI_NSD",
                status = "COMPLETED",
                integrityVerified = true,
                sha256Hex = "abc",
                averageSpeedBytesPerSec = 12_000_000.0,
                timestampMillis = 1000L
            )
        )
        db.trustedDeviceDao().upsert(
            TrustedDeviceEntity(
                deviceId = "dev1",
                displayName = "My Tablet",
                originalName = "Tab S9",
                lastTransportType = "WIFI_DIRECT",
                lastKeyFingerprint = "123456",
                pairedAtMillis = 1000L,
                lastConnectedMillis = 2000L
            )
        )

        assertEquals(1, db.transferHistoryDao().observeAll().first().size)
        assertEquals("My Tablet", db.trustedDeviceDao().getById("dev1")?.displayName)
    }
}
