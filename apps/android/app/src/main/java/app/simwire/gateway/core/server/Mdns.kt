package app.simwire.gateway.core.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import app.simwire.gateway.core.protocol.DEVICE_PORT
import app.simwire.gateway.core.protocol.MDNS_SERVICE_TYPE

/** Advertises the gateway on the LAN so `connect()` finds it without an IP. */
class Mdns(private val context: Context) {
    private var nsd: NsdManager? = null
    private var listener: NsdManager.RegistrationListener? = null

    fun register() {
        val manager = context.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "simwire-${Build.MODEL}".take(48)
            serviceType = MDNS_SERVICE_TYPE
            port = DEVICE_PORT
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        nsd = manager
        listener = registration
    }

    fun unregister() {
        val manager = nsd ?: return
        listener?.let { runCatching { manager.unregisterService(it) } }
        nsd = null
        listener = null
    }
}
