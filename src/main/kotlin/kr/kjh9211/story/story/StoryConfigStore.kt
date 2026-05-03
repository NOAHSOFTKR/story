package kr.kjh9211.story.story

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class StoryConfigStore(
    private val plugin: JavaPlugin,
) {
    fun storiesDirectory(): File = File(plugin.dataFolder, "stories")

    fun loadAll(): StoryRegistry {
        val directory = storiesDirectory()
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val ids = linkedSetOf<String>()
        val stories = directory.walkTopDown()
            .filter { it.isFile && (it.extension.equals("yml", true) || it.extension.equals("yaml", true)) }
            .sortedBy { it.absolutePath }
            .mapNotNull { loadStoryFromFile(it, ids) }
            .toList()

        return StoryRegistry(stories)
    }

    fun setStoryEnabled(storyId: String, enabled: Boolean): Boolean {
        val story = loadAll().findStory(storyId) ?: return false
        val yaml = YamlConfiguration.loadConfiguration(story.sourceFile)
        yaml.set("enable", enabled)
        yaml.save(story.sourceFile)
        return true
    }

    fun setTriggerEnabled(storyId: String, triggerId: String, enabled: Boolean): Boolean {
        val story = loadAll().findStory(storyId) ?: return false
        if (story.findTrigger(triggerId) == null) {
            return false
        }

        val yaml = YamlConfiguration.loadConfiguration(story.sourceFile)
        val triggerSection = yaml.getConfigurationSection("trigger")
        if (triggerSection != null) {
            val singleTriggerId = triggerSection.getString("id")?.takeIf { it.isNotBlank() }
                ?: triggerSection.getString("event")?.substringBefore("#")?.trim()
            if (singleTriggerId.equals(triggerId, ignoreCase = true)) {
                yaml.set("trigger.enable", enabled)
                yaml.save(story.sourceFile)
                return true
            }
        }

        val triggers = yaml.getMapList("triggers").map { it.toMutableMap() }.toMutableList()
        val target = triggers.firstOrNull {
            val id = it["id"]?.toString()?.takeIf { value -> value.isNotBlank() }
                ?: it["event"]?.toString()?.substringBefore("#")?.trim()
            id.equals(triggerId, ignoreCase = true)
        } ?: return false
        target["enable"] = enabled
        yaml.set("triggers", triggers)
        yaml.save(story.sourceFile)
        return true
    }

    fun setActionEnabled(storyId: String, triggerId: String, actionId: String, enabled: Boolean): Boolean {
        val story = loadAll().findStory(storyId) ?: return false
        if (story.findTrigger(triggerId) == null) {
            return false
        }

        val yaml = YamlConfiguration.loadConfiguration(story.sourceFile)
        val updated = updateActionEnabled(yaml, "trigger", triggerId, actionId, enabled)
            || updateActionEnabled(yaml, "triggers", triggerId, actionId, enabled)
        if (updated) {
            yaml.save(story.sourceFile)
        }
        return updated
    }

    private fun updateActionEnabled(
        yaml: YamlConfiguration,
        path: String,
        triggerId: String,
        actionId: String,
        enabled: Boolean,
    ): Boolean {
        if (path == "trigger") {
            val triggerSection = yaml.getConfigurationSection("trigger") ?: return false
            val id = triggerSection.getString("id")?.takeIf { it.isNotBlank() }
                ?: triggerSection.getString("event")?.substringBefore("#")?.trim()
            if (!id.equals(triggerId, ignoreCase = true)) {
                return false
            }
            return updateActionSection(triggerSection, actionId, enabled)
        }

        val triggers = yaml.getMapList("triggers").map { it.toMutableMap() }.toMutableList()
        val index = triggers.indexOfFirst {
            val id = it["id"]?.toString()?.takeIf { value -> value.isNotBlank() }
                ?: it["event"]?.toString()?.substringBefore("#")?.trim()
            id.equals(triggerId, ignoreCase = true)
        }
        if (index < 0) {
            return false
        }

        val section = YamlConfiguration()
        triggers[index].forEach { (key, value) -> section.set(key.toString(), value) }
        val updated = updateActionSection(section, actionId, enabled)
        if (!updated) {
            return false
        }

        val replaced = linkedMapOf<String, Any?>()
        section.getKeys(false).forEach { key -> replaced[key] = section.get(key) }
        triggers[index] = replaced.toMutableMap()
        yaml.set("triggers", triggers)
        return true
    }

    private fun updateActionSection(
        triggerSection: org.bukkit.configuration.ConfigurationSection,
        actionId: String,
        enabled: Boolean,
    ): Boolean {
        val actionsSection = triggerSection.getConfigurationSection("actions") ?: return false
        if (actionId.equals("commands", ignoreCase = true)) {
            actionsSection.set("commandsEnable", enabled)
            return true
        }

        actionsSection.getKeys(false)
            .filter { key -> actionsSection.isConfigurationSection(key) }
            .forEach { key ->
                val section = actionsSection.getConfigurationSection(key) ?: return@forEach
                val candidateId = section.getString("id")?.takeIf { it.isNotBlank() } ?: key
                if (candidateId.equals(actionId, ignoreCase = true)) {
                    section.set("enable", enabled)
                    return true
                }
            }
        return false
    }

    private fun loadStoryFromFile(file: File, ids: MutableSet<String>): StoryDefinition? {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val id = yaml.getString("id")?.takeIf { it.isNotBlank() } ?: return null
        if (!ids.add(id.lowercase())) {
            plugin.logger.warning("Duplicate story id '$id' in ${file.absolutePath}; skipping duplicate.")
            return null
        }

        return StoryDefinition(
            id = id,
            title = yaml.getString("name")?.ifBlank { id } ?: id,
            enabled = yaml.getBoolean("enable", true),
            previousStoryId = yaml.getString("previousStory")?.takeIf { it.isNotBlank() },
            nextStoryId = yaml.getString("nextStory")?.takeIf { it.isNotBlank() },
            quest = loadQuest(yaml),
            triggers = loadTriggers(yaml, id),
            sourceFile = file,
        )
    }

    private fun loadQuest(yaml: YamlConfiguration): StoryQuest? {
        val questSection = yaml.getConfigurationSection("quest") ?: return null
        val objectives = questSection.getMapList("objectives").mapNotNull { raw ->
            val id = raw["id"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StoryObjective(
                id = id,
                title = raw["name"]?.toString()?.ifBlank { id } ?: id,
                description = raw["description"]?.toString().orEmpty(),
                target = (raw["target"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1,
            )
        }
        return StoryQuest(
            enabled = questSection.getBoolean("enable", true),
            title = questSection.getString("name")?.ifBlank { "Quest" } ?: "Quest",
            objectives = objectives,
        )
    }

    private fun loadTriggers(yaml: YamlConfiguration, storyId: String): List<StoryTrigger> {
        val triggerSections = mutableListOf<org.bukkit.configuration.ConfigurationSection>()
        yaml.getConfigurationSection("trigger")?.let { triggerSections += it }
        triggerSections += yaml.getMapList("triggers").mapNotNull { raw ->
            if (raw.isEmpty()) {
                return@mapNotNull null
            }
            val section = YamlConfiguration()
            raw.forEach { (key, value) -> section.set(key.toString(), value) }
            section
        }

        return triggerSections.mapNotNull { loadTrigger(it, storyId) }
    }

    private fun loadTrigger(triggerSection: org.bukkit.configuration.ConfigurationSection, storyId: String): StoryTrigger? {
        val event = triggerSection.getString("event")
            ?.substringBefore("#")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val triggerId = triggerSection.getString("id")?.takeIf { it.isNotBlank() } ?: event

        return StoryTrigger(
            id = triggerId,
            type = StoryTriggerType.fromConfig(triggerSection.getString("type")),
            enabled = triggerSection.getBoolean("enable", true),
            title = triggerSection.getString("name")?.ifBlank { triggerId } ?: triggerId,
            description = triggerSection.getString("description").orEmpty(),
            event = event,
            objectiveId = triggerSection.getString("objectiveId")?.takeIf { it.isNotBlank() },
            progressAmount = triggerSection.getInt("amount", 1).coerceAtLeast(1),
            actions = loadActions(triggerSection, storyId, triggerId),
            blockFilter = triggerSection.getString("block")?.takeIf { it.isNotBlank() },
        )
    }

    private fun loadActions(
        triggerSection: org.bukkit.configuration.ConfigurationSection,
        storyId: String,
        triggerId: String,
    ): List<StoryAction> {
        val actions = mutableListOf<StoryAction>()
        val actionsSection = triggerSection.getConfigurationSection("actions")
            ?: triggerSection.root?.getConfigurationSection("actions")
            ?: return actions

        actionsSection.getKeys(false)
            .filter { key -> actionsSection.isConfigurationSection(key) }
            .forEach { key ->
                val section = actionsSection.getConfigurationSection(key) ?: return@forEach
                if (!section.contains("content")) {
                    return@forEach
                }

                actions += StoryAction(
                    id = section.getString("id")?.takeIf { it.isNotBlank() } ?: key,
                    type = ActionType.MESSAGE,
                    enabled = section.getBoolean("enable", true),
                    message = section.getString("content").orEmpty(),
                    messageTarget = section.getString("toSend", "triggeredPlayer"),
                )
            }

        val commandSections = listOf("commands", "command")
        val commandList = commandSections
            .asSequence()
            .map { key -> actionsSection.getStringList(key) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

        if (commandList.isNotEmpty()) {
            actions += StoryAction(
                id = "commands",
                type = ActionType.COMMANDS,
                enabled = actionsSection.getBoolean("commandsEnable", true),
                commands = commandList,
            )
        }

        if (actions.isEmpty()) {
            plugin.logger.warning("No actions found for story '$storyId' trigger '$triggerId'.")
        }

        return actions
    }
}
