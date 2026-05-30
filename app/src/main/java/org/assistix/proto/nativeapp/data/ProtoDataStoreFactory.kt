package org.assistix.proto.nativeapp.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Preferences always live in internal app storage (stable, no EACCES on Android 11+).
 * One-time migration from legacy external / default DataStore paths.
 */
object ProtoDataStoreFactory {
    private const val TAG = "ProtoDataStore"
    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()
    private val migrated = ConcurrentHashMap.newKeySet<String>()

    fun preferences(context: Context, name: String): DataStore<Preferences> {
        val app = context.applicationContext
        return stores.getOrPut(name) {
            migrateLegacyOnce(app, name)
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
                produceFile = { internalFile(app, name) },
            )
        }
    }

    fun invalidateAll() {
        stores.clear()
        migrated.clear()
    }

    private fun internalFile(context: Context, name: String): File =
        File(File(context.filesDir, "proto_datastore"), "$name.preferences_pb").also {
            it.parentFile?.mkdirs()
        }

    private fun migrateLegacyOnce(app: Context, name: String) {
        if (!migrated.add(name)) return
        val dest = internalFile(app, name)
        if (dest.exists() && dest.length() > 0L) return

        val sources =
            listOfNotNull(
                runCatching { ProtoPersistentStorage.dataStoreFile(app, name) }.getOrNull(),
                runCatching { app.preferencesDataStoreFile(name) }.getOrNull(),
            )
        for (src in sources) {
            if (!src.exists() || src.length() <= 0L) continue
            if (src.absolutePath == dest.absolutePath) continue
            val ok =
                runCatching {
                    src.copyTo(dest, overwrite = true)
                    dest.exists() && dest.length() > 0L
                }.getOrDefault(false)
            if (ok) {
                Log.i(TAG, "migrated $name from ${src.absolutePath}")
                return
            }
        }
    }
}
