package kr.kjh9211.story.event

import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryExecutionContext
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventExecutor
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class CutThinEventBridge(
    private val plugin: Story,
    private val storyRuntime: StoryRuntime,
) : Listener {

    fun registerIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("CutThin") == null) {
            plugin.logger.info("CutThin not found; CutThin story bridge is disabled.")
            return
        }
        register("kr.kjh9211.cutthin.event.CutsceneEndEvent", ::handleCutsceneEnd)
        register("kr.kjh9211.cutthin.event.CutsceneFireEvent", ::handleCutsceneFire)
    }

    private fun handleCutsceneEnd(event: Event) {
        if (!EventContextSupport.invokeNoArg(event, "getReason")?.toString().equals("COMPLETED", ignoreCase = true)) return
        val player = EventContextSupport.extractSenderFromMethods(event, "getPlayer") as? Player ?: return
        val cutscene = EventContextSupport.invokeNoArg(event, "getCutscene") ?: return
        val cutsceneId = EventContextSupport.invokeNoArg(cutscene, "getId")?.toString() ?: return
        storyRuntime.runTriggerByEvent(
            "cutscene_end:$cutsceneId",
            StoryExecutionContext(player, mapOf("player" to player.name)),
        )
    }

    private fun handleCutsceneFire(event: Event) {
        val player = EventContextSupport.extractSenderFromMethods(event, "getPlayer") as? Player ?: return
        val key = EventContextSupport.invokeNoArg(event, "getKey")?.toString() ?: return
        val placeholders = (EventContextSupport.invokeNoArg(event, "getPlaceholders") as? Map<*, *>)
            ?.mapNotNull { (placeholderKey, value) -> placeholderKey?.toString()?.let { it to value.toString() } }
            ?.toMap()
            .orEmpty()
        storyRuntime.runTriggerByEvent(
            key,
            StoryExecutionContext(player, placeholders + mapOf("player" to player.name)),
        )
    }

    private fun register(className: String, handler: (Event) -> Unit) {
        val eventClass = resolveEventClass(className)
        if (eventClass == null) {
            plugin.logger.warning("CutThin event class not found: $className")
            return
        }
        Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, EventExecutor { _, event -> handler(event) }, plugin, true)
    }

    private fun resolveEventClass(className: String): Class<out Event>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(className) as? Class<out Event>
        } catch (_: ClassNotFoundException) {
            null
        }
    }
}
