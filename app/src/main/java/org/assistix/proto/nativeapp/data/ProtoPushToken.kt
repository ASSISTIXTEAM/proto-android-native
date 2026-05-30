package org.assistix.proto.nativeapp.data

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object ProtoPushToken {
    private const val TAG = "ProtoPushToken"

    suspend fun fetch(): String? =
        runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.onFailure { Log.w(TAG, "FCM token", it) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
}
