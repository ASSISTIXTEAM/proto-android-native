package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Non-blocking hint when VPN or a non-RU egress may hurt PROTO (RKN / routing).
 * Does not block usage — informational only.
 */
class ProtoConnectivityAdvisor(
    context: Context,
    private val network: ProtoNetworkMonitor,
    private val api: ProtoApi,
) {
    private val appCtx = context.applicationContext
    private val store = ProtoDataStoreFactory.preferences(appCtx, "proto_connectivity")
    private val keyCountry = stringPreferencesKey("geo_country")
    private val keyDismissedAt = longPreferencesKey("warning_dismissed_at")

    private val _showWarning = MutableStateFlow(false)
    val showWarning: StateFlow<Boolean> = _showWarning.asStateFlow()

    private val dismissTtlMs = 12 * 60 * 60 * 1000L

    suspend fun refresh() =
        withContext(Dispatchers.IO) {
            val vpn = network.isVpnActive()
            var country = store.data.map { it[keyCountry].orEmpty().uppercase() }.first()
            if (network.checkOnline()) {
                api.fetchGeoCountry()?.let { c ->
                    country = c.uppercase()
                    store.edit { it[keyCountry] = country }
                }
            }
            val foreign = country.isNotBlank() && country != "RU"
            val dismissedAt = store.data.map { it[keyDismissedAt] ?: 0L }.first()
            val dismissedRecently = System.currentTimeMillis() - dismissedAt < dismissTtlMs
            _showWarning.value = (vpn || foreign) && !dismissedRecently
        }

    suspend fun dismissWarning() {
        store.edit { it[keyDismissedAt] = System.currentTimeMillis() }
        _showWarning.value = false
    }

    fun isVpnActive(): Boolean = network.isVpnActive()
}
