package kr.kjh9211.story.story

data class StoryQuest(
    val enabled: Boolean,
    val title: String,
    val objectives: List<StoryObjective>,
) {
    fun findObjective(objectiveId: String?): StoryObjective? {
        if (objectiveId == null) {
            return null
        }
        return objectives.firstOrNull { it.id.equals(objectiveId, ignoreCase = true) }
    }
}
