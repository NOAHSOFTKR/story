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
    val itemFilter: String? = null,
) {
    fun matchesContext(context: StoryExecutionContext): Boolean {
        if (blockFilter != null) {
            val contextBlock = context.placeholders["block"] ?: return false
            if (!matchesFilterValue(blockFilter, contextBlock)) return false
        }
        if (itemFilter != null) {
            val contextItem = context.placeholders["item"] ?: return false
            if (!matchesFilterValue(itemFilter, contextItem)) return false
        }
        return true
    }

    private fun matchesFilterValue(filter: String, contextValue: String): Boolean {
        return if (':' in contextValue) {
            filter.equals(contextValue, ignoreCase = true)
        } else {
            val normalized = if (':' in filter) {
                filter.substringAfter(':').uppercase().replace('-', '_')
            } else {
                filter.uppercase().replace('-', '_')
            }
            normalized == contextValue.uppercase()
        }
    }

    fun execute(context: StoryExecutionContext) {
        context.sender.sendMessage("${ChatColor.AQUA}$title")
        if (description.isNotBlank()) {
            context.sender.sendMessage("${ChatColor.GRAY}$description")
        }

        actions.filter { it.enabled }.forEach { it.execute(context) }
    }
}
