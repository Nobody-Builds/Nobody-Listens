package com.nobodybuilds.listens.data

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The network streams use on the watch. While the phone is connected, Wear OS turns the watch's Wi-Fi off and
 * routes everything over the phone's Bluetooth proxy, which is too slow and patchy for streaming. Apps have to
 * ask for Wi-Fi explicitly, so it is requested while a stream plays and released when it stops (Wi-Fi costs
 * battery). Without Wi-Fi, streams use lower quality (see [isFast]).
 */
@Singleton
class WearStreamingNetwork @Inject constructor(application: Application) {

    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    private val wifi = MutableStateFlow<Network?>(null)

    /** On Wi-Fi or LTE: fast enough for the top quality. False on the phone's Bluetooth proxy. */
    val isFast: Boolean
        get() {
            if (wifi.value != null) return true
            val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }

    @Synchronized
    fun acquire() {
        val manager = connectivity ?: return
        if (callback != null) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                manager.bindProcessToNetwork(network)
                wifi.value = network
            }

            override fun onLost(network: Network) {
                if (wifi.value != network) return
                wifi.value = null
                manager.bindProcessToNetwork(null)
            }
        }
        try {
            manager.requestNetwork(request, networkCallback)
            callback = networkCallback
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not request Wi-Fi for streaming")
        }
    }

    /**
     * Asks for Wi-Fi and waits briefly for it, so a stream's connection opens on Wi-Fi rather than
     * the Bluetooth proxy (a stream keeps the network it started on).
     */
    suspend fun acquireAndWait(timeoutMs: Long = WIFI_WAIT_MS) {
        acquire()
        withTimeoutOrNull(timeoutMs) { wifi.first { it != null } }
    }

    @Synchronized
    fun release() {
        val manager = connectivity ?: return
        val networkCallback = callback ?: return
        callback = null
        wifi.value = null
        manager.bindProcessToNetwork(null)
        runCatching { manager.unregisterNetworkCallback(networkCallback) }
    }

    private companion object {
        const val WIFI_WAIT_MS = 4_000L
    }
}
