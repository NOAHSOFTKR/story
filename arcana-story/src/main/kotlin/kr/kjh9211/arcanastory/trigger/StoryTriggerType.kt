package kr.kjh9211.arcanastory.trigger

/**
 * Step이 기다리는 스토리 trigger.
 *
 * 블록 파괴·몹 처치·지역 진입·아이템 획득 같은 게임플레이 카운트는 여기 두지 않는다. 그런 조건은
 * Quest objective로 정의하고 [QUEST_OBJECTIVE_COMPLETE] / [QUEST_COMPLETE]로 관찰한다.
 */
enum class StoryTriggerType(
    val yamlName: String,
    val requiredParams: Set<String>,
    val optionalParams: Set<String> = emptySet(),
) {
    DIALOGUE_END("dialogue_end", setOf("dialogue")),
    CUTSCENE_END("cutscene_end", setOf("cutscene")),
    CHOICE_MADE("choice_made", setOf("choice"), setOf("option")),
    TIMER("timer", setOf("ticks")),
    QUEST_START("quest_start", setOf("quest")),
    QUEST_OBJECTIVE_COMPLETE("quest_objective_complete", setOf("quest", "objective")),
    QUEST_COMPLETE("quest_complete", setOf("quest")),
    QUEST_FAIL("quest_fail", setOf("quest")),
    CUSTOM("custom", setOf("key")),
    ;

    companion object {
        fun fromYaml(name: String): StoryTriggerType? =
            entries.firstOrNull { it.yamlName.equals(name.trim(), ignoreCase = true) }
    }
}
