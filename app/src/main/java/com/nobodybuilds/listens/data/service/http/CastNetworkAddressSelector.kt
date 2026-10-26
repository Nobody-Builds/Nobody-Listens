package com.nobodybuilds.listens.data.service.http

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import timber.log.Timber
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.math.max

// Picks the LAN address and port the Cast HTTP server listens on.

private data class LocalAddressCandidate(
    val hostAddress: String,
    val address: Inet4Address,
    val prefixLength: Int,
    val isActiveNetwork: Boolean,
    val isValidated: Boolean,
    val hasInternet: Boolean
)

internal data class AddressSelection(
    val hostAddress: String,
    val prefixLength: Int,
    val matchedCastSubnet: Boolean
)

internal fun resolveServerPort(preferredPort: Int): Int {
    val startPort = preferredPort.coerceIn(1, 65535)
    val candidatePorts = sequence {
        yield(startPort)
        val upperBound = (startPort + 20).coerceAtMost(65535)
        for (port in (startPort + 1)..upperBound) {
            yield(port)
        }
    }

    candidatePorts.firstOrNull { isPortAvailable(it) }?.let { return it }

    return runCatching {
        ServerSocket(0).use { socket ->
            max(socket.localPort, 1)
        }
    }.getOrDefault(startPort)
}

private fun isPortAvailable(port: Int): Boolean {
    if (port !in 1..65535) return false
    return runCatching {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port))
            true
        }
    }.getOrDefault(false)
}

internal fun Throwable.isAddressAlreadyInUse(): Boolean {
    return generateSequence(this) { it.cause }.any { cause ->
        cause is BindException ||
            cause.message?.contains("Address already in use", ignoreCase = true) == true ||
            cause.message?.contains("EADDRINUSE", ignoreCase = true) == true
    }
}

internal fun selectIpAddress(context: Context, castDeviceIpHint: String?): AddressSelection? {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork

    val candidates = mutableListOf<LocalAddressCandidate>()
    for (network in connectivityManager.allNetworks) {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
        if (!caps.isLocalLanTransport()) continue
        val linkProps = connectivityManager.getLinkProperties(network) ?: continue
        val isActiveNetwork = network == activeNetwork

        for (linkAddress in linkProps.linkAddresses) {
            val ipv4 = linkAddress.address as? Inet4Address ?: continue
            if (ipv4.isLoopbackAddress || ipv4.isLinkLocalAddress) continue
            val hostAddress = ipv4.hostAddress ?: continue
            candidates += LocalAddressCandidate(
                hostAddress = hostAddress,
                address = ipv4,
                prefixLength = linkAddress.prefixLength.coerceIn(0, 32),
                isActiveNetwork = isActiveNetwork,
                isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            )
        }
    }

    if (candidates.isEmpty()) return null

    val castAddress = parseIpv4Address(castDeviceIpHint)
    if (castAddress != null) {
        val subnetMatches = candidates
            .filter { candidate ->
                isSameSubnet(candidate.address, castAddress, candidate.prefixLength)
            }
            .sortedByBestCandidate()
        if (subnetMatches.isNotEmpty()) {
            val selected = subnetMatches.first()
            return AddressSelection(
                hostAddress = selected.hostAddress,
                prefixLength = selected.prefixLength,
                matchedCastSubnet = true
            )
        }
        Timber.tag(CAST_HTTP_LOG_TAG).w(
            "No LAN interface matched Cast subnet for castDeviceIp=%s; falling back to best LAN interface.",
            castDeviceIpHint
        )
    }

    val selected = candidates
        .sortedByBestCandidate()
        .firstOrNull()
        ?: return null

    return AddressSelection(
        hostAddress = selected.hostAddress,
        prefixLength = selected.prefixLength,
        matchedCastSubnet = false
    )
}

private fun List<LocalAddressCandidate>.sortedByBestCandidate(): List<LocalAddressCandidate> {
    return sortedWith(
        compareByDescending<LocalAddressCandidate> { it.isActiveNetwork }
            .thenByDescending { it.isValidated }
            .thenByDescending { it.hasInternet }
            .thenByDescending { it.prefixLength }
            .thenByDescending { it.hostAddress }
    )
}

private fun parseIpv4Address(rawAddress: String?): Inet4Address? {
    val normalized = rawAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parsed = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return null
    return parsed as? Inet4Address
}

private fun isSameSubnet(localAddress: Inet4Address, remoteAddress: Inet4Address, prefixLength: Int): Boolean {
    val clampedPrefix = prefixLength.coerceIn(0, 32)
    if (clampedPrefix == 0) return true
    val localInt = localAddress.toIntAddress()
    val remoteInt = remoteAddress.toIntAddress()
    val mask = if (clampedPrefix == 32) {
        -1
    } else {
        (-1 shl (32 - clampedPrefix))
    }
    return (localInt and mask) == (remoteInt and mask)
}

private fun Inet4Address.toIntAddress(): Int {
    val bytes = address
    return ((bytes[0].toInt() and 0xFF) shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
}

private fun NetworkCapabilities.isLocalLanTransport(): Boolean {
    return hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
