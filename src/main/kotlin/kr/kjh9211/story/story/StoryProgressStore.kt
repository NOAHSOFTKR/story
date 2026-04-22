package kr.kjh9211.story.story

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StoryProgressStore(
    private val plugin: JavaPlugin,
) {
    private val file: File
        get() = File(plugin.dataFolder, "progress.yml")

    private val playerCurrentStory = ConcurrentHashMap<UUID, String>()
    private val playerObjectiveProgress = ConcurrentHashMap<UUID, MutableMap<String, MutableMap<String, Int>>>()
    private val playerCompletedStories = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun load() {
        if (!file.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        val playersSection = yaml.getConfigurationSection("players") ?: return

        playersSection.getKeys(false).forEach { uuidStr ->
            val uuid = try { UUID.fromString(uuidStr) } catch (e: Exception) { return@forEach }
            val section = playersSection.getConfigurationSection(uuidStr) ?: return@forEach

            section.getString("currentStoryId")?.let { playerCurrentStory[uuid] = it }

            section.getConfigurationSection("quests")?.getKeys(false)?.forEach { storyId ->
                val storySection = section.getConfigurationSection("quests.$storyId.objectives") ?: return@forEach
                val objectives = ConcurrentHashMap<String, Int>()
                storySection.getKeys(false).forEach { objId ->
                    objectives[objId] = storySection.getInt(objId)
                }
                playerObjectiveProgress.computeIfAbsent(uuid) { ConcurrentHashMap() }[storyId] = objectives
            }

            section.getStringList("completedStories").forEach { storyId ->
                playerCompletedStories.computeIfAbsent(uuid) { ConcurrentHashSet() }.add(storyId)
            }
        }
    }

    fun save() {
        val yaml = YamlConfiguration()
        playerCurrentStory.forEach { (uuid, storyId) ->
            yaml.set("players.$uuid.currentStoryId", storyId)
        }
        playerObjectiveProgress.forEach { (uuid, stories) ->
            stories.forEach { (storyId, objectives) ->
                objectives.forEach { (objId, value) ->
                    yaml.set("players.$uuid.quests.$storyId.objectives.$objId", value)
                }
            }
        }
        playerCompletedStories.forEach { (uuid, completed) ->
            yaml.set("players.$uuid.completedStories", completed.toList())
        }
        yaml.save(file)
    }

    fun currentStoryId(playerUuid: UUID): String? {
        return playerCurrentStory[playerUuid]
    }

    fun setCurrentStoryId(playerUuid: UUID, storyId: String?) {
        if (storyId == null) {
            playerCurrentStory.remove(playerUuid)
        } else {
            playerCurrentStory[playerUuid] = storyId
        }
    }

    fun objectiveProgress(playerUuid: UUID, storyId: String, objectiveId: String): Int {
        return playerObjectiveProgress[playerUuid]?.get(storyId)?.get(objectiveId) ?: 0
    }

    fun setObjectiveProgress(playerUuid: UUID, storyId: String, objectiveId: String, value: Int) {
        playerObjectiveProgress.computeIfAbsent(playerUuid) { ConcurrentHashMap() }
            .computeIfAbsent(storyId) { ConcurrentHashMap() }[objectiveId] = value
    }

    fun isStoryCompleted(playerUuid: UUID, storyId: String): Boolean {
        return playerCompletedStories[playerUuid]?.contains(storyId) ?: false
    }

    fun setStoryCompleted(playerUuid: UUID, storyId: String, completed: Boolean) {
        if (completed) {
            playerCompletedStories.computeIfAbsent(playerUuid) { ConcurrentHashSet() }.add(storyId)
        } else {
            playerCompletedStories[playerUuid]?.remove(storyId)
        }
    }

    private class ConcurrentHashSet<E> : MutableSet<E> {
        private val map = ConcurrentHashMap<E, Any>()
        private val dummy = Any()
        override val size: Int get() = map.size
        override fun contains(element: E): Boolean = map.containsKey(element)
        override fun containsAll(elements: Collection<E>): Boolean = elements.all { contains(it) }
        override fun isEmpty(): Boolean = map.isEmpty()
        override fun iterator(): MutableIterator<E> = map.keys.iterator()
        override fun add(element: E): Boolean = map.put(element, dummy) == null
        override fun addAll(elements: Collection<E>): Boolean {
            var changed = false
            for (e in elements) if (add(e)) changed = true
            return changed
        }
        override fun clear() = map.clear()
        override fun remove(element: E): Boolean = map.remove(element) != null
        override fun removeAll(elements: Collection<E>): Boolean {
            var changed = false
            for (e in elements) if (remove(e)) changed = true
            return changed
        }
        override fun retainAll(elements: Collection<E>): Boolean {
            val toRemove = map.keys.filter { !elements.contains(it) }
            toRemove.forEach { map.remove(it) }
            return toRemove.isNotEmpty()
        }
    }
}
