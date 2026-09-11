package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.content.model.RewardDelivery
import kr.kjh9211.arcanastory.history.BranchCompletion
import kr.kjh9211.arcanastory.history.BranchKey
import kr.kjh9211.arcanastory.history.CanonicalChoice
import kr.kjh9211.arcanastory.history.ChapterCompletion
import kr.kjh9211.arcanastory.history.ChoiceKey
import kr.kjh9211.arcanastory.history.ChoiceRecord
import kr.kjh9211.arcanastory.history.ParticipantRole
import kr.kjh9211.arcanastory.history.RewardClaim
import kr.kjh9211.arcanastory.history.StoryHistory
import kr.kjh9211.arcanastory.persistence.ChapterCompletionCommit
import kr.kjh9211.arcanastory.persistence.CommitOutcome
import kr.kjh9211.arcanastory.persistence.Database
import kr.kjh9211.arcanastory.persistence.DeliveryEvent
import kr.kjh9211.arcanastory.persistence.DeliveryStatus
import kr.kjh9211.arcanastory.persistence.HistoryRepository
import kr.kjh9211.arcanastory.persistence.RewardDeliveryRecord
import java.sql.Connection
import java.util.UUID

class MySqlHistoryRepository(
    private val database: Database,
) : HistoryRepository {

    override fun load(playerId: UUID): StoryHistory = database.withConnection { connection ->
        StoryHistory(
            playerId = playerId,
            completedChapters = connection.query(
                "SELECT chapter_id, first_completed_at, last_completed_at, completion_count " +
                    "FROM arcanastory_chapter_completion WHERE player_uuid = ?",
                playerId,
            ) { rs ->
                ChapterCompletion(
                    chapterId = rs.getString("chapter_id"),
                    firstCompletedAt = rs.instant("first_completed_at"),
                    lastCompletedAt = rs.instant("last_completed_at"),
                    completionCount = rs.getInt("completion_count"),
                )
            }.associateBy { it.chapterId },
            completedBranches = connection.query(
                "SELECT chapter_id, branch_id, first_completed_at, last_completed_at, completion_count " +
                    "FROM arcanastory_branch_completion WHERE player_uuid = ?",
                playerId,
            ) { rs ->
                BranchCompletion(
                    chapterId = rs.getString("chapter_id"),
                    branchId = rs.getString("branch_id"),
                    firstCompletedAt = rs.instant("first_completed_at"),
                    lastCompletedAt = rs.instant("last_completed_at"),
                    completionCount = rs.getInt("completion_count"),
                )
            }.associateBy { BranchKey(it.chapterId, it.branchId) },
            canonicalChoices = connection.query(
                "SELECT chapter_id, choice_id, option_id, run_id, updated_at FROM arcanastory_canonical_choice WHERE player_uuid = ?",
                playerId,
            ) { rs ->
                CanonicalChoice(
                    chapterId = rs.getString("chapter_id"),
                    choiceId = rs.getString("choice_id"),
                    optionId = rs.getString("option_id"),
                    runId = rs.uuid("run_id"),
                    updatedAt = rs.instant("updated_at"),
                )
            }.associateBy { ChoiceKey(it.chapterId, it.choiceId) },
            choiceHistory = connection.query(
                "SELECT chapter_id, choice_id, option_id, run_id, decided_by_uuid, participant_role, committed_at " +
                    "FROM arcanastory_choice_event WHERE player_uuid = ? ORDER BY id",
                playerId,
            ) { rs ->
                ChoiceRecord(
                    chapterId = rs.getString("chapter_id"),
                    choiceId = rs.getString("choice_id"),
                    optionId = rs.getString("option_id"),
                    runId = rs.uuid("run_id"),
                    decidedBy = rs.uuid("decided_by_uuid"),
                    role = ParticipantRole.valueOf(rs.getString("participant_role")),
                    committedAt = rs.instant("committed_at"),
                )
            },
            claimedRewards = connection.query(
                "SELECT reward_key, run_id, claimed_at FROM arcanastory_reward_claim WHERE player_uuid = ?",
                playerId,
            ) { rs ->
                RewardClaim(rs.getString("reward_key"), rs.uuidOrNull("run_id"), rs.instant("claimed_at"))
            }.associateBy { it.rewardKey },
            flags = connection.query("SELECT flag, value FROM arcanastory_flag WHERE player_uuid = ?", playerId) { rs ->
                rs.getString("flag") to rs.getString("value")
            }.toMap(),
        )
    }

    override fun commitChapterCompletion(commit: ChapterCompletionCommit): CommitOutcome {
        val unsafe = commit.rewards.flatMap { it.components }.firstOrNull { it.delivery != RewardDelivery.PLAYERDATA }
        require(unsafe == null) { "PLAYERDATA가 아닌 보상은 최초 완료 보상으로 기록할 수 없습니다: $unsafe" }
        return database.transaction { connection -> apply(connection, commit) }
    }

    private fun apply(connection: Connection, commit: ChapterCompletionCommit): CommitOutcome {
        val at = commit.completedAt
        val firstApplication = connection.insertUnlessDuplicate(
            "INSERT INTO arcanastory_run_commit (run_id, player_uuid, committed_at) VALUES (?, ?, ?)",
            commit.runId,
            commit.playerId,
            at,
        )
        if (!firstApplication) return CommitOutcome.AlreadyCommitted

        connection.update(
            "INSERT INTO arcanastory_player (player_uuid, created_at, updated_at) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)",
            commit.playerId,
            at,
            at,
        )
        connection.update(
            "INSERT INTO arcanastory_chapter_completion " +
                "(player_uuid, chapter_id, first_completed_at, last_completed_at, completion_count, first_run_id, last_run_id) " +
                "VALUES (?, ?, ?, ?, 1, ?, ?) " +
                "ON DUPLICATE KEY UPDATE last_completed_at = VALUES(last_completed_at), " +
                "completion_count = completion_count + 1, last_run_id = VALUES(last_run_id)",
            commit.playerId,
            commit.chapterId,
            at,
            at,
            commit.runId,
            commit.runId,
        )
        commit.completedBranchIds.forEach { branchId ->
            connection.update(
                "INSERT INTO arcanastory_branch_completion " +
                    "(player_uuid, chapter_id, branch_id, first_completed_at, last_completed_at, completion_count) " +
                    "VALUES (?, ?, ?, ?, ?, 1) " +
                    "ON DUPLICATE KEY UPDATE last_completed_at = VALUES(last_completed_at), completion_count = completion_count + 1",
                commit.playerId,
                commit.chapterId,
                branchId,
                at,
                at,
            )
        }
        commit.choices.forEach { choice ->
            connection.update(
                "INSERT INTO arcanastory_choice_event " +
                    "(player_uuid, chapter_id, choice_id, option_id, run_id, decided_by_uuid, participant_role, committed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                commit.playerId,
                commit.chapterId,
                choice.choiceId,
                choice.optionId,
                commit.runId,
                choice.decidedBy,
                commit.role.name,
                at,
            )
            connection.update(
                "INSERT INTO arcanastory_canonical_choice (player_uuid, chapter_id, choice_id, option_id, run_id, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE option_id = VALUES(option_id), run_id = VALUES(run_id), updated_at = VALUES(updated_at)",
                commit.playerId,
                commit.chapterId,
                choice.choiceId,
                choice.optionId,
                commit.runId,
                at,
            )
        }
        commit.persistentFlags.forEach { (flag, value) ->
            connection.update(
                "INSERT INTO arcanastory_flag (player_uuid, flag, value, run_id, updated_at) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE value = VALUES(value), run_id = VALUES(run_id), updated_at = VALUES(updated_at)",
                commit.playerId,
                flag,
                value,
                commit.runId,
                at,
            )
        }

        val claimed = mutableListOf<String>()
        val deliveries = mutableListOf<RewardDeliveryRecord>()
        commit.rewards.forEach { grant ->
            val newClaim = connection.insertUnlessDuplicate(
                "INSERT INTO arcanastory_reward_claim (player_uuid, reward_key, source, chapter_id, run_id, claimed_at) " +
                    "VALUES (?, ?, 'RUN', ?, ?, ?)",
                commit.playerId,
                grant.rewardKey,
                commit.chapterId,
                commit.runId,
                at,
            )
            if (!newClaim) return@forEach
            claimed += grant.rewardKey

            grant.components.forEachIndexed { index, component ->
                val delivery = RewardDeliveryRecord(
                    deliveryId = UUID.randomUUID(),
                    playerId = commit.playerId,
                    rewardKey = grant.rewardKey,
                    componentIndex = index,
                    handler = component.delivery,
                    component = component,
                    status = DeliveryStatus.PENDING,
                    attemptId = null,
                    attempts = 0,
                    lastError = null,
                    createdAt = at,
                    updatedAt = at,
                    deliveredAt = null,
                )
                connection.update(
                    "INSERT INTO arcanastory_reward_delivery " +
                        "(delivery_id, player_uuid, reward_key, component_index, handler, payload_json, status, attempts, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    delivery.deliveryId,
                    delivery.playerId,
                    delivery.rewardKey,
                    delivery.componentIndex,
                    delivery.handler.name,
                    PersistenceJson.rewardComponent(component),
                    delivery.status.name,
                    at,
                    at,
                )
                connection.logDeliveryEvent(delivery.deliveryId, null, DeliveryEvent.CREATED, "run ${commit.runId}", at)
                deliveries += delivery
            }
        }

        return CommitOutcome.Committed(claimed, deliveries)
    }
}
