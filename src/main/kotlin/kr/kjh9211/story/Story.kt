package kr.kjh9211.story

import kr.kjh9211.story.command.StoryCommand
import kr.kjh9211.story.event.BukkitEventBridge
import kr.kjh9211.story.event.ItemsAdderEventBridge
import kr.kjh9211.story.event.PluginEventBridge
import kr.kjh9211.story.placeholder.StoryPlaceholderBridge
import kr.kjh9211.story.story.StoryConfigStore
import kr.kjh9211.story.story.StoryProgressStore
import kr.kjh9211.story.story.StoryRegistry
import kr.kjh9211.story.story.StoryRuntime
import kr.kjh9211.story.towny.TownyEventBridge
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class Story : JavaPlugin() {

    private lateinit var storyRegistry: StoryRegistry
    private lateinit var storyConfigStore: StoryConfigStore
    private lateinit var placeholderBridge: StoryPlaceholderBridge
    private lateinit var storyRuntime: StoryRuntime
    private lateinit var townyEventBridge: TownyEventBridge
    private lateinit var storyProgressStore: StoryProgressStore
    private lateinit var bukkitEventBridge: BukkitEventBridge
    private lateinit var itemsAdderEventBridge: ItemsAdderEventBridge
    private lateinit var pluginEventBridge: PluginEventBridge

    override fun onEnable() {
        saveResource("stories/example.yml", false)

        storyConfigStore = StoryConfigStore(this)
        storyProgressStore = StoryProgressStore(this)
        storyProgressStore.load()

        // Initialize storyRegistry first to avoid UninitializedPropertyAccessException in bridges
        storyRegistry = storyConfigStore.loadAll()

        storyRuntime = StoryRuntime(
            storyRegistryProvider = { storyRegistry },
            progressStore = storyProgressStore,
            currentStoryIdProvider = { uuid -> storyProgressStore.currentStoryId(uuid) },
            setCurrentStoryId = { uuid, storyId -> storyProgressStore.setCurrentStoryId(uuid, storyId) },
        )
        placeholderBridge = StoryPlaceholderBridge(this, { storyRegistry }, storyProgressStore)
        townyEventBridge = TownyEventBridge(this, storyRuntime)
        bukkitEventBridge = BukkitEventBridge(this, storyRuntime)
        itemsAdderEventBridge = ItemsAdderEventBridge(this, storyRuntime)
        pluginEventBridge = PluginEventBridge(this, storyRuntime)

        val storyCommand = StoryCommand(
            storyRegistryProvider = { storyRegistry },
            reloadStories = ::reloadStories,
            restartPlugin = ::restartPlugin,
            currentStoryIdProvider = { player -> storyProgressStore.currentStoryId(player.uniqueId) },
            questProgressProvider = { player, storyId -> storyRuntime.questProgress(player, storyId) },
            setCurrentStory = ::setCurrentStory,
            moveCurrentStory = ::moveCurrentStory,
            setStoryEnabled = { storyId, enabled -> setStoryEnabled(storyId, enabled) },
            setTriggerEnabled = { storyId, triggerId, enabled -> setTriggerEnabled(storyId, triggerId, enabled) },
            setActionEnabled = { storyId, triggerId, actionId, enabled ->
                setActionEnabled(storyId, triggerId, actionId, enabled)
            },
        )
        getCommand("story")?.apply {
            setExecutor(storyCommand)
            tabCompleter = storyCommand
        } ?: logger.warning("Command 'story' is missing from plugin.yml")

        placeholderBridge.registerIfAvailable()
        bukkitEventBridge.register()
        itemsAdderEventBridge.registerIfAvailable()
        pluginEventBridge.register()
        townyEventBridge.registerIfAvailable()

        logger.info("Loaded ${storyRegistry.stories().size} stories.")
    }

    override fun onDisable() {
        if (::storyProgressStore.isInitialized) {
            storyProgressStore.save()
        }
        if (::placeholderBridge.isInitialized) {
            placeholderBridge.close()
        }
    }

    private fun reloadStories() {
        storyRegistry = storyConfigStore.loadAll()
        placeholderBridge.refresh()
    }

    fun restartPlugin(): Boolean {
        return try {
            onDisable()
            onEnable()
            true
        } catch (exception: Exception) {
            logger.severe("Failed to restart plugin: ${exception.message}")
            exception.printStackTrace()
            false
        }
    }

    private fun ensureCurrentStory() {
        // Per-player progress is initialized lazily when a player uses the plugin.
    }

    private fun setCurrentStory(player: Player, storyId: String): Boolean {
        val story = storyRegistry.findStory(storyId) ?: return false
        storyProgressStore.setCurrentStoryId(player.uniqueId, story.id)
        placeholderBridge.refresh()
        return true
    }

    private fun moveCurrentStory(player: Player, direction: StoryMoveDirection): Boolean {
        val currentId = storyProgressStore.currentStoryId(player.uniqueId)
            ?: storyRegistry.orderedStories().firstOrNull()?.id
            ?: return false
        val target = when (direction) {
            StoryMoveDirection.NEXT -> storyRegistry.nextStory(currentId)
            StoryMoveDirection.PREVIOUS -> storyRegistry.previousStory(currentId)
        } ?: return false

        storyProgressStore.setCurrentStoryId(player.uniqueId, target.id)
        placeholderBridge.refresh()
        return true
    }

    private fun setStoryEnabled(storyId: String, enabled: Boolean): Boolean {
        val updated = storyConfigStore.setStoryEnabled(storyId, enabled)
        if (updated) {
            reloadStories()
        }
        return updated
    }

    private fun setTriggerEnabled(storyId: String, triggerId: String, enabled: Boolean): Boolean {
        val updated = storyConfigStore.setTriggerEnabled(storyId, triggerId, enabled)
        if (updated) {
            reloadStories()
        }
        return updated
    }

    private fun setActionEnabled(storyId: String, triggerId: String, actionId: String, enabled: Boolean): Boolean {
        val updated = storyConfigStore.setActionEnabled(storyId, triggerId, actionId, enabled)
        if (updated) {
            reloadStories()
        }
        return updated
    }
}

enum class StoryMoveDirection {
    NEXT,
    PREVIOUS,
}
