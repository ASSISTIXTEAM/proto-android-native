package org.assistix.proto.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads profiles from network when possible; always falls back to [ProtoOfflineVault]. */
class ProtoProfileCache(
    private val vault: ProtoOfflineVault,
    private val api: ProtoApi,
) {
    suspend fun loadMe(token: String): ProtoApi.MeLoad? =
        withContext(Dispatchers.IO) {
            val cached = vault.readMe()
            val remote =
                runCatching { api.me(token) }.getOrNull()?.also { vault.writeMe(it) }
            remote ?: cached
        }

    suspend fun loadUser(token: String, userId: Int): UserProfile? =
        withContext(Dispatchers.IO) {
            if (userId <= 0) return@withContext null
            val cached = vault.readUser(userId)
            val remote =
                runCatching { api.userById(token, userId) }.getOrNull()?.also { vault.writeUser(it) }
            remote ?: cached
        }

    suspend fun persistMe(profile: MeProfile, restriction: AccountRestriction? = null) {
        withContext(Dispatchers.IO) {
            vault.writeMe(ProtoApi.MeLoad(profile, restriction))
        }
    }

    suspend fun persistUser(profile: UserProfile) {
        withContext(Dispatchers.IO) {
            vault.writeUser(profile)
        }
    }
}
