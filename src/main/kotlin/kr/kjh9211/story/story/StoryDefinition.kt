package kr.kjh9211.story.story

import java.io.File

data class StoryDefinition(
    val id: String,
    val title: String,
    val enabled: Boolean,
    val previousStoryId: String?,
    val nextStoryId: String?,
    val quest: StoryQuest?,
    val triggers: List<StoryTrigger>,
    val sourceFile: File,
) {
    fun findTrigger(triggerId: String?): StoryTrigger? {
        if (triggerId == null) {
            return null
        }
        return triggers.firstOrNull { it.id.equals(triggerId, ignoreCase = true) }
    }
}
