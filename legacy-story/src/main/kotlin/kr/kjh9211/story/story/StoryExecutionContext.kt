package kr.kjh9211.story.story

import org.bukkit.command.CommandSender

data class StoryExecutionContext(
    val sender: CommandSender,
    val placeholders: Map<String, String> = emptyMap(),
)
