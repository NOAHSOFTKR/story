package kr.kjh9211.story.placeholder

import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryProgressStore
import kr.kjh9211.story.story.StoryRegistry
import org.bukkit.Bukkit

class StoryPlaceholderBridge(
    private val plugin: Story,
    private val storyRegistryProvider: () -> StoryRegistry,
    private val storyProgressStore: StoryProgressStore,
) {
    private var expansion: StoryPlaceholderExpansion? = null

    fun registerIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.logger.info("PlaceholderAPI not found; story placeholders are disabled.")
            return
        }

        if (expansion == null) {
            expansion = StoryPlaceholderExpansion(plugin, storyRegistryProvider, storyProgressStore)
        }

        expansion?.register()
    }

    fun refresh() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return
        }

        registerIfAvailable()
        expansion?.markUpdated()
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "papi reload")
    }

    fun close() {
        expansion = null
    }
}
