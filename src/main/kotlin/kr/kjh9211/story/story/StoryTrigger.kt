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
    val actions: List<StoryAction>,
    val blockFilter: String? = null,
) {
    fun matchesContext(context: StoryExecutionContext): Boolean {
        if (blockFilter == null) return true
        val contextBlock = context.placeholders["block"] ?: return false
        val normalizedFilter = if (':' in blockFilter) {
            blockFilter.substringAfter(':').uppercase().replace('-', '_')
        } else {
            blockFilter.uppercase().replace('-', '_')
        }
        return normalizedFilter == contextBlock.uppercase()
    }

    fun execute(context: StoryExecutionContext) {
        context.sender.sendMessage("${ChatColor.AQUA}$title")
        if (description.isNotBlank()) {
            context.sender.sendMessage("${ChatColor.GRAY}$description")
        }

        actions.filter { it.enabled }.forEach { it.execute(context) }
    }
}
