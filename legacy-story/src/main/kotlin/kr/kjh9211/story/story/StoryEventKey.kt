package kr.kjh9211.story.story

object StoryEventKey {
    private val supported = setOf(
        "block_break",
        "block_interact_chest",
        "customcrafting_craft",
        "itemsadder_item_pickup",
        "itemsadder_item_equip",
        "mythicmobs_mob_death",
        "quests_quest_complete",
        "quests_quest_reward_claim",
        "towny_town_create",
        "towny_town_claim",
        "towny_town_unclaim",
        "towny_town_add_resident",
        "towny_town_remove_resident",
        "towny_nation_create",
        "towny_town_transaction",
        "towny_nation_transaction",
        "cutscene_fire",
        "cutscene_end",
        "story_done",
    )

    fun isSupported(eventKey: String): Boolean = eventKey.lowercase() in supported
}
