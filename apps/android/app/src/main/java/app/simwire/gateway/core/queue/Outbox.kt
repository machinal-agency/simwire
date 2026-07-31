package app.simwire.gateway.core.queue

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import app.simwire.gateway.core.GatewayBus
import app.simwire.gateway.core.JournalKind
import app.simwire.gateway.core.protocol.StatusFrame
import app.simwire.gateway.core.sms.ACTION_SMS_DELIVERED
import app.simwire.gateway.core.sms.ACTION_SMS_SENT
import app.simwire.gateway.core.sms.EXTRA_MESSAGE_ID
import app.simwire.gateway.core.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Instant

private const val MAX_ATTEMPTS = 3

class Outbox(private val context: Context, private val scope: CoroutineScope) {
    private val dao = GatewayDb.get(context).dao()
    private val sender = SmsSender(context)

    fun start() {
        scope.launch(Dispatchers.IO) {
            // Anything stuck in "sending" from a previous process death goes
            // back to the queue: the durable-queue contract of the protocol.
            dao.pendingOutbox().forEach { message ->
                if (message.status == OutboxStatus.SENDING) {
                    dao.updateOutboxStatus(message.id, OutboxStatus.QUEUED)
                }
            }
            dao.nextQueued()
                .filterNotNull()
                // Compare attempts too, so a retried message re-triggers collection.
                .distinctUntilChanged { old, new -> old.id == new.id && old.attempts == new.attempts }
                .collect { message -> attempt(message) }
        }
    }

    suspend fun enqueue(id: String, recipients: List<String>, text: String, simSlot: Int?) {
        dao.insertOutbox(
            OutboxEntity(
                id = id,
                recipients = recipients.joinToString("|"),
                text = text,
                simSlot = simSlot,
                status = OutboxStatus.QUEUED,
                error = null,
                attempts = 0,
                createdAt = System.currentTimeMillis(),
            ),
        )
        emitStatus(id, OutboxStatus.QUEUED)
        GatewayBus.log(JournalKind.OUT, "→ ${recipients.joinToString(", ")}  $text")
    }

    private suspend fun attempt(message: OutboxEntity) {
        dao.updateOutboxStatus(message.id, OutboxStatus.SENDING)
        dao.bumpAttempts(message.id)
        try {
            sender.send(
                messageId = message.id,
                recipients = message.recipients.split("|"),
                text = message.text,
                simSlot = message.simSlot,
            )
            // Now waiting for the SENT broadcast; nothing else to do here.
        } catch (e: Exception) {
            if (message.attempts + 1 >= MAX_ATTEMPTS) {
                fail(message.id, e.message ?: "send failed")
            } else {
                delay(2_000L * (message.attempts + 1))
                dao.updateOutboxStatus(message.id, OutboxStatus.QUEUED)
            }
        }
    }

    /** Dynamic receiver for the radio's sent/delivered results. */
    val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val id = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
            val ok = resultCode == Activity.RESULT_OK
            scope.launch(Dispatchers.IO) {
                when (intent.action) {
                    ACTION_SMS_SENT ->
                        if (ok) {
                            dao.updateOutboxStatus(id, OutboxStatus.SENT)
                            emitStatus(id, OutboxStatus.SENT)
                            GatewayBus.log(JournalKind.SYS, "sent ✓  ${id.take(8)}")
                        } else {
                            fail(id, sendErrorName(resultCode))
                        }
                    ACTION_SMS_DELIVERED -> {
                        dao.updateOutboxStatus(id, OutboxStatus.DELIVERED)
                        emitStatus(id, OutboxStatus.DELIVERED)
                        GatewayBus.log(JournalKind.SYS, "delivered ✓  ${id.take(8)}")
                    }
                }
            }
        }
    }

    private suspend fun fail(id: String, error: String) {
        dao.updateOutboxStatus(id, OutboxStatus.FAILED, error)
        emitStatus(id, OutboxStatus.FAILED, error)
        GatewayBus.log(JournalKind.SYS, "failed ✗  $error")
    }

    private fun emitStatus(id: String, status: String, error: String? = null) {
        GatewayBus.outbound.tryEmit(
            StatusFrame(id = id, status = status, error = error, at = Instant.now().toString()),
        )
    }

    private fun sendErrorName(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no cellular service"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off (airplane mode?)"
        SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "sending limit exceeded"
        else -> "generic radio failure ($code)"
    }
}
