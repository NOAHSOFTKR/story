package kr.kjh9211.story.story

import org.bukkit.ChatColor
data class StoryTrigger(
    val id: String,
    val type: StoryTriggerType,
    val enabled: Boolean,
    val title: String,
    val description: String,
    val event: String,
    val objectiveId: String? = null,
    val progressAmount: Int = 1,
    val blockName: String? = null,
    val iaItemId: String? = null,
    val actions: List<StoryAction>,
) {
    fun execute(context: StoryExecutionContext) {
        context.sender.sendMessage("${ChatColor.AQUA}$title")
        if (description.isNotBlank()) {
            context.sender.sendMessage("${ChatColor.GRAY}$description")
        }

        actions.filter { it.enabled }.forEach { it.execute(context) }
    }
}
