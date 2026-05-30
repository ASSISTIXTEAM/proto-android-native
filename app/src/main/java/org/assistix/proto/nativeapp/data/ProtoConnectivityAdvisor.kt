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

enum class ConnectivityWarningKind {
    Vpn,
    Foreign,
    Slow,
    VpnForeign,
    ForeignSlow,
}

/**
 * Non-blocking connectivity hints: VPN, non-RU egress, slow API (RKN / routing).
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

    private val _warningKind = MutableStateFlow<ConnectivityWarningKind?>(null)
    val warningKind: StateFlow<ConnectivityWarningKind?> = _warningKind.asStateFlow()

    private val dismissTtlMs = 12 * 60 * 60 * 1000L
    private val slowLatencyMs = 2800L

    suspend fun refresh() =
        withContext(Dispatchers.IO) {
            val vpn = network.isVpnActive()
            var country = store.data.map { it[keyCountry].orEmpty().uppercase() }.first()
            var latencyMs = -1L
            if (network.checkOnline()) {
                latencyMs = api.measureApiLatencyMs()
                api.fetchGeoCountry()?.let { c ->
                    country = c.uppercase()
                    store.edit { it[keyCountry] = country }
                }
            }
            val foreign = country.isNotBlank() && country != "RU"
            val slow = latencyMs < 0 || latencyMs > slowLatencyMs
            val dismissedAt = store.data.map { it[keyDismissedAt] ?: 0L }.first()
            val dismissedRecently = System.currentTimeMillis() - dismissedAt < dismissTtlMs

            _warningKind.value =
                when {
                    dismissedRecently -> null
                    vpn && foreign -> ConnectivityWarningKind.VpnForeign
                    vpn -> ConnectivityWarningKind.Vpn
                    foreign && slow -> ConnectivityWarningKind.ForeignSlow
                    foreign -> ConnectivityWarningKind.Foreign
                    slow && network.checkOnline() -> ConnectivityWarningKind.Slow
                    else -> null
                }
        }

    suspend fun dismissWarning() {
        store.edit { it[keyDismissedAt] = System.currentTimeMillis() }
        _warningKind.value = null
    }

    fun isVpnActive(): Boolean = network.isVpnActive()
}
