package kr.kjh9211.story

import kr.kjh9211.story.command.StoryCommand
import kr.kjh9211.story.placeholder.StoryPlaceholderBridge
import kr.kjh9211.story.story.StoryConfigStore
import kr.kjh9211.story.story.StoryRegistry
import kr.kjh9211.story.story.StoryRuntime
import kr.kjh9211.story.towny.TownyEventBridge
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class Story : JavaPlugin() {

    private lateinit var storyRegistry: StoryRegistry
    private lateinit var storyConfigStore: StoryConfigStore
    private lateinit var placeholderBridge: StoryPlaceholderBridge
    private lateinit var storyRuntime: StoryRuntime
    private lateinit var townyEventBridge: TownyEventBridge

    override fun onEnable() {
        saveResource("stories/example.yml", false)

        storyConfigStore = StoryConfigStore(this)
        storyRuntime = StoryRuntime { storyRegistry }
        placeholderBridge = StoryPlaceholderBridge(this) { storyRegistry }
        townyEventBridge = TownyEventBridge(this, storyRuntime)
        reloadStories()

        val storyCommand = StoryCommand(
            storyRegistryProvider = { storyRegistry },
            reloadStories = ::reloadStories,
            restartPlugin = ::restartPlugin,
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
        townyEventBridge.registerIfAvailable()
    }

    override fun onDisable() {
        if (::placeholderBridge.isInitialized) {
            placeholderBridge.close()
        }
    }

    private fun reloadStories() {
        storyRegistry = storyConfigStore.loadAll()
        logger.info("Loaded ${storyRegistry.stories().size} stories from ${storyConfigStore.storiesDirectory().absolutePath}")
        placeholderBridge.refresh()
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
