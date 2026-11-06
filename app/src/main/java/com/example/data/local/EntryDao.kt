package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MaterialInwardEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: MaterialInwardEntry): Long

    @Update
    suspend fun updateEntry(entry: MaterialInwardEntry)

    @Query("SELECT * FROM inward_entries ORDER BY recordedAtTimestamp DESC")
    fun getAllEntriesFlow(): Flow<List<MaterialInwardEntry>>

    @Query("SELECT * FROM inward_entries ORDER BY recordedAtTimestamp DESC")
    suspend fun getAllEntries(): List<MaterialInwardEntry>

    @Query("SELECT * FROM inward_entries WHERE entryDate = :date ORDER BY recordedAtTimestamp DESC")
    fun getEntriesForDateFlow(date: String): Flow<List<MaterialInwardEntry>>

    @Query("SELECT * FROM inward_entries WHERE entryDate = :date ORDER BY recordedAtTimestamp DESC")
    suspend fun getEntriesForDate(date: String): List<MaterialInwardEntry>

    @Query("SELECT * FROM inward_entries WHERE entryDate LIKE :monthPrefix || '%' ORDER BY recordedAtTimestamp ASC")
    suspend fun getEntriesForMonth(monthPrefix: String): List<MaterialInwardEntry>

    @Query("SELECT * FROM inward_entries WHERE vehicleNumber = :vehicleNumber AND entryDate = :date ORDER BY recordedAtTimestamp DESC")
    suspend fun getEntriesForVehicleOnDate(vehicleNumber: String, date: String): List<MaterialInwardEntry>

    @Query("SELECT COUNT(*) FROM inward_entries WHERE entryDate = :date")
    fun getCountForDateFlow(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM inward_entries")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT * FROM inward_entries WHERE serialNumber = :serialNumber LIMIT 1")
    suspend fun getEntryBySerialNumber(serialNumber: String): MaterialInwardEntry?

    @Query("SELECT * FROM inward_entries WHERE isSyncedToCloud = 0")
    suspend fun getUnsyncedEntries(): List<MaterialInwardEntry>

    @Query("UPDATE inward_entries SET isSyncedToCloud = :synced, cloudSyncError = :error WHERE serialNumber = :serialNumber")
    suspend fun updateSyncStatus(serialNumber: String, synced: Boolean, error: String?)
}
