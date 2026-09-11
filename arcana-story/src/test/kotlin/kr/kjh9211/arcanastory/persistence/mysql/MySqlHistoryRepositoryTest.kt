package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.content.model.RewardComponent
import kr.kjh9211.arcanastory.history.BranchKey
import kr.kjh9211.arcanastory.history.ChoiceKey
import kr.kjh9211.arcanastory.history.ParticipantRole
import kr.kjh9211.arcanastory.persistence.ChapterCompletionCommit
import kr.kjh9211.arcanastory.persistence.CommitOutcome
import kr.kjh9211.arcanastory.persistence.CommittedChoice
import kr.kjh9211.arcanastory.persistence.DeliveryStatus
import kr.kjh9211.arcanastory.persistence.RewardGrant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class MySqlHistoryRepositoryTest {

    private val chapterId = "prologue.exile"
    private val at = Instant.ofEpochMilli(1_700_000_000_000)

    private fun commit(
        playerId: UUID,
        runId: UUID,
        optionId: String,
        branchId: String,
        decidedBy: UUID = playerId,
        role: ParticipantRole = ParticipantRole.SOLO,
        rewards: List<RewardGrant> = listOf(
            RewardGrant("$chapterId.first_clear", listOf(RewardComponent.Item("arcana:tattered_material", 3))),
            RewardGrant("$chapterId.$branchId.first_clear", listOf(RewardComponent.Experience(20))),
        ),
        completedAt: Instant = at,
    ) = ChapterCompletionCommit(
        playerId = playerId,
        runId = runId,
        chapterId = chapterId,
        role = role,
        completedAt = completedAt,
        completedBranchIds = setOf(branchId),
        choices = listOf(CommittedChoice("chief_words", optionId, decidedBy)),
        persistentFlags = mapOf("prologue_exiled" to "true"),
        rewards = rewards,
    )

    @Test
    fun `기록이 없는 플레이어는 빈 History다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val history = MySqlHistoryRepository(database).load(UUID.randomUUID())

            assertTrue(history.completedChapters.isEmpty())
            assertTrue(history.choiceHistory.isEmpty())
        }
    }

    @Test
    fun `최초 완료는 History와 보상 claim·delivery를 함께 기록한다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlHistoryRepository(database)
            val playerId = UUID.randomUUID()
            val runId = UUID.randomUUID()

            val outcome = repository.commitChapterCompletion(commit(playerId, runId, "trust", "trust"))

            val committed = outcome as CommitOutcome.Committed
            assertEquals(listOf("$chapterId.first_clear", "$chapterId.trust.first_clear"), committed.newlyClaimedRewardKeys)
            assertEquals(2, committed.deliveries.size)
            assertTrue(committed.deliveries.all { it.status == DeliveryStatus.PENDING })

            val history = repository.load(playerId)
            assertEquals(1, history.completedChapters.getValue(chapterId).completionCount)
            assertEquals(1, history.completedBranches.getValue(BranchKey(chapterId, "trust")).completionCount)
            assertEquals("trust", history.canonicalChoices.getValue(ChoiceKey(chapterId, "chief_words")).optionId)
            assertEquals(listOf("trust"), history.choiceHistory.map { it.optionId })
            assertEquals(mapOf("prologue_exiled" to "true"), history.flags)
            assertEquals(setOf("$chapterId.first_clear", "$chapterId.trust.first_clear"), history.claimedRewards.keys)

            val deliveries = MySqlRewardDeliveryRepository(database).findUnfinished(playerId)
            assertEquals(2, deliveries.size)
            assertEquals(RewardComponent.Item("arcana:tattered_material", 3), deliveries.first().component)
        }
    }

    @Test
    fun `같은 run으로 다시 commit하면 아무 것도 바꾸지 않는다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlHistoryRepository(database)
            val playerId = UUID.randomUUID()
            val runId = UUID.randomUUID()
            repository.commitChapterCompletion(commit(playerId, runId, "trust", "trust"))

            val retry = repository.commitChapterCompletion(commit(playerId, runId, "trust", "trust"))

            assertEquals(CommitOutcome.AlreadyCommitted, retry)
            val history = repository.load(playerId)
            assertEquals(1, history.completedChapters.getValue(chapterId).completionCount)
            assertEquals(1, history.choiceHistory.size)
        }
    }

    @Test
    fun `재플레이는 이력을 남기고 canonical을 바꾸지만 이미 받은 보상은 다시 주지 않는다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlHistoryRepository(database)
            val playerId = UUID.randomUUID()
            repository.commitChapterCompletion(commit(playerId, UUID.randomUUID(), "trust", "trust"))

            val replay = repository.commitChapterCompletion(
                commit(playerId, UUID.randomUUID(), "distrust", "distrust", completedAt = at.plusSeconds(60)),
            ) as CommitOutcome.Committed

            // chapter first_clear는 이미 claim되어 distrust branch 보상만 새로 생긴다.
            assertEquals(listOf("$chapterId.distrust.first_clear"), replay.newlyClaimedRewardKeys)
            assertEquals(1, replay.deliveries.size)

            val history = repository.load(playerId)
            assertEquals(2, history.completedChapters.getValue(chapterId).completionCount)
            assertEquals(listOf("trust", "distrust"), history.choiceHistory.map { it.optionId })
            assertEquals("distrust", history.canonicalChoices.getValue(ChoiceKey(chapterId, "chief_words")).optionId)
            assertEquals(setOf("trust", "distrust"), history.completedBranches.keys.map { it.branchId }.toSet())
        }
    }

    @Test
    fun `파티 참가자는 같은 선택을 각자의 History에 기록한다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlHistoryRepository(database)
            val leader = UUID.randomUUID()
            val member = UUID.randomUUID()
            val runId = UUID.randomUUID()

            repository.commitChapterCompletion(commit(leader, runId, "distrust", "distrust", leader, ParticipantRole.LEADER))
            repository.commitChapterCompletion(commit(member, runId, "distrust", "distrust", leader, ParticipantRole.MEMBER))

            listOf(leader, member).forEach { playerId ->
                val record = repository.load(playerId).choiceHistory.single()
                assertEquals("distrust", record.optionId)
                assertEquals(leader, record.decidedBy)
            }
            assertEquals(ParticipantRole.MEMBER, repository.load(member).choiceHistory.single().role)
        }
    }

    @Test
    fun `중복 지급을 막을 수 없는 보상은 기록을 거부한다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlHistoryRepository(database)
            val unsafe = listOf(RewardGrant("$chapterId.first_clear", listOf(RewardComponent.Command("give x"))))

            assertThrows<IllegalArgumentException> {
                repository.commitChapterCompletion(commit(UUID.randomUUID(), UUID.randomUUID(), "trust", "trust", rewards = unsafe))
            }
        }
    }
}
