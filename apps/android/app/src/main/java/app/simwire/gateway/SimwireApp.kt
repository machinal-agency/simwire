package app.simwire.gateway

import android.app.Application
import app.simwire.gateway.core.GatewayService

class SimwireApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Resume the gateway if we were paired before the process died.
        GatewayService.start(this)
    }
}
