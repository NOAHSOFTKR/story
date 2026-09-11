package kr.kjh9211.arcanastory.persistence

import kr.kjh9211.arcanastory.history.StoryHistory
import java.time.Instant
import java.util.UUID

// 모든 저장소 호출은 블로킹 I/O다. 메인 스레드에서 호출하지 않는다.

interface HistoryRepository {
    fun load(playerId: UUID): StoryHistory

    /**
     * Chapter 완료를 한 트랜잭션으로 반영한다: 완료 횟수, branch, 선택 이력 append·canonical 갱신, 영구 flag,
     * 신규 보상 claim과 delivery 생성. 같은 run·플레이어로 다시 호출하면 [CommitOutcome.AlreadyCommitted].
     */
    fun commitChapterCompletion(commit: ChapterCompletionCommit): CommitOutcome
}

interface SessionSnapshotRepository {
    /** snapshot과 (있으면) 인벤토리 snapshot을 한 트랜잭션으로 저장해 두 값이 항상 같은 checkpoint를 가리키게 한다. */
    fun save(snapshot: SessionSnapshotRecord, inventory: InventorySnapshotRecord?)

    fun load(playerId: UUID): StoredSession?

    /** session snapshot과 인벤토리 snapshot을 함께 삭제한다. */
    fun delete(playerId: UUID)
}

/**
 * 보상 delivery outbox. 상태 전이는 모두 조건부 UPDATE이며, attempt id가 맞을 때만 성공한다.
 * 모든 전이는 attempt 로그에 함께 기록된다.
 */
interface RewardDeliveryRepository {
    /** PENDING·DELIVERING·FAILED 상태. */
    fun findUnfinished(playerId: UUID): List<RewardDeliveryRecord>

    fun find(deliveryId: UUID): RewardDeliveryRecord?

    /** PENDING·FAILED → DELIVERING. 다른 시도가 이미 진행 중이면 false. */
    fun beginAttempt(deliveryId: UUID, attemptId: UUID, at: Instant): Boolean

    /** DELIVERING(같은 attempt) → DELIVERED. */
    fun markDelivered(deliveryId: UUID, attemptId: UUID, at: Instant): Boolean

    /** DELIVERING(같은 attempt) → PENDING. 크래시 복구에서 지급이 영속화되지 않았음을 확인했을 때 사용한다. */
    fun revertToPending(deliveryId: UUID, attemptId: UUID, reason: String, at: Instant): Boolean

    /** DELIVERING(같은 attempt) → FAILED. */
    fun markFailed(deliveryId: UUID, attemptId: UUID, error: String, at: Instant): Boolean

    /** DELIVERED가 아닌 delivery를 운영자 확인 대상으로 전환한다. */
    fun markManualReview(deliveryId: UUID, reason: String, at: Instant): Boolean

    fun attempts(deliveryId: UUID): List<DeliveryAttemptRecord>
}

interface RunRepository {
    fun start(run: RunRecord)

    /** 아직 끝나지 않은 run만 종료한다. 이미 끝났으면 false. */
    fun end(runId: UUID, reason: RunEndReason, endedAt: Instant, leaderHistory: List<UUID>): Boolean

    fun find(runId: UUID): RunRecord?
}
