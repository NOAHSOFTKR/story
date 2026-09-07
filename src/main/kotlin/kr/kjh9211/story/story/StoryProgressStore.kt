package kr.kjh9211.story.story

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.util.UUID

class StoryProgressStore(
    private val plugin: JavaPlugin,
) {
    private val file: File
        get() = File(plugin.dataFolder, "progress.yml")

    /**
     * Applies all state changes caused by one trigger to a single YAML document and writes it once.
     * A false result means callers must not perform player-visible actions or rewards.
     */
    fun update(playerUuid: UUID, change: (YamlConfiguration) -> Unit): Boolean {
        val yaml = try {
            load()
        } catch (exception: Exception) {
            plugin.logger.severe("Failed to load story progress for $playerUuid: ${exception.message}")
            return false
        }

        return try {
            change(yaml)
            file.parentFile?.mkdirs()
            yaml.save(file)
            true
        } catch (exception: IOException) {
            plugin.logger.severe("Failed to save story progress for $playerUuid: ${exception.message}")
            false
        } catch (exception: Exception) {
            plugin.logger.severe("Failed to update story progress for $playerUuid: ${exception.message}")
            false
        }
    }

    fun currentStoryId(playerUuid: UUID): String? {
        if (!file.exists()) {
            return null
        }
        return load().getString(path(playerUuid, "currentStoryId"))?.takeIf { it.isNotBlank() }
    }

    fun setCurrentStoryId(playerUuid: UUID, storyId: String?): Boolean {
        return update(playerUuid) { yaml ->
            yaml.set(path(playerUuid, "currentStoryId"), storyId)
        }
    }

    fun objectiveProgress(playerUuid: UUID, storyId: String, objectiveId: String): Int {
        if (!file.exists()) {
            return 0
        }
        return load().getInt(path(playerUuid, "quests.$storyId.objectives.$objectiveId"), 0)
    }

    fun setObjectiveProgress(playerUuid: UUID, storyId: String, objectiveId: String, value: Int): Boolean {
        return update(playerUuid) { yaml ->
            yaml.set(path(playerUuid, "quests.$storyId.objectives.$objectiveId"), value)
        }
    }

    fun isStoryCompleted(playerUuid: UUID, storyId: String): Boolean {
        if (!file.exists()) {
            return false
        }
        return load().getBoolean(path(playerUuid, "completedStories.$storyId"), false)
    }

    fun setStoryCompleted(playerUuid: UUID, storyId: String, completed: Boolean): Boolean {
        return update(playerUuid) { yaml ->
            yaml.set(path(playerUuid, "completedStories.$storyId"), completed)
        }
    }

    fun hasFlag(playerUuid: UUID, flag: String): Boolean {
        if (!file.exists()) {
            return false
        }
        return load().getBoolean(path(playerUuid, "flags.$flag"), false)
    }

    fun isTriggerExecuted(playerUuid: UUID, storyId: String, triggerId: String): Boolean {
        if (!file.exists()) {
            return false
        }
        return load().getBoolean(path(playerUuid, "completedTriggers.$storyId.$triggerId"), false)
    }

    fun commitTrigger(
        playerUuid: UUID,
        storyId: String,
        triggerId: String,
        currentStoryId: String,
        objectiveId: String?,
        objectiveProgress: Int?,
        completedStory: Boolean,
        nextStoryId: String?,
        completionFlags: Set<String>,
        repeatable: Boolean,
    ): Boolean {
        return update(playerUuid) { yaml ->
            val playerPath = "players.$playerUuid"
            if (objectiveId != null && objectiveProgress != null) {
                yaml.set("$playerPath.quests.$storyId.objectives.$objectiveId", objectiveProgress)
            }
            if (!repeatable) {
                yaml.set("$playerPath.completedTriggers.$storyId.$triggerId", true)
            }
            if (completedStory) {
                yaml.set("$playerPath.completedStories.$storyId", true)
                completionFlags.forEach { flag -> yaml.set("$playerPath.flags.$flag", true) }
                yaml.set("$playerPath.currentStoryId", nextStoryId)
            } else if (yaml.getString("$playerPath.currentStoryId").isNullOrBlank()) {
                yaml.set("$playerPath.currentStoryId", currentStoryId)
            }
        }
    }

    private fun load(): YamlConfiguration = YamlConfiguration.loadConfiguration(file)

    private fun path(playerUuid: UUID, suffix: String): String = "players.$playerUuid.$suffix"
}
