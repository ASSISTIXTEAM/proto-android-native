package org.assistix.proto.nativeapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ProtoNetworkMonitor(context: Context) {
    private val appCtx = context.applicationContext
    private val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(checkOnline())
    val isOnline: StateFlow<Boolean> = _online.asStateFlow()

    val onlineFlow: Flow<Boolean> =
        callbackFlow {
            trySend(checkOnline())
            val request =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(checkOnline())
                    }

                    override fun onLost(network: Network) {
                        trySend(checkOnline())
                    }

                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    }
                }
            cm.registerNetworkCallback(request, callback)
            awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
        }.distinctUntilChanged()

    fun checkOnline(): Boolean {
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isOnWifi(): Boolean {
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isVpnActive(): Boolean {
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    /** Keeps [isOnline] in sync with [onlineFlow] (ConnectivityManager callbacks). */
    fun attach(scope: CoroutineScope) {
        scope.launch {
            onlineFlow.collect { online -> _online.value = online }
        }
    }
}
