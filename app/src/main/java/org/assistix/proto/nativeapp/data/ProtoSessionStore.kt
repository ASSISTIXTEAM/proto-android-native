package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ProtoSessionStore(private val context: Context) {
    private val dataStore get() = ProtoDataStoreFactory.preferences(context, "proto_session")
    private val keyToken = stringPreferencesKey("token")
    private val keyNick = stringPreferencesKey("nick")
    private val keyUserId = stringPreferencesKey("user_id")

    val tokenFlow: Flow<String?> = dataStore.data.map { it[keyToken] }

    val userIdFlow: Flow<Int> =
        dataStore.data.map { it[keyUserId]?.toIntOrNull() ?: 0 }

    suspend fun save(token: String, userId: Int, nick: String) {
        dataStore.edit {
            it[keyToken] = token
            it[keyNick] = nick
            it[keyUserId] = userId.toString()
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun token(): String? = dataStore.data.map { it[keyToken] }.first()

    suspend fun userId(): Int =
        dataStore.data.map { it[keyUserId]?.toIntOrNull() ?: 0 }.first()

    suspend fun nick(): String? = dataStore.data.map { it[keyNick] }.first()
}
