package kr.kjh9211.story.story

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class StoryProgressStore(
    private val plugin: JavaPlugin,
) {
    private val file: File
        get() = File(plugin.dataFolder, "progress.yml")

    fun currentStoryId(playerUuid: UUID): String? {
        if (!file.exists()) {
            return null
        }
        return YamlConfiguration.loadConfiguration(file)
            .getString("players.${playerUuid}.currentStoryId")
            ?.takeIf { it.isNotBlank() }
    }

    fun setCurrentStoryId(playerUuid: UUID, storyId: String?) {
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.set("players.${playerUuid}.currentStoryId", storyId)
        yaml.save(file)
    }

    fun objectiveProgress(playerUuid: UUID, storyId: String, objectiveId: String): Int {
        if (!file.exists()) {
            return 0
        }
        return YamlConfiguration.loadConfiguration(file)
            .getInt("players.${playerUuid}.quests.$storyId.objectives.$objectiveId", 0)
    }

    fun setObjectiveProgress(playerUuid: UUID, storyId: String, objectiveId: String, value: Int) {
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.set("players.${playerUuid}.quests.$storyId.objectives.$objectiveId", value)
        yaml.save(file)
    }

    fun isStoryCompleted(playerUuid: UUID, storyId: String): Boolean {
        if (!file.exists()) {
            return false
        }
        return YamlConfiguration.loadConfiguration(file)
            .getBoolean("players.${playerUuid}.completedStories.$storyId", false)
    }

    fun setStoryCompleted(playerUuid: UUID, storyId: String, completed: Boolean) {
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.set("players.${playerUuid}.completedStories.$storyId", completed)
        yaml.save(file)
    }
}
