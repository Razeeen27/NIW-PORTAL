package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.cloud.CloudStatus
import com.example.data.cloud.CloudSyncManager
import com.example.data.cloud.CloudSyncResult
import com.example.data.local.AppDatabase
import com.example.data.model.MaterialInwardEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Calendar
import java.util.Locale

class MimsRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val entryDao = db.entryDao()
    private val sequenceDao = db.sequenceDao()
    private val cloudSyncManager = CloudSyncManager(context)

    fun getAllEntriesFlow(): Flow<List<MaterialInwardEntry>> = entryDao.getAllEntriesFlow()

    fun getTodayEntriesFlow(todayDate: String): Flow<List<MaterialInwardEntry>> =
        entryDao.getEntriesForDateFlow(todayDate)

    fun getTodayCountFlow(todayDate: String): Flow<Int> = entryDao.getCountForDateFlow(todayDate)

    fun getTotalCountFlow(): Flow<Int> = entryDao.getTotalCountFlow()

    fun getCloudStatus(): CloudStatus = cloudSyncManager.getCloudStatus()

    suspend fun getEntriesForMonth(monthPrefix: String): List<MaterialInwardEntry> = withContext(Dispatchers.IO) {
        entryDao.getEntriesForMonth(monthPrefix)
    }

    suspend fun getAllEntries(): List<MaterialInwardEntry> = withContext(Dispatchers.IO) {
        entryDao.getAllEntries()
    }

    /**
     * Checks if a vehicle with the given number has already entered today.
     * Returns previous entries on the same day if any, so the UI can display
     * an intelligent duplicate warning modal without blocking legitimate repeat trips.
     */
    suspend fun checkDuplicateVehicleToday(vehicleNumber: String, date: String): List<MaterialInwardEntry> =
        withContext(Dispatchers.IO) {
            val normalized = vehicleNumber.trim().replace("\\s+".toRegex(), "").uppercase(Locale.ROOT)
            val todayEntries = entryDao.getEntriesForDate(date)
            todayEntries.filter {
                it.vehicleNumber.trim().replace("\\s+".toRegex(), "").uppercase(Locale.ROOT) == normalized
            }
        }

    /**
     * Atomically generates the next sequential serial number (e.g., NIW-2026-0001)
     * and persists the new Material Inward Entry.
     */
    suspend fun createAndSaveEntry(
        entryDate: String,
        entryTime: String,
        vehicleNumber: String,
        customerName: String,
        driverName: String,
        driverNumber: String,
        photoPath: String?,
        recordedBy: String,
        notes: String = ""
    ): Result<MaterialInwardEntry> = withContext(Dispatchers.IO) {
        try {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val nextSeq = sequenceDao.getNextSequenceNumber(currentYear)
            val serialNumber = String.format(Locale.US, "NIW-%d-%04d", currentYear, nextSeq)

            val entry = MaterialInwardEntry(
                serialNumber = serialNumber,
                entryDate = entryDate.trim(),
                entryTime = entryTime.trim(),
                vehicleNumber = vehicleNumber.trim().uppercase(Locale.ROOT),
                customerName = customerName.trim(),
                driverName = driverName.trim(),
                driverNumber = driverNumber.trim(),
                photoPath = photoPath,
                recordedBy = recordedBy,
                notes = notes.trim(),
                recordedAtTimestamp = System.currentTimeMillis(),
                isSyncedToCloud = false,
                cloudSyncError = null
            )

            // Save to local Room SQLite database
            entryDao.insertEntry(entry)

            // Attempt cloud sync (if Firebase is configured)
            val cloudResult = cloudSyncManager.syncEntry(entry)
            when (cloudResult) {
                is CloudSyncResult.Success -> {
                    entryDao.updateSyncStatus(serialNumber, synced = true, error = null)
                }
                is CloudSyncResult.NotConfigured -> {
                    entryDao.updateSyncStatus(serialNumber, synced = false, error = "Local DB Active (Cloud Unconfigured)")
                }
                is CloudSyncResult.Failure -> {
                    entryDao.updateSyncStatus(serialNumber, synced = false, error = cloudResult.error)
                }
            }

            val savedEntry = entryDao.getEntryBySerialNumber(serialNumber) ?: entry
            Result.success(savedEntry)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copies a photo from temporary cache or camera output into secure internal storage.
     */
    suspend fun copyPhotoToInternalStorage(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val photosDir = File(context.filesDir, "vehicle_photos").apply { mkdirs() }
            val destFile = File(photosDir, "VEH_${System.currentTimeMillis()}.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { input: InputStream ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a captured Bitmap directly into secure internal storage.
     */
    suspend fun saveBitmapToInternalStorage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val photosDir = File(context.filesDir, "vehicle_photos").apply { mkdirs() }
            val destFile = File(photosDir, "VEH_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
