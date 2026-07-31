package app.simwire.gateway.core.queue

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

object OutboxStatus {
    const val QUEUED = "queued"
    const val SENDING = "sending"
    const val SENT = "sent"
    const val DELIVERED = "delivered"
    const val FAILED = "failed"
}

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    /** Recipients joined with '|' — E.164 numbers never contain it. */
    val recipients: String,
    val text: String,
    val simSlot: Int?,
    val status: String,
    val error: String?,
    val attempts: Int,
    val createdAt: Long,
)

@Entity(tableName = "incoming")
data class IncomingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val text: String,
    val simSlot: Int,
    val receivedAt: Long,
    /** False until replayed to a connected client. */
    val forwarded: Boolean,
)

@Dao
interface GatewayDao {
    @Insert
    suspend fun insertOutbox(message: OutboxEntity)

    @Query("UPDATE outbox SET status = :status, error = :error WHERE id = :id")
    suspend fun updateOutboxStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: String)

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun outboxById(id: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE status IN ('queued', 'sending') ORDER BY createdAt ASC")
    suspend fun pendingOutbox(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE status = 'queued' ORDER BY createdAt ASC LIMIT 1")
    fun nextQueued(): Flow<OutboxEntity?>

    @Insert
    suspend fun insertIncoming(message: IncomingEntity): Long

    @Query("SELECT * FROM incoming WHERE forwarded = 0 ORDER BY receivedAt ASC")
    suspend fun unforwardedIncoming(): List<IncomingEntity>

    @Query("UPDATE incoming SET forwarded = 1 WHERE id = :id")
    suspend fun markForwarded(id: Long)
}

@Database(entities = [OutboxEntity::class, IncomingEntity::class], version = 1, exportSchema = false)
abstract class GatewayDb : RoomDatabase() {
    abstract fun dao(): GatewayDao

    companion object {
        @Volatile private var instance: GatewayDb? = null

        fun get(context: Context): GatewayDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                GatewayDb::class.java,
                "simwire.db",
            ).build().also { instance = it }
        }
    }
}
