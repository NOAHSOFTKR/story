package kr.kjh9211.arcanastory.history

import java.time.Instant
import java.util.UUID

/** 선택이 기록될 때 그 플레이어가 세션에서 맡은 역할. */
enum class ParticipantRole {
    SOLO,
    LEADER,
    MEMBER,
}

data class BranchKey(val chapterId: String, val branchId: String)

/** choice id는 Chapter 안에서만 고유하므로 항상 Chapter id와 함께 식별한다. */
data class ChoiceKey(val chapterId: String, val choiceId: String)

data class ChapterCompletion(
    val chapterId: String,
    val firstCompletedAt: Instant,
    val lastCompletedAt: Instant,
    val completionCount: Int,
)

data class BranchCompletion(
    val chapterId: String,
    val branchId: String,
    val firstCompletedAt: Instant,
    val lastCompletedAt: Instant,
    val completionCount: Int,
)

data class CanonicalChoice(
    val chapterId: String,
    val choiceId: String,
    val optionId: String,
    val runId: UUID,
    val updatedAt: Instant,
)

data class ChoiceRecord(
    val chapterId: String,
    val choiceId: String,
    val optionId: String,
    val runId: UUID,
    /** 선택을 결정한 플레이어. 파티 세션에서는 Session Leader다. */
    val decidedBy: UUID,
    val role: ParticipantRole,
    val committedAt: Instant,
)

data class RewardClaim(
    val rewardKey: String,
    /** 레거시 이관처럼 run 없이 기록된 claim이면 null. */
    val runId: UUID?,
    val claimedAt: Instant,
)

/** 플레이어 1명의 영구 Story 기록. 완료된 Chapter에서 commit된 내용만 담긴다. */
data class StoryHistory(
    val playerId: UUID,
    val completedChapters: Map<String, ChapterCompletion>,
    val completedBranches: Map<BranchKey, BranchCompletion>,
    val canonicalChoices: Map<ChoiceKey, CanonicalChoice>,
    /** commit 순서대로 정렬된 선택 이력. 이전 선택을 지우지 않는다. */
    val choiceHistory: List<ChoiceRecord>,
    val claimedRewards: Map<String, RewardClaim>,
    val flags: Map<String, String>,
) {
    fun hasCompleted(chapterId: String): Boolean = chapterId in completedChapters

    companion object {
        fun empty(playerId: UUID) = StoryHistory(
            playerId = playerId,
            completedChapters = emptyMap(),
            completedBranches = emptyMap(),
            canonicalChoices = emptyMap(),
            choiceHistory = emptyList(),
            claimedRewards = emptyMap(),
            flags = emptyMap(),
        )
    }
}
