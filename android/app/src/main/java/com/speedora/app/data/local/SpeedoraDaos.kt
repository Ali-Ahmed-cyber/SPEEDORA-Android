package com.speedora.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<TransferHistoryEntity>>

    @Query("SELECT * FROM transfer_history ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 6): Flow<List<TransferHistoryEntity>>

    @Query("SELECT * FROM transfer_history WHERE status = :status ORDER BY timestampMillis DESC")
    fun observeByStatus(status: String): Flow<List<TransferHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TransferHistoryEntity): Long

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()
}

@Dao
interface TrustedDeviceDao {
    @Query("SELECT * FROM trusted_devices ORDER BY lastConnectedMillis DESC")
    fun observeAll(): Flow<List<TrustedDeviceEntity>>

    @Query("SELECT * FROM trusted_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getById(deviceId: String): TrustedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: TrustedDeviceEntity)

    @Query("UPDATE trusted_devices SET displayName = :newName WHERE deviceId = :deviceId")
    suspend fun rename(deviceId: String, newName: String)

    @Query("UPDATE trusted_devices SET autoAcceptIncoming = :autoAccept WHERE deviceId = :deviceId")
    suspend fun setAutoAccept(deviceId: String, autoAccept: Boolean)

    @Query("DELETE FROM trusted_devices WHERE deviceId = :deviceId")
    suspend fun remove(deviceId: String)

    @Query("DELETE FROM trusted_devices")
    suspend fun clearAll()
}

@Dao
interface FavoriteFileDao {
    @Query("SELECT * FROM favorite_files ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<FavoriteFileEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_files WHERE uriString = :uriString)")
    suspend fun isFavorite(uriString: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(item: FavoriteFileEntity)

    @Query("DELETE FROM favorite_files WHERE uriString = :uriString")
    suspend fun removeFavorite(uriString: String)
}
