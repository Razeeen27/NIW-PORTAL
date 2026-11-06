package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inward_entries")
data class MaterialInwardEntry(
    @PrimaryKey
    val serialNumber: String, // e.g. NIW-2026-0001
    val entryDate: String,    // YYYY-MM-DD
    val entryTime: String,    // HH:mm (24-hr or 12-hr with AM/PM)
    val vehicleNumber: String,// e.g. MH-12-AB-1234
    val customerName: String, // Company / Customer Name
    val driverName: String,   // Driver full name
    val driverNumber: String, // Driver phone number
    val photoPath: String?,   // Local file path or storage URL
    val recordedBy: String,   // Operator name / role
    val recordedAtTimestamp: Long = System.currentTimeMillis(),
    val isSyncedToCloud: Boolean = false,
    val cloudSyncError: String? = null,
    val notes: String = ""
)
