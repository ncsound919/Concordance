package com.example.concordance.autonomy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors network connectivity and flips AutonomyModeManager to OFFLINE
 * when connectivity is lost, restoring the previous mode on reconnect.
 */
object NetworkMonitor {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var cm: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var wasOffline = false

    fun init(context: Context) {
        if (cm != null) return
        cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _online.value = true
                if (wasOffline) {
                    wasOffline = false
                    AutonomyModeManager.setOffline(false)
                }
            }
            override fun onLost(network: Network) {
                _online.value = false
                wasOffline = true
                AutonomyModeManager.setOffline(true)
            }
        }
        cm?.registerNetworkCallback(request, callback!!)
        // initial state
        val active = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(active)
        _online.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!_online.value) {
            wasOffline = true
            AutonomyModeManager.setOffline(true)
        }
    }

    fun isOnline(): Boolean = _online.value
}
