package kr.kjh9211.arcanastory.persistence

import kr.kjh9211.arcanastory.content.model.RewardComponent
import kr.kjh9211.arcanastory.content.model.RewardDelivery
import kr.kjh9211.arcanastory.history.ParticipantRole
import java.time.Instant
import java.util.UUID

// ─── Chapter 완료 commit ────────────────────────────────────────────────

/** 참가자 1명에게 적용할 Chapter 완료 결과. 파티 세션에서는 CONNECTED 참가자 전원이 같은 선택·branch를 받는다. */
data class ChapterCompletionCommit(
    val playerId: UUID,
    val runId: UUID,
    val chapterId: String,
    val role: ParticipantRole,
    val completedAt: Instant,
    val completedBranchIds: Set<String>,
    val choices: List<CommittedChoice>,
    val persistentFlags: Map<String, String>,
    /** 최초 1회만 지급할 보상 후보. 이미 claim된 키는 무시된다. */
    val rewards: List<RewardGrant>,
)

data class CommittedChoice(
    val choiceId: String,
    val optionId: String,
    val decidedBy: UUID,
)

data class RewardGrant(
    val rewardKey: String,
    val components: List<RewardComponent>,
)

sealed interface CommitOutcome {
    data class Committed(
        val newlyClaimedRewardKeys: List<String>,
        val deliveries: List<RewardDeliveryRecord>,
    ) : CommitOutcome

    /** 같은 run·플레이어의 commit이 이미 적용되어 아무 것도 바꾸지 않았다. */
    data object AlreadyCommitted : CommitOutcome
}

// ─── 보상 delivery outbox ───────────────────────────────────────────────

enum class DeliveryStatus {
    PENDING,
    DELIVERING,
    DELIVERED,
    FAILED,
    MANUAL_REVIEW,
}

data class RewardDeliveryRecord(
    val deliveryId: UUID,
    val playerId: UUID,
    val rewardKey: String,
    val componentIndex: Int,
    val handler: RewardDelivery,
    /** claim 시점의 보상 내용. 이후 콘텐츠가 바뀌어도 지급할 내용은 바뀌지 않는다. */
    val component: RewardComponent,
    val status: DeliveryStatus,
    val attemptId: UUID?,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deliveredAt: Instant?,
)

enum class DeliveryEvent {
    CREATED,
    BEGIN,
    DELIVERED,
    REVERTED,
    FAILED,
    MANUAL_REVIEW,
}

data class DeliveryAttemptRecord(
    val deliveryId: UUID,
    val attemptId: UUID?,
    val event: DeliveryEvent,
    val detail: String?,
    val createdAt: Instant,
)

// ─── Run ────────────────────────────────────────────────────────────────

enum class RunEndReason {
    COMPLETED,
    NORMAL_EXIT,
    INTERRUPTED,
    ABANDONED,
}

data class RunRecord(
    val runId: UUID,
    val chapterId: String,
    val contentHash: String,
    val participants: List<UUID>,
    val leaderHistory: List<UUID>,
    val startedAt: Instant,
    val endedAt: Instant?,
    val endReason: RunEndReason?,
)

// ─── 중단 복구 snapshot ─────────────────────────────────────────────────

enum class SnapshotState {
    ACTIVE,
    INTERRUPTED,
}

data class SnapshotLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

data class TemporaryChoice(val optionId: String, val decidedBy: UUID)

data class TemporaryFlag(val value: String, val persist: Boolean)

/** 마지막 checkpoint 시점의 세션 상태. checkpoint 이후의 선택·flag는 담지 않는다. */
data class SessionSnapshotRecord(
    val playerId: UUID,
    val runId: UUID,
    val chapterId: String,
    val contentHash: String,
    val state: SnapshotState,
    /** null이면 Chapter entry. */
    val checkpointId: String?,
    val stepId: String,
    val location: SnapshotLocation?,
    /** choice id → 선택. */
    val temporaryChoices: Map<String, TemporaryChoice>,
    val temporaryFlags: Map<String, TemporaryFlag>,
    val pendingBranches: Set<String>,
    /** checkpoint 시점에 진행 중이던 STORY quest id. 복구 시 이 segment 이후의 quest는 reset한다. */
    val questSegment: List<String>,
    val role: ParticipantRole,
    val updatedAt: Instant,
)

enum class InventorySnapshotKind {
    ENTRY,
    CHECKPOINT,
}

/** [payload]의 인코딩은 [payloadFormat]이 결정한다(인벤토리 codec 소관). 저장소는 불투명한 bytes로 다룬다. */
class InventorySnapshotRecord(
    val playerId: UUID,
    val kind: InventorySnapshotKind,
    val runId: UUID,
    val checkpointId: String?,
    val payloadFormat: String,
    val dataVersion: Int,
    val payload: ByteArray,
    val checksum: String,
    val xpLevel: Int,
    val xpProgress: Float,
    val capturedAt: Instant,
) {
    /** 저장 후 payload가 손상되지 않았는지. */
    val isIntact: Boolean
        get() = checksum == Checksums.sha256(payload)

    companion object {
        fun create(
            playerId: UUID,
            kind: InventorySnapshotKind,
            runId: UUID,
            checkpointId: String?,
            payloadFormat: String,
            dataVersion: Int,
            payload: ByteArray,
            xpLevel: Int,
            xpProgress: Float,
            capturedAt: Instant,
        ) = InventorySnapshotRecord(
            playerId = playerId,
            kind = kind,
            runId = runId,
            checkpointId = checkpointId,
            payloadFormat = payloadFormat,
            dataVersion = dataVersion,
            payload = payload,
            checksum = Checksums.sha256(payload),
            xpLevel = xpLevel,
            xpProgress = xpProgress,
            capturedAt = capturedAt,
        )
    }
}

data class StoredSession(
    val snapshot: SessionSnapshotRecord,
    val inventories: Map<InventorySnapshotKind, InventorySnapshotRecord>,
)
