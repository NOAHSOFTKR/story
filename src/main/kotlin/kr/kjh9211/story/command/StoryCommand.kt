package kr.kjh9211.story.command

import kr.kjh9211.story.StoryMoveDirection
import kr.kjh9211.story.story.StoryAction
import kr.kjh9211.story.story.StoryExecutionContext
import kr.kjh9211.story.story.StoryRegistry
import kr.kjh9211.story.story.StoryTrigger
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class StoryCommand(
    private val storyRegistryProvider: () -> StoryRegistry,
    private val reloadStories: () -> Unit,
    private val restartPlugin: () -> Boolean,
    private val currentStoryIdProvider: (Player) -> String?,
    private val questProgressProvider: (Player, String?) -> kr.kjh9211.story.story.StoryQuestProgress?,
    private val setCurrentStory: (Player, storyId: String) -> Boolean,
    private val moveCurrentStory: (Player, StoryMoveDirection) -> Boolean,
    private val setStoryEnabled: (storyId: String, enabled: Boolean) -> Boolean,
    private val setTriggerEnabled: (storyId: String, triggerId: String, enabled: Boolean) -> Boolean,
    private val setActionEnabled: (storyId: String, triggerId: String, actionId: String, enabled: Boolean) -> Boolean,
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender, label)
            return true
        }

        return when (args[0].lowercase()) {
            "list" -> handleStoryList(sender)
            "reload" -> handleReload(sender)
            "restart" -> handleRestart(sender)
            "progress" -> handleProgressCommand(sender, args.drop(1), label)
            "quest" -> handleQuestCommand(sender, args.drop(1), label)
            "enable" -> handleStoryToggle(sender, args.getOrNull(1), true)
            "disable" -> handleStoryToggle(sender, args.getOrNull(1), false)
            "trigger" -> handleTriggerCommand(sender, args.drop(1), label)
            "action" -> handleActionCommand(sender, args.drop(1), label)
            else -> {
                sendHelp(sender, label)
                true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> {
        val registry = try { storyRegistryProvider() } catch (e: Exception) { null }
        return when (args.size) {
            1 -> complete(args[0], "list", "reload", "restart", "progress", "quest", "enable", "disable", "trigger", "action")
            2 -> topLevelSecondArgs(registry, args)
            3 -> thirdArgs(registry, args)
            4 -> fourthArgs(registry, args)
            5 -> fifthArgs(registry, args)
            else -> mutableListOf()
        }
    }

    private fun topLevelSecondArgs(registry: StoryRegistry?, args: Array<out String>): MutableList<String> {
        if (registry == null) return mutableListOf()
        return when (args[0].lowercase()) {
            "enable" -> complete(args[1], registry.stories().filter { !it.enabled }.map { it.id })
            "disable" -> complete(args[1], registry.stories().filter { it.enabled }.map { it.id })
            "progress" -> complete(args[1], "info", "set", "next", "previous")
            "quest" -> complete(args[1], "info", "status")
            "trigger" -> complete(args[1], "list", "run", "enable", "disable")
            "action" -> complete(args[1], "list", "enable", "disable")
            else -> mutableListOf()
        }
    }

    private fun thirdArgs(registry: StoryRegistry?, args: Array<out String>): MutableList<String> {
        if (registry == null) return mutableListOf()
        return when (args[0].lowercase()) {
            "progress" -> if (args[1].equals("set", ignoreCase = true)) {
                complete(args[2], registry.storyIds())
            } else if (args[1].equals("info", ignoreCase = true) || args[1].equals("status", ignoreCase = true)) {
                complete(args[2], registry.storyIds())
            } else {
                mutableListOf()
            }
            "quest" -> if (args[1].equals("info", ignoreCase = true) || args[1].equals("status", ignoreCase = true)) {
                complete(args[2], registry.storyIds())
            } else {
                mutableListOf()
            }
            "trigger", "action" -> complete(args[2], registry.storyIds())
            else -> mutableListOf()
        }
    }

    private fun fourthArgs(registry: StoryRegistry?, args: Array<out String>): MutableList<String> {
        if (registry == null) return mutableListOf()
        return when (args[0].lowercase()) {
            "trigger" -> {
                val story = registry.findStory(args[2]) ?: return mutableListOf()
                val candidates = when (args[1].lowercase()) {
                    "enable" -> story.triggers.filter { !it.enabled }.map { it.id }
                    "disable", "run", "list" -> story.triggers.map { it.id }
                    else -> emptyList()
                }
                complete(args[3], candidates)
            }

            "action" -> {
                val story = registry.findStory(args[2]) ?: return mutableListOf()
                complete(args[3], story.triggers.map { it.id })
            }

            else -> mutableListOf()
        }
    }

    private fun fifthArgs(registry: StoryRegistry?, args: Array<out String>): MutableList<String> {
        if (registry == null || !args[0].equals("action", ignoreCase = true)) {
            return mutableListOf()
        }

        val story = registry.findStory(args[2]) ?: return mutableListOf()
        val trigger = story.findTrigger(args[3]) ?: return mutableListOf()
        val candidates = when (args[1].lowercase()) {
            "enable" -> trigger.actions.filter { !it.enabled }.map { it.id }
            "disable" -> trigger.actions.filter { it.enabled }.map { it.id }
            "list" -> trigger.actions.map { it.id }
            else -> emptyList()
        }
        return complete(args[4], candidates)
    }

    private fun complete(current: String, vararg values: String): MutableList<String> {
        return complete(current, values.asList())
    }

    private fun complete(current: String, values: List<String>): MutableList<String> {
        return values
            .distinct()
            .filter { it.startsWith(current, ignoreCase = true) }
            .sorted()
            .toMutableList()
    }

    private fun handleStoryList(sender: CommandSender): Boolean {
        sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Loaded stories")
        storyRegistryProvider().stories().forEach { story ->
            sender.sendMessage(
                "${ChatColor.GRAY}- ${statusColor(story.enabled)}${statusText(story.enabled)} " +
                    "${ChatColor.AQUA}${story.id}${ChatColor.WHITE} : ${story.title}",
            )
        }
        return true
    }

    private fun handleReload(sender: CommandSender): Boolean {
        reloadStories()
        sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} Reloaded story files")
        return true
    }

    private fun handleRestart(sender: CommandSender): Boolean {
        sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Restarting plugin...")
        val restarted = restartPlugin()
        if (restarted) {
            sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} Plugin restarted")
        } else {
            sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.RED} Plugin restart failed")
        }
        return true
    }

    private fun handleStoryToggle(sender: CommandSender, storyId: String?, enabled: Boolean): Boolean {
        if (storyId == null) {
            sender.sendMessage("${ChatColor.RED}Use /story ${if (enabled) "enable" else "disable"} <storyId>")
            return true
        }

        if (!setStoryEnabled(storyId, enabled)) {
            sender.sendMessage("${ChatColor.RED}Story not found: $storyId")
            return true
        }

        sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} ${toggleWord(enabled)} story $storyId")
        return true
    }

    private fun handleProgressCommand(sender: CommandSender, args: List<String>, label: String): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("${ChatColor.RED}Progress commands are only available to players.")
            return true
        }

        val registry = storyRegistryProvider()
        val currentStoryId = currentStoryIdProvider(player)
        return when (args.getOrNull(0)?.lowercase()) {
            null, "info" -> {
                val currentStory = registry.findStory(currentStoryId)
                val currentIndex = registry.progressIndex(currentStoryId)
                val total = registry.totalStories()
                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Progress")
                sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}Current: ${currentStory?.id ?: "none"}")
                sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}Title: ${currentStory?.title ?: "none"}")
                sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}Position: ${currentIndex ?: 0}/$total")
                sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}Previous: ${currentStory?.previousStoryId ?: "none"}")
                sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}Next: ${currentStory?.nextStoryId ?: "none"}")
                true
            }

            "set" -> {
                val storyId = args.getOrNull(1)
                if (storyId == null) {
                    sender.sendMessage("${ChatColor.RED}/$label progress set <storyId>")
                    return true
                }
                if (!setCurrentStory(player, storyId)) {
                    sender.sendMessage("${ChatColor.RED}Story not found: $storyId")
                    return true
                }
                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} Current story set to $storyId")
                true
            }

            "next" -> {
                if (!moveCurrentStory(player, StoryMoveDirection.NEXT)) {
                    sender.sendMessage("${ChatColor.RED}No next story available")
                    return true
                }
                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} Moved to next story")
                true
            }

            "previous" -> {
                if (!moveCurrentStory(player, StoryMoveDirection.PREVIOUS)) {
                    sender.sendMessage("${ChatColor.RED}No previous story available")
                    return true
                }
                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} Moved to previous story")
                true
            }

            else -> {
                sender.sendMessage("${ChatColor.RED}/$label progress <info|set|next|previous>")
                true
            }
        }
    }

    private fun handleQuestCommand(sender: CommandSender, args: List<String>, label: String): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("${ChatColor.RED}Quest commands are only available to players.")
            return true
        }

        val storyId = args.getOrNull(1) ?: currentStoryIdProvider(player)
        val progress = questProgressProvider(player, storyId)
        if (progress == null) {
            sender.sendMessage("${ChatColor.RED}No internal quest found for story: ${storyId ?: "current"}")
            return true
        }

        sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Quest status for ${progress.story.id}")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}Title: ${progress.story.title}")
        sender.sendMessage(
            "${ChatColor.GRAY}- ${ChatColor.WHITE}Objectives: ${progress.completedObjectives}/${progress.totalObjectives}",
        )
        sender.sendMessage(
            "${ChatColor.GRAY}- ${ChatColor.WHITE}State: ${if (progress.completed) "COMPLETED" else "IN_PROGRESS"}",
        )
        progress.objectives.forEach { objective ->
            val stateColor = if (objective.completed) ChatColor.GREEN else ChatColor.YELLOW
            sender.sendMessage(
                "${ChatColor.GRAY}- ${stateColor}${objective.current}/${objective.objective.target} " +
                    "${ChatColor.WHITE}${objective.objective.title}",
            )
            if (objective.objective.description.isNotBlank()) {
                sender.sendMessage("${ChatColor.DARK_GRAY}  ${objective.objective.description}")
            }
        }
        return true
    }

    private fun handleTriggerCommand(sender: CommandSender, args: List<String>, label: String): Boolean {
        if (args.isEmpty()) {
            sendTriggerHelp(sender, label)
            return true
        }

        return when (args[0].lowercase()) {
            "list" -> {
                val story = storyRegistryProvider().findStory(args.getOrNull(1))
                if (story == null) {
                    sender.sendMessage("${ChatColor.RED}/$label trigger list <storyId>")
                    return true
                }

                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Triggers for ${story.id}")
                story.triggers.forEach { sender.sendMessage(triggerLine(it)) }
                true
            }

            "run" -> {
                val story = storyRegistryProvider().findStory(args.getOrNull(1))
                val trigger = story?.findTrigger(args.getOrNull(2))
                if (story == null || trigger == null) {
                    sender.sendMessage("${ChatColor.RED}/$label trigger run <storyId> <triggerId>")
                    return true
                }

                if (!story.enabled) {
                    sender.sendMessage("${ChatColor.RED}Story is disabled: ${story.id}")
                    return true
                }

                if (!trigger.enabled) {
                    sender.sendMessage("${ChatColor.RED}Trigger is disabled: ${trigger.id}")
                    return true
                }

                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} Running ${story.id}/${trigger.id}")
                trigger.execute(StoryExecutionContext(sender, mapOf("player" to sender.name)))
                true
            }

            "enable", "disable" -> {
                val storyId = args.getOrNull(1)
                val triggerId = args.getOrNull(2)
                if (storyId == null || triggerId == null) {
                    sender.sendMessage("${ChatColor.RED}/$label trigger ${args[0].lowercase()} <storyId> <triggerId>")
                    return true
                }

                val enabled = args[0].equals("enable", ignoreCase = true)
                if (!setTriggerEnabled(storyId, triggerId, enabled)) {
                    sender.sendMessage("${ChatColor.RED}Trigger not found: $storyId/$triggerId")
                    return true
                }

                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} ${toggleWord(enabled)} trigger $storyId/$triggerId")
                true
            }

            else -> {
                sendTriggerHelp(sender, label)
                true
            }
        }
    }

    private fun handleActionCommand(sender: CommandSender, args: List<String>, label: String): Boolean {
        if (args.isEmpty()) {
            sendActionHelp(sender, label)
            return true
        }

        return when (args[0].lowercase()) {
            "list" -> {
                val story = storyRegistryProvider().findStory(args.getOrNull(1))
                val trigger = story?.findTrigger(args.getOrNull(2))
                if (story == null || trigger == null) {
                    sender.sendMessage("${ChatColor.RED}/$label action list <storyId> <triggerId>")
                    return true
                }

                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Actions for ${story.id}/${trigger.id}")
                trigger.actions.forEach { sender.sendMessage(actionLine(it)) }
                true
            }

            "enable", "disable" -> {
                val storyId = args.getOrNull(1)
                val triggerId = args.getOrNull(2)
                val actionId = args.getOrNull(3)
                if (storyId == null || triggerId == null || actionId == null) {
                    sender.sendMessage("${ChatColor.RED}/$label action ${args[0].lowercase()} <storyId> <triggerId> <actionId>")
                    return true
                }

                val enabled = args[0].equals("enable", ignoreCase = true)
                if (!setActionEnabled(storyId, triggerId, actionId, enabled)) {
                    sender.sendMessage("${ChatColor.RED}Action not found: $storyId/$triggerId/$actionId")
                    return true
                }

                sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.GREEN} ${toggleWord(enabled)} action $storyId/$triggerId/$actionId")
                true
            }

            else -> {
                sendActionHelp(sender, label)
                true
            }
        }
    }

    private fun sendHelp(sender: CommandSender, label: String) {
        sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Commands")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label list")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label reload")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label restart")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label progress <info|set|next|previous>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label quest <info|status> [storyId]")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label enable <storyId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label disable <storyId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label trigger list <storyId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label trigger run <storyId> <triggerId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label trigger enable <storyId> <triggerId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label trigger disable <storyId> <triggerId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label action list <storyId> <triggerId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label action enable <storyId> <triggerId> <actionId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label action disable <storyId> <triggerId> <actionId>")
    }

    private fun sendTriggerHelp(sender: CommandSender, label: String) {
        sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Trigger commands")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label trigger list <storyId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label trigger run <storyId> <triggerId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label trigger enable <storyId> <triggerId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label trigger disable <storyId> <triggerId>")
    }

    private fun sendActionHelp(sender: CommandSender, label: String) {
        sender.sendMessage("${ChatColor.GOLD}[Story]${ChatColor.YELLOW} Action commands")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label action list <storyId> <triggerId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label action enable <storyId> <triggerId> <actionId>")
        sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}/$label action disable <storyId> <triggerId> <actionId>")
    }

    private fun triggerLine(trigger: StoryTrigger): String {
        return "${ChatColor.GRAY}- ${statusColor(trigger.enabled)}${statusText(trigger.enabled)} " +
            "${ChatColor.AQUA}${trigger.id}${ChatColor.WHITE} (${trigger.event})"
    }

    private fun actionLine(action: StoryAction): String {
        return "${ChatColor.GRAY}- ${statusColor(action.enabled)}${statusText(action.enabled)} " +
            "${ChatColor.AQUA}${action.id}${ChatColor.WHITE} (${action.type.name})"
    }

    private fun statusText(enabled: Boolean): String = if (enabled) "[ON]" else "[OFF]"

    private fun statusColor(enabled: Boolean): ChatColor = if (enabled) ChatColor.GREEN else ChatColor.RED

    private fun toggleWord(enabled: Boolean): String = if (enabled) "Enabled" else "Disabled"
}
