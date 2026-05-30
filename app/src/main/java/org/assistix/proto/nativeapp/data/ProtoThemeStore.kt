package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ProtoThemeMode { LIGHT, DARK, SYSTEM }

class ProtoThemeStore(private val context: Context) {
    private val themeStore get() = ProtoDataStoreFactory.preferences(context, "proto_theme")
    private val key = stringPreferencesKey("mode")

    val mode: Flow<ProtoThemeMode> =
        themeStore.data.map { prefs ->
            when (prefs[key]) {
                "light" -> ProtoThemeMode.LIGHT
                "system" -> ProtoThemeMode.SYSTEM
                else -> ProtoThemeMode.DARK
            }
        }

    suspend fun setMode(mode: ProtoThemeMode) {
        themeStore.edit {
            it[key] =
                when (mode) {
                    ProtoThemeMode.LIGHT -> "light"
                    ProtoThemeMode.DARK -> "dark"
                    ProtoThemeMode.SYSTEM -> "system"
                }
        }
    }
}
