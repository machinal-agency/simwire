package app.simwire.gateway.core.pairing

import android.content.Context
import app.simwire.gateway.core.buildDeviceInfo
import app.simwire.gateway.core.lanIpv4
import app.simwire.gateway.core.protocol.DEVICE_PORT
import app.simwire.gateway.core.protocol.PROTOCOL_VERSION
import app.simwire.gateway.core.protocol.PairingEndpoint
import app.simwire.gateway.core.protocol.PairingQrPayload
import app.simwire.gateway.core.protocol.PairingRequest
import app.simwire.gateway.core.protocol.PairingResponse
import app.simwire.gateway.core.protocol.wireJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed interface PairingResult {
    data class Success(val clientName: String) : PairingResult
    data class Failure(val reason: String) : PairingResult
}

class PairingClient(private val context: Context) {

    fun parseQr(raw: String): PairingQrPayload? =
        runCatching { wireJson.decodeFromString<PairingQrPayload>(raw) }.getOrNull()
            ?.takeIf { it.v == PROTOCOL_VERSION }

    suspend fun pair(payload: PairingQrPayload): PairingResult = withContext(Dispatchers.IO) {
        val host = lanIpv4(context)
            ?: return@withContext PairingResult.Failure("No Wi-Fi address on this phone")
        val token = "${UUID.randomUUID()}${UUID.randomUUID()}".replace("-", "")
        val request = PairingRequest(
            code = payload.code,
            device = buildDeviceInfo(context),
            endpoint = PairingEndpoint(host = host, port = DEVICE_PORT),
            token = token,
        )
        try {
            val url = URL("http://${payload.host}:${payload.port}/pair")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use {
                it.write(wireJson.encodeToString(PairingRequest.serializer(), request).toByteArray())
            }
            if (connection.responseCode != 200) {
                return@withContext PairingResult.Failure("Computer answered HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().readText()
            val response = wireJson.decodeFromString<PairingResponse>(body)
            if (!response.ok) return@withContext PairingResult.Failure("Pairing rejected")

            TokenStore(context).apply {
                this.token = token
                this.clientName = response.clientName
            }
            PairingResult.Success(response.clientName)
        } catch (e: Exception) {
            PairingResult.Failure(e.message ?: "Could not reach the computer")
        }
    }
}
