package com.smartexpense.tracker.data.json

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.smartexpense.tracker.data.model.AppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages all local JSON storage operations.
 * Data is persisted to a single JSON file in the app's private directory.
 */
class JsonStorageManager(private val context: Context) {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    private val mutex = Mutex()
    private val fileName = "smart_expense_data.json"
    private val backupFileName = "smart_expense_data_backup.json"

    private var cachedData: AppData? = null

    private fun getFile(): File = File(context.filesDir, fileName)
    private fun getBackupFile(): File = File(context.filesDir, backupFileName)

    /**
     * Load app data from JSON file. Returns default data if file doesn't exist.
     */
    suspend fun loadData(): AppData = mutex.withLock {
        cachedData?.let { return it }

        withContext(Dispatchers.IO) {
            val file = getFile()
            if (file.exists()) {
                try {
                    val json = file.readText()
                    val data = gson.fromJson(json, AppData::class.java)
                    cachedData = data
                    data
                } catch (e: Exception) {
                    // Try backup
                    try {
                        val backup = getBackupFile()
                        if (backup.exists()) {
                            val json = backup.readText()
                            val data = gson.fromJson(json, AppData::class.java)
                            cachedData = data
                            data
                        } else {
                            val default = AppData()
                            cachedData = default
                            default
                        }
                    } catch (e2: Exception) {
                        val default = AppData()
                        cachedData = default
                        default
                    }
                }
            } else {
                val default = AppData()
                saveDataInternal(default)
                cachedData = default
                default
            }
        }
    }

    /**
     * Save app data to JSON file with backup.
     */
    suspend fun saveData(data: AppData) = mutex.withLock {
        withContext(Dispatchers.IO) {
            saveDataInternal(data.copy(lastUpdated = System.currentTimeMillis()))
        }
    }

    private fun saveDataInternal(data: AppData) {
        val file = getFile()
        val backupFile = getBackupFile()

        // Create backup of existing data
        if (file.exists()) {
            file.copyTo(backupFile, overwrite = true)
        }

        // Write new data
        val json = gson.toJson(data)
        file.writeText(json)
        cachedData = data
    }

    /**
     * Export data as JSON string for sharing.
     */
    suspend fun exportData(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val data = cachedData ?: loadData()
            gson.toJson(data)
        }
    }

    /**
     * Import data from JSON string.
     */
    suspend fun importData(jsonString: String): Result<AppData> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val data = gson.fromJson(jsonString, AppData::class.java)
                saveDataInternal(data)
                Result.success(data)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Clear all data and reset to defaults.
     */
    suspend fun clearData() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val default = AppData()
            saveDataInternal(default)
        }
    }

    /**
     * Get storage file size info.
     */
    fun getStorageInfo(): StorageInfo {
        val file = getFile()
        return StorageInfo(
            fileSizeBytes = if (file.exists()) file.length() else 0,
            lastModified = if (file.exists()) file.lastModified() else 0,
            backupExists = getBackupFile().exists()
        )
    }

    data class StorageInfo(
        val fileSizeBytes: Long,
        val lastModified: Long,
        val backupExists: Boolean
    )
}
