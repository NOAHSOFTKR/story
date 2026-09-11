package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.content.model.RewardComponent
import kr.kjh9211.arcanastory.history.ParticipantRole
import kr.kjh9211.arcanastory.persistence.ChapterCompletionCommit
import kr.kjh9211.arcanastory.persistence.CommitOutcome
import kr.kjh9211.arcanastory.persistence.Database
import kr.kjh9211.arcanastory.persistence.DeliveryEvent
import kr.kjh9211.arcanastory.persistence.DeliveryStatus
import kr.kjh9211.arcanastory.persistence.RewardGrant
import kr.kjh9211.arcanastory.persistence.RunEndReason
import kr.kjh9211.arcanastory.persistence.RunRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MySqlRewardDeliveryRepositoryTest {

    private val at = Instant.ofEpochMilli(1_700_000_000_000)

    private fun createDelivery(database: Database, playerId: UUID): UUID {
        val outcome = MySqlHistoryRepository(database).commitChapterCompletion(
            ChapterCompletionCommit(
                playerId = playerId,
                runId = UUID.randomUUID(),
                chapterId = "prologue.exile",
                role = ParticipantRole.SOLO,
                completedAt = at,
                completedBranchIds = emptySet(),
                choices = emptyList(),
                persistentFlags = emptyMap(),
                rewards = listOf(RewardGrant("prologue.exile.first_clear", listOf(RewardComponent.Experience(10)))),
            ),
        )
        return (outcome as CommitOutcome.Committed).deliveries.single().deliveryId
    }

    @Test
    fun `한 번에 한 시도만 DELIVERING이 된다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlRewardDeliveryRepository(database)
            val deliveryId = createDelivery(database, UUID.randomUUID())
            val first = UUID.randomUUID()

            assertTrue(repository.beginAttempt(deliveryId, first, at))
            assertFalse(repository.beginAttempt(deliveryId, UUID.randomUUID(), at))

            val record = repository.find(deliveryId)!!
            assertEquals(DeliveryStatus.DELIVERING, record.status)
            assertEquals(first, record.attemptId)
            assertEquals(1, record.attempts)
        }
    }

    @Test
    fun `다른 시도 id로는 완료 처리할 수 없다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlRewardDeliveryRepository(database)
            val deliveryId = createDelivery(database, UUID.randomUUID())
            val attemptId = UUID.randomUUID()
            repository.beginAttempt(deliveryId, attemptId, at)

            assertFalse(repository.markDelivered(deliveryId, UUID.randomUUID(), at))
            assertTrue(repository.markDelivered(deliveryId, attemptId, at))

            val record = repository.find(deliveryId)!!
            assertEquals(DeliveryStatus.DELIVERED, record.status)
            assertEquals(at, record.deliveredAt)
            assertTrue(repository.findUnfinished(record.playerId).isEmpty())
        }
    }

    @Test
    fun `지급이 영속화되지 않았으면 PENDING으로 되돌려 다시 시도한다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlRewardDeliveryRepository(database)
            val playerId = UUID.randomUUID()
            val deliveryId = createDelivery(database, playerId)
            val crashed = UUID.randomUUID()
            repository.beginAttempt(deliveryId, crashed, at)

            assertTrue(repository.revertToPending(deliveryId, crashed, "마커 없음", at))

            val reverted = repository.find(deliveryId)!!
            assertEquals(DeliveryStatus.PENDING, reverted.status)
            assertNull(reverted.attemptId)
            assertEquals(listOf(deliveryId), repository.findUnfinished(playerId).map { it.deliveryId })

            val retry = UUID.randomUUID()
            assertTrue(repository.beginAttempt(deliveryId, retry, at))
            assertEquals(2, repository.find(deliveryId)!!.attempts)
            assertTrue(repository.markDelivered(deliveryId, retry, at))

            assertEquals(
                listOf(
                    DeliveryEvent.CREATED,
                    DeliveryEvent.BEGIN,
                    DeliveryEvent.REVERTED,
                    DeliveryEvent.BEGIN,
                    DeliveryEvent.DELIVERED,
                ),
                repository.attempts(deliveryId).map { it.event },
            )
        }
    }

    @Test
    fun `실패한 지급은 다시 시도할 수 있고 완료된 지급은 수동 확인 대상으로 바꿀 수 없다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlRewardDeliveryRepository(database)
            val deliveryId = createDelivery(database, UUID.randomUUID())
            val failed = UUID.randomUUID()
            repository.beginAttempt(deliveryId, failed, at)

            assertTrue(repository.markFailed(deliveryId, failed, "인벤토리 공간 부족", at))
            assertEquals(DeliveryStatus.FAILED, repository.find(deliveryId)!!.status)

            val retry = UUID.randomUUID()
            assertTrue(repository.beginAttempt(deliveryId, retry, at))
            assertTrue(repository.markDelivered(deliveryId, retry, at))

            assertFalse(repository.markManualReview(deliveryId, "이미 지급됨", at))
        }
    }

    @Test
    fun `run은 한 번만 종료된다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlRunRepository(database)
            val runId = UUID.randomUUID()
            val leader = UUID.randomUUID()
            repository.start(
                RunRecord(
                    runId = runId,
                    chapterId = "prologue.exile",
                    contentHash = "hash-1",
                    participants = listOf(leader),
                    leaderHistory = listOf(leader),
                    startedAt = at,
                    endedAt = null,
                    endReason = null,
                ),
            )

            assertTrue(repository.end(runId, RunEndReason.COMPLETED, at.plusSeconds(600), listOf(leader)))
            assertFalse(repository.end(runId, RunEndReason.INTERRUPTED, at.plusSeconds(900), listOf(leader)))

            val stored = repository.find(runId)!!
            assertEquals(RunEndReason.COMPLETED, stored.endReason)
            assertEquals(at.plusSeconds(600), stored.endedAt)
            assertEquals(listOf(leader), stored.participants)
        }
    }
}
