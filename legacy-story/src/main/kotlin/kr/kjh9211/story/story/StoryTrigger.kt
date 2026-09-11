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
    val repeatable: Boolean = false,
    val completesStory: Boolean = false,
    val filters: Map<String, String> = emptyMap(),
    val actions: List<StoryAction>,
) {
    fun matchesContext(context: StoryExecutionContext): Boolean {
        return filters.all { (key, filter) ->
            context.placeholders[key]?.let { value -> matchesFilterValue(filter, value) } ?: false
        }
    }

    private fun matchesFilterValue(filter: String, contextValue: String): Boolean {
        return if (':' in filter) {
            if (':' in contextValue) {
                filter.equals(contextValue, ignoreCase = true)
            } else {
                filter.substringBefore(':').equals("minecraft", ignoreCase = true) &&
                    filter.substringAfter(':').equals(contextValue, ignoreCase = true)
            }
        } else {
            val normalizedFilter = filter.substringAfter(':').uppercase().replace('-', '_')
            val normalizedContext = contextValue.substringAfter(':').uppercase().replace('-', '_')
            normalizedFilter == normalizedContext
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
