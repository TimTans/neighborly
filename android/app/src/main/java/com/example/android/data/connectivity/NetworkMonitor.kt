package com.example.android.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes device connectivity. Mirrors iOS `NetworkMonitor` (NWPathMonitor wrapper).
 */
interface NetworkMonitor {
    /**
     * Emits `true` when the device has an internet-capable network, `false` otherwise.
     * Always emits the current state on collection start.
     */
    val isOnline: Flow<Boolean>
}

/**
 * `NetworkMonitor` implementation backed by `ConnectivityManager.NetworkCallback`.
 * Tracks the set of currently-available networks so brief switches between Wi-Fi
 * and cellular do not flicker `false` between `onLost`/`onAvailable`.
 */
class ConnectivityManagerNetworkMonitor(
    private val context: Context
) : NetworkMonitor {
    override val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        if (connectivityManager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            private val activeNetworks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                activeNetworks += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                activeNetworks -= network
                trySend(activeNetworks.isNotEmpty())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (online) activeNetworks += network else activeNetworks -= network
                trySend(activeNetworks.isNotEmpty())
            }
        }

        // Seed with current state so the first emission reflects reality, not the
        // first callback that happens to fire.
        val current = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(current)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
