package kr.kjh9211.story.event

import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.event.entity.EntityPickupItemEvent

class ItemsAdderEventBridge(
    private val plugin: Story,
    private val storyRuntime: StoryRuntime,
) : Listener {

    fun registerIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            plugin.logger.info("ItemsAdder not found; ItemsAdder story bridge is disabled.")
            return
        }

        Bukkit.getPluginManager().registerEvents(this, plugin)
        val customBlockBreakEvent = resolveEventClass("dev.lone.itemsadder.api.Events.CustomBlockBreakEvent")
        if (customBlockBreakEvent == null) {
            plugin.logger.warning("ItemsAdder CustomBlockBreakEvent was not found; custom block triggers are disabled.")
            return
        }
        Bukkit.getPluginManager().registerEvent(
            customBlockBreakEvent,
            this,
            EventPriority.MONITOR,
            EventExecutor { _, event -> handleCustomBlockBreak(event) },
            plugin,
            true,
        )
    }

    private fun handleCustomBlockBreak(event: Event) {
        val player = EventContextSupport.extractSenderFromMethods(event, "getPlayer") as? Player ?: return
        val blockId = EventContextSupport.invokeNoArg(event, "getNamespacedID")?.toString() ?: return
        storyRuntime.runTriggerByEvent(
            "block_break",
            EventContextSupport.createContext(
                player,
                mapOf(
                    "player" to player.name,
                    "block" to blockId,
                ),
            ),
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onItemPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val itemId = EventContextSupport.itemIdentifier(event.item.itemStack) ?: return
        storyRuntime.runTriggerByEvent(
            "itemsadder_item_pickup",
            EventContextSupport.createContext(player, mapOf("player" to player.name, "item" to itemId)),
        )
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
