package com.speedora.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TransferHistoryEntity::class,
        TrustedDeviceEntity::class,
        FavoriteFileEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SpeedoraDatabase : RoomDatabase() {
    abstract fun transferHistoryDao(): TransferHistoryDao
    abstract fun trustedDeviceDao(): TrustedDeviceDao
    abstract fun favoriteFileDao(): FavoriteFileDao

    companion object {
        @Volatile
        private var INSTANCE: SpeedoraDatabase? = null

        fun getInstance(context: Context): SpeedoraDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpeedoraDatabase::class.java,
                    "speedora_local.db"
                ).build().also { INSTANCE = it }
            }
    }
}
