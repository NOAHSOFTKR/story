package kr.kjh9211.arcanastory.content.model

/** 보상 지급이 크래시 후에도 중복·누락 없이 추적 가능한지에 따른 분류. */
enum class RewardDelivery {
    /** 아이템·XP. playerdata 마커와 함께 저장되어 사실상 1회 지급이 보장된다. */
    PLAYERDATA,

    /** 외부 시스템이 멱등키를 받아 중복을 막아야 하는 보상(칭호·코스메틱·공용 해금). */
    EXTERNAL_IDEMPOTENT,

    /** 중복 실행을 막을 수 없는 보상(콘솔 명령, 멱등키 없는 경제 입금). */
    AT_MOST_ONCE,
}

sealed interface RewardComponent {
    val delivery: RewardDelivery

    data class Item(val item: String, val amount: Int) : RewardComponent {
        override val delivery: RewardDelivery
            get() = RewardDelivery.PLAYERDATA
    }

    data class Experience(val points: Int) : RewardComponent {
        override val delivery: RewardDelivery
            get() = RewardDelivery.PLAYERDATA
    }

    data class Command(val command: String) : RewardComponent {
        override val delivery: RewardDelivery
            get() = RewardDelivery.AT_MOST_ONCE
    }

    data class Money(val amount: Double) : RewardComponent {
        override val delivery: RewardDelivery
            get() = RewardDelivery.AT_MOST_ONCE
    }

    /** [kind]는 `title`, `cosmetic`, `unlock` 중 하나. */
    data class SharedUnlock(val kind: String, val id: String) : RewardComponent {
        override val delivery: RewardDelivery
            get() = RewardDelivery.EXTERNAL_IDEMPOTENT
    }
}

data class ChapterRewards(
    val firstClear: List<RewardComponent>,
    /** branch id → 해당 branch를 처음 완료했을 때의 보상. */
    val branchFirstClear: Map<String, List<RewardComponent>>,
) {
    companion object {
        val NONE = ChapterRewards(emptyList(), emptyMap())
    }
}

object RewardKeys {
    const val FIRST_CLEAR = "first_clear"

    fun firstClear(chapterId: String): String = "$chapterId.$FIRST_CLEAR"

    fun branchFirstClear(chapterId: String, branchId: String): String = "$chapterId.$branchId.$FIRST_CLEAR"
}
