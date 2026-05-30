package org.assistix.proto.nativeapp.data



import android.content.Context

import android.os.Build

import android.os.Environment

import android.provider.Settings

import android.util.Log

import androidx.datastore.preferences.preferencesDataStoreFile

import java.io.File



/**

 * User data root — prefers public Documents when all-files access is granted (survives uninstall).

 * Otherwise app-scoped external storage (no EACCES on Android 11+).

 * Path: Documents/PROTO/ or Android/data/.../files/Documents/PROTO/

 */

object ProtoPersistentStorage {

    private const val TAG = "ProtoStorage"

    private const val ROOT_NAME = "PROTO"

    private const val MIGRATION_MARKER = ".storage_migrated_v2"



    @Volatile

    private var root: File? = null



    fun rootDir(context: Context): File {

        root?.let { cached ->

            if (canAccess(cached)) return cached

            synchronized(this) {

                if (root == cached) root = null

            }

        }

        synchronized(this) {

            root?.let { if (canAccess(it)) return it }

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

    /** Encrypted Cells shards — isolated vault, never cleared with media cache. */
    fun cellsDir(context: Context): File = File(rootDir(context), "cells").apply { mkdirs() }

    /** Top-level dirs that hold encrypted vault / user data — protected from cache wipe. */
    val VAULT_DIR_NAMES: List<String> =
        listOf("cells", "db", "prefs", "offline", "media", "stt", "backups", "avatars")

    fun isVaultPath(context: Context, file: File): Boolean {
        val root = runCatching { rootDir(context).canonicalFile }.getOrNull() ?: return false
        val target = runCatching { file.canonicalFile }.getOrNull() ?: return false
        if (!target.path.startsWith(root.path)) return false
        val relative = target.relativeTo(root).path.replace('\\', '/').substringBefore('/', missingDelimiterValue = "").ifBlank { return false }
        return relative in VAULT_DIR_NAMES
    }



    fun prefsDir(context: Context): File = File(rootDir(context), "prefs").apply { mkdirs() }



    fun dataStoreFile(context: Context, name: String): File =

        File(prefsDir(context), "$name.preferences_pb")



    fun legacyDataStoreFile(context: Context, name: String): File =

        context.preferencesDataStoreFile(name)



    fun initAndMigrate(context: Context) {

        val app = context.applicationContext

        recoverPublicVaultIfNeeded(app)

        val marker = File(rootDir(app), MIGRATION_MARKER)

        if (marker.exists()) return

        synchronized(this) {

            if (marker.exists()) return

            runCatching { migrateAll(app) }

                .onFailure { Log.e(TAG, "migration failed", it) }

            runCatching { marker.createNewFile() }

        }

    }



    fun isRootReady(context: Context): Boolean = canAccess(rootDir(context))



    fun needsAllFilesAccess(context: Context): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        return publicDocumentsRoot().let { canAccess(it) && !Environment.isExternalStorageManager() }

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



    private fun publicDocumentsRoot(): File =

        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ROOT_NAME)



    private fun appScopedRoot(context: Context): File =

        File(

            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,

            ROOT_NAME,

        )



    private fun resolveRoot(context: Context): File {

        val publicRoot = publicDocumentsRoot()

        val scopedRoot = appScopedRoot(context)



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            if (Environment.isExternalStorageManager() && canAccess(publicRoot)) {

                return publicRoot

            }

            if (canAccess(publicRoot) && !publicRoot.exists()) {

                // Empty tree — avoid public path without MANAGE permission (EACCES on read later).

                scopedRoot.mkdirs()

                return scopedRoot

            }

            if (canAccess(publicRoot) && publicRoot.exists()) {

                // Legacy data in public Documents but no all-files grant — pull into scoped root.

                runCatching { migrateTreeIfNeeded(publicRoot, scopedRoot) }

            }

            scopedRoot.mkdirs()

            return scopedRoot

        }



        if (canAccess(publicRoot)) return publicRoot

        if (canAccess(scopedRoot)) return scopedRoot

        scopedRoot.mkdirs()

        return scopedRoot

    }



    private fun migrateTreeIfNeeded(from: File, to: File) {

        if (!from.exists() || !from.isDirectory) return

        to.mkdirs()

        from.listFiles()?.forEach { child ->

            val dest = File(to, child.name)

            if (child.isDirectory) {

                migrateTreeIfNeeded(child, dest)

            } else if (!dest.exists() || dest.length() < child.length()) {

                runCatching { child.copyTo(dest, overwrite = true) }

            }

        }

    }



    private fun canAccess(dir: File): Boolean {

        return try {

            dir.mkdirs()

            val probe = File(dir, ".write_probe")

            probe.writeText("ok")

            val readBack = probe.readText()

            probe.delete()

            readBack == "ok"

        } catch (e: Exception) {

            Log.w(TAG, "cannot access ${dir.absolutePath}", e)

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

        val publicRoot = publicDocumentsRoot()

        if (publicRoot.exists() && publicRoot != rootDir(context)) {

            migrateTreeIfNeeded(publicRoot, rootDir(context))

        }

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

    /** After system «clear data», pull vault from public Documents/PROTO if scoped tree is empty. */
    private fun recoverPublicVaultIfNeeded(context: Context) {
        val public = publicDocumentsRoot()
        if (!public.exists() || !public.isDirectory) return
        val targetRoot = resolveRoot(context)
        if (public.absolutePath == targetRoot.absolutePath) return
        for (name in VAULT_DIR_NAMES) {
            val from = File(public, name)
            if (!from.exists()) continue
            val to = File(targetRoot, name)
            val empty =
                !to.exists() ||
                    (to.isDirectory && to.list()?.isEmpty() != false) ||
                    (to.isFile && to.length() <= 0L)
            if (empty) {
                runCatching { migrateTreeIfNeeded(from, to) }
            }
        }
    }

}


