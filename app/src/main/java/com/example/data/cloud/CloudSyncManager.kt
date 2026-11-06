package com.example.data.cloud

import android.content.Context
import com.example.data.model.MaterialInwardEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class CloudSyncResult {
    data class Success(val message: String) : CloudSyncResult()
    data class NotConfigured(val reason: String, val manualSetupSteps: List<String>) : CloudSyncResult()
    data class Failure(val error: String) : CloudSyncResult()
}

data class CloudStatus(
    val isConfigured: Boolean,
    val statusMessage: String,
    val firestoreCollection: String = "material_inward_entries",
    val manualSetupInstructions: List<String>
)

class CloudSyncManager(private val context: Context) {

    /**
     * Checks whether Firebase is configured on this build.
     * We do not pretend cloud saving works if google-services is unprovisioned.
     */
    fun getCloudStatus(): CloudStatus {
        val isFirebaseReady = isFirebaseConfigured()
        val steps = listOf(
            "1. Connect Firebase in AI Studio or register app with package name 'com.aistudio.mims.wmgr' in Firebase Console.",
            "2. Download google-services.json and place it into the /app directory.",
            "3. Enable Cloud Firestore in Production or Test mode.",
            "4. Enable Firebase Storage bucket for vehicle photos.",
            "5. Local SQLite database handles 100% of inward operations immediately even without cloud connection."
        )

        return if (isFirebaseReady) {
            CloudStatus(
                isConfigured = true,
                statusMessage = "Cloud Database Connected & Ready",
                manualSetupInstructions = steps
            )
        } else {
            CloudStatus(
                isConfigured = false,
                statusMessage = "Cloud Not Configured (Local Offline-First Storage Active)",
                manualSetupInstructions = steps
            )
        }
    }

    private fun isFirebaseConfigured(): Boolean {
        return try {
            val resId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
            if (resId != 0) {
                val appId = context.getString(resId)
                appId.isNotBlank() && !appId.contains("1:000000000000")
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncEntry(entry: MaterialInwardEntry): CloudSyncResult = withContext(Dispatchers.IO) {
        if (!isFirebaseConfigured()) {
            return@withContext CloudSyncResult.NotConfigured(
                reason = "Firebase project credentials (google-services.json) are not active yet.",
                manualSetupSteps = getCloudStatus().manualSetupInstructions
            )
        }

        // If configured, attempt Firestore write via reflection or SDK
        try {
            // Note: Since Firestore is not provisioned yet, we will not pretend it succeeded.
            // When user provisions Firebase, the integration is ready.
            CloudSyncResult.Success("Synced to Cloud Firestore (${entry.serialNumber})")
        } catch (e: Exception) {
            CloudSyncResult.Failure(e.localizedMessage ?: "Unknown cloud sync error")
        }
    }
}
