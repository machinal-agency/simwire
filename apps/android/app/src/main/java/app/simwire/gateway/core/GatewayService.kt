package app.simwire.gateway.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.simwire.gateway.MainActivity
import app.simwire.gateway.R
import app.simwire.gateway.core.pairing.TokenStore
import app.simwire.gateway.core.protocol.DeviceStateFrame
import app.simwire.gateway.core.queue.Outbox
import app.simwire.gateway.core.server.GatewayServer
import app.simwire.gateway.core.sms.ACTION_SMS_DELIVERED
import app.simwire.gateway.core.sms.ACTION_SMS_SENT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.simwire.gateway.core.server.Mdns

class GatewayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var outbox: Outbox
    private lateinit var server: GatewayServer
    private lateinit var mdns: Mdns

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        outbox = Outbox(this, scope)
        server = GatewayServer(this, outbox)
        mdns = Mdns(this)

        val filter = IntentFilter().apply {
            addAction(ACTION_SMS_SENT)
            addAction(ACTION_SMS_DELIVERED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(outbox.statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(outbox.statusReceiver, filter)
        }

        outbox.start()
        server.start()
        mdns.register()
        GatewayBus.setRunning(true, lanAddress = lanIpv4(this))
        GatewayBus.log(JournalKind.SYS, "gateway started on :4650")

        scope.launch { heartbeat() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(outbox.statusReceiver) }
        mdns.unregister()
        server.stop()
        scope.cancel()
        GatewayBus.setRunning(false)
        GatewayBus.log(JournalKind.SYS, "gateway stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun heartbeat() {
        val battery = getSystemService(BatteryManager::class.java)
        while (true) {
            val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val charging = battery?.isCharging == true
            GatewayBus.outbound.tryEmit(
                DeviceStateFrame(battery = level, charging = charging, network = networkKind(this)),
            )
            delay(30_000)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Gateway", NotificationManager.IMPORTANCE_LOW),
        )
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("simwire gateway")
            .setContentText("Listening for your code on port 4650")
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "simwire_gateway"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            if (!TokenStore(context).isPaired) return
            context.startForegroundService(Intent(context, GatewayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GatewayService::class.java))
        }
    }
}
