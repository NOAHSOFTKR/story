package kr.kjh9211.story

import kr.kjh9211.story.command.StoryCommand
import kr.kjh9211.story.event.BukkitEventBridge
import kr.kjh9211.story.event.ItemsAdderEventBridge
import kr.kjh9211.story.event.PluginEventBridge
import kr.kjh9211.story.event.QuestEventBridge
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
    private lateinit var questEventBridge: QuestEventBridge

    override fun onEnable() {
        extractDefaultStories()

        storyConfigStore = StoryConfigStore(this)
        storyProgressStore = StoryProgressStore(this)
        storyRuntime = StoryRuntime(
            storyRegistryProvider = { storyRegistry },
            progressStore = storyProgressStore,
            currentStoryIdProvider = { uuid -> storyProgressStore.currentStoryId(uuid) },
        )
        placeholderBridge = StoryPlaceholderBridge(this, { storyRegistry }, storyProgressStore)
        townyEventBridge = TownyEventBridge(this, storyRuntime)
        bukkitEventBridge = BukkitEventBridge(this, storyRuntime)
        itemsAdderEventBridge = ItemsAdderEventBridge(this, storyRuntime)
        pluginEventBridge = PluginEventBridge(this, storyRuntime)
        questEventBridge = QuestEventBridge(this, storyRuntime)
        reloadStories()

        val storyCommand = StoryCommand(
            storyRegistryProvider = { storyRegistry },
            reloadStories = ::reloadStories,
            restartPlugin = ::restartPlugin,
            currentStoryIdProvider = { player -> storyProgressStore.currentStoryId(player.uniqueId) },
            questProgressProvider = { player, storyId -> storyRuntime.questProgress(player, storyId) },
            runManualTrigger = { storyId, triggerId, context -> storyRuntime.runManualTrigger(storyId, triggerId, context) },
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
        if (Bukkit.getPluginManager().getPlugin("CutThin") == null) {
            logger.warning("CutThin not found; cutscene_fire and cutscene_end story triggers are unavailable.")
        }
        questEventBridge.registerIfAvailable()
        townyEventBridge.registerIfAvailable()
    }

    override fun onDisable() {
        if (::placeholderBridge.isInitialized) {
            placeholderBridge.close()
        }
    }

    private fun reloadStories() {
        storyRegistry = storyConfigStore.loadAll()
        ensureCurrentStory()
        logger.info("Loaded ${storyRegistry.stories().size} stories from ${storyConfigStore.storiesDirectory().absolutePath}")
        placeholderBridge.refresh()
    }

    private fun extractDefaultStories() {
        listOf(
            "stories/example.yml",
            "stories/pending/arcana-chapter-1.yml",
            "stories/pending/arcana-chapter-2.yml",
        ).forEach { resource -> saveResource(resource, false) }
    }

    fun restartPlugin(): Boolean {
        val pluginManager = Bukkit.getPluginManager()
        return try {
            pluginManager.disablePlugin(this)
            pluginManager.enablePlugin(this)
            true
        } catch (exception: Exception) {
            logger.severe("Failed to restart plugin: ${exception.message}")
            false
        }
    }

    private fun ensureCurrentStory() {
        // Per-player progress is initialized lazily when a player uses the plugin.
    }

    private fun setCurrentStory(player: Player, storyId: String): Boolean {
        val story = storyRegistry.findStory(storyId) ?: return false
        val updated = storyProgressStore.setCurrentStoryId(player.uniqueId, story.id)
        if (updated) {
            placeholderBridge.refresh()
        }
        return updated
    }

    private fun moveCurrentStory(player: Player, direction: StoryMoveDirection): Boolean {
        val currentId = storyProgressStore.currentStoryId(player.uniqueId)
            ?: storyRegistry.orderedStories().firstOrNull()?.id
            ?: return false
        val target = when (direction) {
            StoryMoveDirection.NEXT -> storyRegistry.nextStory(currentId)
            StoryMoveDirection.PREVIOUS -> storyRegistry.previousStory(currentId)
        } ?: return false

        val updated = storyProgressStore.setCurrentStoryId(player.uniqueId, target.id)
        if (updated) {
            placeholderBridge.refresh()
        }
        return updated
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
