package kr.kjh9211.story.event

import kr.kjh9211.cutthin.event.CutsceneEndEvent
import kr.kjh9211.cutthin.event.CutsceneFireEvent
import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryExecutionContext
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
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
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler
    fun onCutsceneEnd(event: CutsceneEndEvent) {
        if (event.reason != CutsceneEndEvent.Reason.COMPLETED) return
        val player = event.player
        storyRuntime.runTriggerByEvent(
            "cutscene_end:${event.cutscene.id}",
            StoryExecutionContext(player, mapOf("player" to player.name)),
        )
    }

    @EventHandler
    fun onCutsceneFire(event: CutsceneFireEvent) {
        val player = event.player
        storyRuntime.runTriggerByEvent(
            event.key,
            StoryExecutionContext(player, event.placeholders + mapOf("player" to player.name)),
        )
    }
}
