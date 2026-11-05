package com.example.opsc.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tells the app whether the phone currently has a working internet connection.
 * Used for the offline banner and by the offline-sync feature.
 */
class ConnectivityMonitor(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** True if the active network can reach the internet right now. */
    fun isOnlineNow(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Adapted from: Android Developers (2026) Monitor connectivity status and connection metering.
    // Available at: https://developer.android.com/training/monitoring-device-state/connectivity-status-type
    /** Emits the current state straight away, then every change. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnlineNow())
            }

            override fun onLost(network: Network) {
                trySend(isOnlineNow())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(isOnlineNow())
            }
        }
        trySend(isOnlineNow())
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }
        awaitClose {
            try {
                manager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Could not unregister network callback", e)
            }
        }
    }.distinctUntilChanged()

    private companion object {
        const val TAG = "ConnectivityMonitor"
    }
}
