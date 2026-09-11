package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.content.model.RewardDelivery
import kr.kjh9211.arcanastory.persistence.Database
import kr.kjh9211.arcanastory.persistence.DeliveryAttemptRecord
import kr.kjh9211.arcanastory.persistence.DeliveryEvent
import kr.kjh9211.arcanastory.persistence.DeliveryStatus
import kr.kjh9211.arcanastory.persistence.RewardDeliveryRecord
import kr.kjh9211.arcanastory.persistence.RewardDeliveryRepository
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

private const val MAX_DETAIL_LENGTH = 512

internal fun Connection.logDeliveryEvent(
    deliveryId: UUID,
    attemptId: UUID?,
    event: DeliveryEvent,
    detail: String?,
    at: Instant,
) {
    update(
        "INSERT INTO arcanastory_reward_delivery_attempt (delivery_id, attempt_id, event, detail, created_at) VALUES (?, ?, ?, ?, ?)",
        deliveryId,
        attemptId,
        event.name,
        detail?.take(MAX_DETAIL_LENGTH),
        at,
    )
}

class MySqlRewardDeliveryRepository(
    private val database: Database,
) : RewardDeliveryRepository {

    override fun findUnfinished(playerId: UUID): List<RewardDeliveryRecord> = database.withConnection { connection ->
        connection.query(
            "$SELECT_DELIVERY WHERE player_uuid = ? AND status IN ('PENDING', 'DELIVERING', 'FAILED') " +
                "ORDER BY created_at, reward_key, component_index",
            playerId,
        ) { it.toDelivery() }
    }

    override fun find(deliveryId: UUID): RewardDeliveryRecord? = database.withConnection { connection ->
        connection.query("$SELECT_DELIVERY WHERE delivery_id = ?", deliveryId) { it.toDelivery() }.singleOrNull()
    }

    override fun beginAttempt(deliveryId: UUID, attemptId: UUID, at: Instant): Boolean = transition(
        deliveryId = deliveryId,
        attemptId = attemptId,
        event = DeliveryEvent.BEGIN,
        detail = null,
        at = at,
        sql = "UPDATE arcanastory_reward_delivery SET status = 'DELIVERING', attempt_id = ?, attempts = attempts + 1, " +
            "last_error = NULL, updated_at = ? WHERE delivery_id = ? AND status IN ('PENDING', 'FAILED')",
        args = arrayOf(attemptId, at, deliveryId),
    )

    override fun markDelivered(deliveryId: UUID, attemptId: UUID, at: Instant): Boolean = transition(
        deliveryId = deliveryId,
        attemptId = attemptId,
        event = DeliveryEvent.DELIVERED,
        detail = null,
        at = at,
        sql = "UPDATE arcanastory_reward_delivery SET status = 'DELIVERED', delivered_at = ?, updated_at = ? " +
            "WHERE delivery_id = ? AND status = 'DELIVERING' AND attempt_id = ?",
        args = arrayOf(at, at, deliveryId, attemptId),
    )

    override fun revertToPending(deliveryId: UUID, attemptId: UUID, reason: String, at: Instant): Boolean = transition(
        deliveryId = deliveryId,
        attemptId = attemptId,
        event = DeliveryEvent.REVERTED,
        detail = reason,
        at = at,
        sql = "UPDATE arcanastory_reward_delivery SET status = 'PENDING', attempt_id = NULL, last_error = ?, updated_at = ? " +
            "WHERE delivery_id = ? AND status = 'DELIVERING' AND attempt_id = ?",
        args = arrayOf(reason.take(MAX_DETAIL_LENGTH), at, deliveryId, attemptId),
    )

    override fun markFailed(deliveryId: UUID, attemptId: UUID, error: String, at: Instant): Boolean = transition(
        deliveryId = deliveryId,
        attemptId = attemptId,
        event = DeliveryEvent.FAILED,
        detail = error,
        at = at,
        sql = "UPDATE arcanastory_reward_delivery SET status = 'FAILED', attempt_id = NULL, last_error = ?, updated_at = ? " +
            "WHERE delivery_id = ? AND status = 'DELIVERING' AND attempt_id = ?",
        args = arrayOf(error.take(MAX_DETAIL_LENGTH), at, deliveryId, attemptId),
    )

    override fun markManualReview(deliveryId: UUID, reason: String, at: Instant): Boolean = transition(
        deliveryId = deliveryId,
        attemptId = null,
        event = DeliveryEvent.MANUAL_REVIEW,
        detail = reason,
        at = at,
        sql = "UPDATE arcanastory_reward_delivery SET status = 'MANUAL_REVIEW', attempt_id = NULL, last_error = ?, updated_at = ? " +
            "WHERE delivery_id = ? AND status <> 'DELIVERED'",
        args = arrayOf(reason.take(MAX_DETAIL_LENGTH), at, deliveryId),
    )

    override fun attempts(deliveryId: UUID): List<DeliveryAttemptRecord> = database.withConnection { connection ->
        connection.query(
            "SELECT delivery_id, attempt_id, event, detail, created_at FROM arcanastory_reward_delivery_attempt " +
                "WHERE delivery_id = ? ORDER BY id",
            deliveryId,
        ) { rs ->
            DeliveryAttemptRecord(
                deliveryId = rs.uuid("delivery_id"),
                attemptId = rs.uuidOrNull("attempt_id"),
                event = DeliveryEvent.valueOf(rs.getString("event")),
                detail = rs.getString("detail"),
                createdAt = rs.instant("created_at"),
            )
        }
    }

    private fun transition(
        deliveryId: UUID,
        attemptId: UUID?,
        event: DeliveryEvent,
        detail: String?,
        at: Instant,
        sql: String,
        args: Array<Any?>,
    ): Boolean = database.transaction { connection ->
        val changed = connection.update(sql, *args) == 1
        if (changed) connection.logDeliveryEvent(deliveryId, attemptId, event, detail, at)
        changed
    }

    private fun ResultSet.toDelivery() = RewardDeliveryRecord(
        deliveryId = uuid("delivery_id"),
        playerId = uuid("player_uuid"),
        rewardKey = getString("reward_key"),
        componentIndex = getInt("component_index"),
        handler = RewardDelivery.valueOf(getString("handler")),
        component = PersistenceJson.parseRewardComponent(getString("payload_json")),
        status = DeliveryStatus.valueOf(getString("status")),
        attemptId = uuidOrNull("attempt_id"),
        attempts = getInt("attempts"),
        lastError = getString("last_error"),
        createdAt = instant("created_at"),
        updatedAt = instant("updated_at"),
        deliveredAt = instantOrNull("delivered_at"),
    )

    private companion object {
        const val SELECT_DELIVERY =
            "SELECT delivery_id, player_uuid, reward_key, component_index, handler, payload_json, status, " +
                "attempt_id, attempts, last_error, created_at, updated_at, delivered_at FROM arcanastory_reward_delivery"
    }
}
