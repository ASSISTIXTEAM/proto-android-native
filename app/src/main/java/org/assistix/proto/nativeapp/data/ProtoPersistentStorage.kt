package org.assistix.proto.nativeapp.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File

/**
 * User data root under public Documents — survives app uninstall.
 * Path: /storage/emulated/0/Documents/PROTO/
 */
object ProtoPersistentStorage {
    private const val TAG = "ProtoStorage"
    private const val ROOT_NAME = "PROTO"
    private const val MIGRATION_MARKER = ".storage_migrated_v2"

    @Volatile
    private var root: File? = null

    fun rootDir(context: Context): File {
        root?.let { return it }
        synchronized(this) {
            root?.let { return it }
            val dir = resolveRoot(context)
            dir.mkdirs()
            root = dir
            return dir
        }
    }

    fun databaseFile(context: Context): File =
        File(File(rootDir(context), "db"), "proto_local.db").apply { parentFile?.mkdirs() }

    fun sttDir(context: Context): File = File(rootDir(context), "stt").apply { mkdirs() }

    fun cacheDir(context: Context): File = File(rootDir(context), "cache").apply { mkdirs() }

    fun avatarsDir(context: Context): File = File(rootDir(context), "avatars").apply { mkdirs() }

    fun backupsDir(context: Context): File = File(rootDir(context), "backups").apply { mkdirs() }

    fun exportsDir(context: Context): File = File(rootDir(context), "exports").apply { mkdirs() }

    fun mediaDir(context: Context): File = File(rootDir(context), "media").apply { mkdirs() }

    fun prefsDir(context: Context): File = File(rootDir(context), "prefs").apply { mkdirs() }

    fun dataStoreFile(context: Context, name: String): File =
        File(prefsDir(context), "$name.preferences_pb")

    fun legacyDataStoreFile(context: Context, name: String): File =
        context.preferencesDataStoreFile(name)

    fun initAndMigrate(context: Context) {
        val app = context.applicationContext
        val marker = File(rootDir(app), MIGRATION_MARKER)
        if (marker.exists()) return
        synchronized(this) {
            if (marker.exists()) return
            runCatching { migrateAll(app) }
                .onFailure { Log.e(TAG, "migration failed", it) }
            runCatching { marker.createNewFile() }
        }
    }

    fun isRootReady(context: Context): Boolean = canWrite(rootDir(context))

    fun needsAllFilesAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return !Environment.isExternalStorageManager()
    }

    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val intent =
                android.content.Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }
    }

    private fun resolveRoot(context: Context): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val preferred = File(docs, ROOT_NAME)
        if (canWrite(preferred)) return preferred
        val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), ROOT_NAME)
        if (fallback != null && canWrite(fallback)) return fallback
        return preferred
    }

    private fun canWrite(dir: File): Boolean {
        return try {
            dir.mkdirs()
            val probe = File(dir, ".write_probe")
            probe.writeText("ok")
            probe.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun migrateAll(context: Context) {
        migrateDatabase(context)
        migrateDir(File(context.filesDir, "stt"), sttDir(context))
        migrateDir(File(context.filesDir, "proto_cache"), cacheDir(context))
        migrateDir(File(context.filesDir, "proto_avatars"), avatarsDir(context))
        migrateDir(File(context.getExternalFilesDir(null), "proto_backups"), backupsDir(context))
        migrateDir(File(context.getExternalFilesDir(null), "exports"), exportsDir(context))
        migrateDataStores(context)
        migrateSharedPrefs(context, "proto_stt")
    }

    private fun migrateDatabase(context: Context) {
        val old = context.getDatabasePath("proto_local.db")
        val newFile = databaseFile(context)
        if (old.exists() && (!newFile.exists() || newFile.length() < old.length())) {
            newFile.parentFile?.mkdirs()
            old.copyTo(newFile, overwrite = true)
        }
    }

    private fun migrateDir(from: File, to: File) {
        if (!from.exists() || !from.isDirectory) return
        to.mkdirs()
        from.listFiles()?.forEach { child ->
            val dest = File(to, child.name)
            if (child.isDirectory) {
                migrateDir(child, dest)
            } else if (!dest.exists() || dest.length() < child.length()) {
                child.copyTo(dest, overwrite = true)
            }
        }
    }

    private fun migrateDataStores(context: Context) {
        val names =
            listOf(
                "proto_app_prefs",
                "proto_session",
                "proto_chat_drafts",
                "proto_chat_local",
                "proto_voice_transcripts",
                "proto_saved_meta",
                "proto_theme",
                "proto_pending_verify",
                "proto_widget_cache",
            )
        for (name in names) {
            val old = legacyDataStoreFile(context, name)
            val newFile = dataStoreFile(context, name)
            if (old.exists() && (!newFile.exists() || newFile.length() < old.length())) {
                newFile.parentFile?.mkdirs()
                old.copyTo(newFile, overwrite = true)
            }
        }
    }

    private fun migrateSharedPrefs(context: Context, name: String) {
        val oldXml = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
        val dest = File(prefsDir(context), "$name.xml")
        if (oldXml.exists() && !dest.exists()) {
            oldXml.copyTo(dest, overwrite = true)
        }
    }
}
