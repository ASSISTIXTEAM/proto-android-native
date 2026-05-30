package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.util.concurrent.ConcurrentHashMap

object ProtoDataStoreFactory {
    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun preferences(context: Context, name: String): DataStore<Preferences> {
        val app = context.applicationContext
        return stores.getOrPut(name) {
            PreferenceDataStoreFactory.create(
                produceFile = { ProtoPersistentStorage.dataStoreFile(app, name) },
            )
        }
    }
}
