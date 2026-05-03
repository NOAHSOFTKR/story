package kr.kjh9211.story.event

import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent
import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

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
    }

    @EventHandler(ignoreCancelled = true)
    fun onCustomBlockBreak(event: CustomBlockBreakEvent) {
        val player = event.player
        val blockId = event.customBlock.namespacedID
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
        val itemId = resolveItemsAdderId(event.item.itemStack) ?: return
        storyRuntime.runTriggerByEvent(
            "itemsadder_item_pickup",
            EventContextSupport.createContext(player, mapOf("player" to player.name, "item" to itemId)),
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val itemId = resolveItemsAdderId(event.currentItem ?: event.cursor) ?: return
        storyRuntime.runTriggerByEvent(
            "itemsadder_item_equip",
            EventContextSupport.createContext(player, mapOf("player" to player.name, "item" to itemId)),
        )
    }

    private fun resolveItemsAdderId(item: ItemStack?): String? {
        if (item == null || item.type.isAir) {
            return null
        }

        return try {
            val customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack")
            val byItemStack = customStackClass.getMethod("byItemStack", ItemStack::class.java)
            val customStack = byItemStack.invoke(null, item) ?: return null
            EventContextSupport.invokeNoArg(customStack, "getNamespacedID")?.toString()
                ?: EventContextSupport.invokeNoArg(customStack, "getId")?.toString()
        } catch (_: ReflectiveOperationException) {
            null
        }
    }
}
