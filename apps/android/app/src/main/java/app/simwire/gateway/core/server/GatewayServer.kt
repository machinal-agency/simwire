package app.simwire.gateway.core.server

import android.content.Context
import app.simwire.gateway.core.GatewayBus
import app.simwire.gateway.core.JournalKind
import app.simwire.gateway.core.buildDeviceInfo
import app.simwire.gateway.core.pairing.TokenStore
import app.simwire.gateway.core.protocol.ClientFrame
import app.simwire.gateway.core.protocol.DEVICE_PORT
import app.simwire.gateway.core.protocol.DeviceFrame
import app.simwire.gateway.core.protocol.ErrorFrame
import app.simwire.gateway.core.protocol.HelloAckFrame
import app.simwire.gateway.core.protocol.HelloFrame
import app.simwire.gateway.core.protocol.IncomingFrame
import app.simwire.gateway.core.protocol.PingFrame
import app.simwire.gateway.core.protocol.PongFrame
import app.simwire.gateway.core.protocol.SendFrame
import app.simwire.gateway.core.protocol.wireJson
import app.simwire.gateway.core.queue.GatewayDb
import app.simwire.gateway.core.queue.Outbox
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class GatewayServer(
    private val context: Context,
    private val outbox: Outbox,
) {
    private val store = TokenStore(context)
    private val active = AtomicReference<WebSocketSession?>(null)
    private var engine: io.ktor.server.engine.ApplicationEngine? = null

    fun start() {
        engine = embeddedServer(CIO, port = DEVICE_PORT) {
            install(WebSockets)
            routing {
                get("/") { call.respondText("simwire gateway") }
                webSocket("/ws") { session() }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(500, 1_000)
        engine = null
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.session() {
        val first = (incoming.receive() as? Frame.Text)?.readText() ?: return close()
        val hello = runCatching { wireJson.decodeFromString<ClientFrame>(first) }.getOrNull()
        val token = store.token
        if (hello !is HelloFrame || token == null || hello.token != token) {
            sendFrame(ErrorFrame(code = "unauthorized", message = "invalid or missing token"))
            close()
            return
        }

        // Single-client phase 1: a new connection displaces the previous one.
        active.getAndSet(this)?.close()
        GatewayBus.setClient(hello.clientName)
        GatewayBus.log(JournalKind.SYS, "client connected: ${hello.clientName}")

        sendFrame(HelloAckFrame(device = buildDeviceInfo(context)))
        replayBufferedIncoming()

        val pump: Job = launch {
            GatewayBus.outbound.collect { frame ->
                if (active.get() === this@session) sendFrame(frame)
            }
        }

        try {
            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                when (val parsed = runCatching { wireJson.decodeFromString<ClientFrame>(text) }.getOrNull()) {
                    is SendFrame -> outbox.enqueue(parsed.id, parsed.to, parsed.text, parsed.simSlot)
                    is PingFrame -> sendFrame(PongFrame)
                    is HelloFrame -> Unit
                    null -> sendFrame(ErrorFrame(code = "bad_frame", message = "unparseable frame"))
                }
            }
        } finally {
            pump.cancel()
            if (active.compareAndSet(this, null)) {
                GatewayBus.setClient(null)
                GatewayBus.log(JournalKind.SYS, "client disconnected")
            }
        }
    }

    private suspend fun replayBufferedIncoming() {
        val dao = GatewayDb.get(context).dao()
        for (buffered in dao.unforwardedIncoming()) {
            active.get()?.sendFrame(
                IncomingFrame(
                    from = buffered.sender,
                    text = buffered.text,
                    simSlot = buffered.simSlot,
                    receivedAt = Instant.ofEpochMilli(buffered.receivedAt).toString(),
                ),
            )
            dao.markForwarded(buffered.id)
        }
    }

    private suspend fun WebSocketSession.sendFrame(frame: DeviceFrame) {
        send(Frame.Text(wireJson.encodeToString<DeviceFrame>(frame)))
    }
}
