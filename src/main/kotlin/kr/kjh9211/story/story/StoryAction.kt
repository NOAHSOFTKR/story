package kr.kjh9211.story.story

import org.bukkit.Bukkit
import org.bukkit.ChatColor

data class StoryAction(
    val id: String,
    val type: ActionType,
    val enabled: Boolean,
    val message: String? = null,
    val messageTarget: String? = null,
    val commands: List<String> = emptyList(),
) {
    fun execute(context: StoryExecutionContext) {
        if (!enabled) {
            return
        }

        when (type) {
            ActionType.MESSAGE -> message?.let {
                val formattedMessage = colorize(replaceRuntimeValues(it, context))
                when (messageTarget?.lowercase()) {
                    "everyone", "allplayers" -> Bukkit.broadcastMessage(formattedMessage)
                    else -> context.sender.sendMessage(formattedMessage)
                }
            }
            ActionType.COMMANDS -> commands.forEach {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceRuntimeValues(it, context))
            }
        }
    }

    private fun replaceRuntimeValues(value: String, context: StoryExecutionContext): String {
        var result = value
            .replace("{sender}", context.sender.name)
            .replace("{player}", context.placeholders["player"] ?: context.sender.name)

        context.placeholders.forEach { (key, placeholderValue) ->
            result = result.replace("{$key}", placeholderValue)
        }
        return result
    }

    private fun colorize(value: String): String = ChatColor.translateAlternateColorCodes('&', value)
}
