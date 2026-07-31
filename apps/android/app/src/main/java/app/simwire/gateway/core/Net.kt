package app.simwire.gateway.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

fun lanIpv4(@Suppress("UNUSED_PARAMETER") context: Context): String? =
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress

fun networkKind(context: Context): String {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "offline"
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        else -> "offline"
    }
}
