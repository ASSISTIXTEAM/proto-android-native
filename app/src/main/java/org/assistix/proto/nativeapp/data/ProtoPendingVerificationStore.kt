package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Регистрация / вход без подтверждённой почты — пока не введён код, сессии нет. */
class ProtoPendingVerificationStore(private val context: Context) {
    private val pendingVerifyStore get() = ProtoDataStoreFactory.preferences(context, "proto_pending_verify")
    private val keyUserId = intPreferencesKey("user_id")
    private val keyEmailHint = stringPreferencesKey("email_hint")
    private val keyMessage = stringPreferencesKey("message")

    data class Pending(val userId: Int, val emailHint: String, val message: String)

    suspend fun save(userId: Int, emailHint: String, message: String = "") {
        if (userId < 1) return
        pendingVerifyStore.edit {
            it[keyUserId] = userId
            it[keyEmailHint] = emailHint
            it[keyMessage] = message
        }
    }

    suspend fun load(): Pending? {
        val prefs = pendingVerifyStore.data.first()
        val uid = prefs[keyUserId] ?: 0
        if (uid < 1) return null
        return Pending(
            userId = uid,
            emailHint = prefs[keyEmailHint].orEmpty(),
            message = prefs[keyMessage].orEmpty(),
        )
    }

    suspend fun clear() {
        pendingVerifyStore.edit { it.clear() }
    }

    suspend fun userIdOrZero(): Int =
        pendingVerifyStore.data.map { it[keyUserId] ?: 0 }.first()
}
