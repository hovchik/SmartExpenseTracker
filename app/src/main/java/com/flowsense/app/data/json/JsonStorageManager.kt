package com.flowsense.app.data.json

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.flowsense.app.data.model.AppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Fixes null non-null String fields that Gson may produce for older JSON data. */
private fun AppData.sanitizeTransactions(): AppData {
    val sanitized = transactions.map { it.sanitized() }
    return if (sanitized != transactions) copy(transactions = sanitized) else this
}

/**
 * Manages all local JSON storage operations.
 * Data is persisted to a single JSON file in the app's private directory.
 */
class JsonStorageManager(private val context: Context) {

    private companion object {
        const val TAG = "JsonStorageManager"
        const val MAX_CORRUPT_COPIES = 3
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    private val mutex = Mutex()
    private val fileName = "flowsense_data.json"
    private val backupFileName = "flowsense_data_backup.json"

    @Volatile
    private var cachedData: AppData? = null

    private fun getFile(): File = File(context.filesDir, fileName)
    private fun getBackupFile(): File = File(context.filesDir, backupFileName)

    /**
     * Load app data from JSON file. Returns default data if file doesn't exist.
     */
    suspend fun loadData(): AppData = mutex.withLock {
        cachedData?.let { return it }

        withContext(Dispatchers.IO) {
            // An unreadable file is moved aside (see [readOrQuarantine]) so the next
            // save can never copy it over a good backup.
            val primary = getFile().let { if (it.exists()) readOrQuarantine(it) else null }
            if (primary != null) {
                cachedData = primary
                primary
            } else {
                // Primary is missing or corrupt: recover from the backup if it is readable.
                // This also covers a primary that was quarantined on a previous launch.
                val backup = getBackupFile()
                val recovered = if (backup.exists()) readOrQuarantine(backup) else null
                val data = recovered ?: AppData()
                // Persist straight away so the recovered data survives a process death
                // before the next save. No primary exists at this point, so
                // saveDataInternal leaves the existing backup untouched.
                saveDataInternal(data)
                cachedData = data
                data
            }
        }
    }

    /** Parses [file], or quarantines it and returns null if it can't be read. */
    private fun readOrQuarantine(file: File): AppData? = try {
        gson.fromJson(file.readText(), AppData::class.java).sanitizeTransactions()
    } catch (e: Exception) {
        Log.e(TAG, "Data file ${file.name} unreadable, quarantining it", e)
        quarantine(file)
        null
    }

    /**
     * Renames an unreadable data file to a timestamped `.corrupt` copy so it can be
     * recovered manually, keeping at most [MAX_CORRUPT_COPIES] of them.
     */
    private fun quarantine(source: File) {
        try {
            if (!source.exists()) return
            val target = File(context.filesDir, "${source.name}.corrupt-${System.currentTimeMillis()}")
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
            context.filesDir.listFiles { f -> f.name.startsWith("${source.name}.corrupt-") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_CORRUPT_COPIES)
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not quarantine ${source.name}: ${e.message}")
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
        val tempFile = File(context.filesDir, "$fileName.tmp")

        // Atomic write: serialize to temp file first, validate, then swap.
        // The previous live file becomes the backup only after the swap
        // succeeds, so a crash mid-write leaves the existing backup intact.
        val json = gson.toJson(data)
        tempFile.writeText(json)
        if (tempFile.length() <= 0) {
            tempFile.delete()
            return
        }

        val priorExists = file.exists()
        val stagedBackup = if (priorExists) {
            File(context.filesDir, "$backupFileName.tmp").also {
                file.copyTo(it, overwrite = true)
            }
        } else null

        val swapped = tempFile.renameTo(file) || run {
            // renameTo can fail across filesystems / when the destination exists.
            try {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
                true
            } catch (_: Exception) {
                false
            }
        }

        if (!swapped) {
            stagedBackup?.delete()
            tempFile.delete()
            return
        }

        // Promote the staged copy of the previous file to be the new backup.
        if (stagedBackup != null) {
            if (backupFile.exists()) backupFile.delete()
            if (!stagedBackup.renameTo(backupFile)) {
                stagedBackup.copyTo(backupFile, overwrite = true)
                stagedBackup.delete()
            }
        }

        cachedData = data
    }

    /**
     * Export data as JSON string for sharing.
     */
    suspend fun exportData(): String {
        // loadData() returns the cached copy under the lock, so we never serialize
        // a reference that is being swapped by a concurrent save.
        val data = loadData()
        return withContext(Dispatchers.IO) {
            gson.toJson(data)
        }
    }

    /**
     * Import data from JSON string.
     */
    suspend fun importData(jsonString: String): Result<AppData> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val data = gson.fromJson(jsonString, AppData::class.java).sanitizeTransactions()
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
