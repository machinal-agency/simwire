package app.simwire.gateway.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1
const val MDNS_SERVICE_TYPE = "_simwire._tcp."
const val DEVICE_PORT = 4650

val wireJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class SimSlot(
    val index: Int,
    val carrier: String?,
    val phoneNumber: String?,
)

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val model: String,
    val androidVersion: String,
    val simSlots: List<SimSlot>,
)

// ---------------------------------------------------------------------------
// Pairing (phone -> CLI endpoint, plain HTTP)
// ---------------------------------------------------------------------------

@Serializable
data class PairingQrPayload(
    val v: Int,
    val host: String,
    val port: Int,
    val code: String,
)

@Serializable
data class PairingEndpoint(val host: String, val port: Int)

@Serializable
data class PairingRequest(
    val v: Int = PROTOCOL_VERSION,
    val code: String,
    val device: DeviceInfo,
    val endpoint: PairingEndpoint,
    val token: String,
)

@Serializable
data class PairingResponse(
    val ok: Boolean,
    val clientName: String = "",
)

// ---------------------------------------------------------------------------
// WebSocket frames — client (SDK/CLI) -> device
// ---------------------------------------------------------------------------

@Serializable
sealed interface ClientFrame

@Serializable
@SerialName("hello")
data class HelloFrame(
    val v: Int,
    val token: String,
    val clientName: String,
) : ClientFrame

@Serializable
@SerialName("send")
data class SendFrame(
    val id: String,
    val to: List<String>,
    val text: String,
    val simSlot: Int? = null,
) : ClientFrame

@Serializable
@SerialName("ping")
data object PingFrame : ClientFrame

// ---------------------------------------------------------------------------
// WebSocket frames — device -> client (SDK/CLI)
// ---------------------------------------------------------------------------

@Serializable
sealed interface DeviceFrame

@Serializable
@SerialName("hello.ack")
data class HelloAckFrame(val device: DeviceInfo) : DeviceFrame

@Serializable
@SerialName("message.status")
data class StatusFrame(
    val id: String,
    val status: String,
    val error: String? = null,
    val at: String,
) : DeviceFrame

@Serializable
@SerialName("message.incoming")
data class IncomingFrame(
    val from: String,
    val text: String,
    val simSlot: Int,
    val receivedAt: String,
) : DeviceFrame

@Serializable
@SerialName("device.state")
data class DeviceStateFrame(
    val battery: Int,
    val charging: Boolean,
    val network: String,
) : DeviceFrame

@Serializable
@SerialName("pong")
data object PongFrame : DeviceFrame

@Serializable
@SerialName("error")
data class ErrorFrame(
    val code: String,
    val message: String,
) : DeviceFrame
