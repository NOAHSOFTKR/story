package kr.kjh9211.arcanastory.content

/** 검증 시 Chapter가 참조하는 Quest 정의를 조회한다. 런타임 구현은 QuestAPI를 사용한다. */
fun interface QuestCatalog {
    fun find(questId: String): StoryQuestInfo?
}

data class StoryQuestInfo(
    val id: String,
    val scope: String,
    val objectiveIds: Set<String>,
    val hasRewards: Boolean,
    val abandonAllowed: Boolean,
    val hasBranch: Boolean,
)

enum class ResourceKind {
    SOUND,
    PARTICLE,
    EFFECT,
    MATERIAL,
}

/** 검증 시 사운드·파티클·효과·바닐라 아이템 키가 실제로 존재하는지 판정한다. 런타임 구현은 Bukkit Registry를 사용한다. */
fun interface ResourceKeyCatalog {
    fun contains(kind: ResourceKind, key: String): Boolean
}
