package kr.kjh9211.story.event

import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.block.Container
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType

class BukkitEventBridge(
    private val plugin: Story,
    private val storyRuntime: StoryRuntime,
) : Listener {

    fun register() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        storyRuntime.runTriggerByEvent(
            "block_break",
            EventContextSupport.createContext(
                event.player,
                mapOf(
                    "player" to event.player.name,
                    "block" to event.block.type.name,
                ),
            ),
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val result = event.currentItem?.type?.name ?: event.recipe.result.type.name
        storyRuntime.runTriggerByEvent(
            "customcrafting_craft",
            EventContextSupport.createContext(
                player,
                mapOf(
                    "player" to player.name,
                    "item" to result,
                ),
            ),
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onChestInteract(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val topInventory = event.view.topInventory
        val holder = topInventory.holder
        val isChestLike = topInventory.type == InventoryType.CHEST || holder is Container
        if (!isChestLike) {
            return
        }

        storyRuntime.runTriggerByEvent(
            "block_interact_chest",
            EventContextSupport.createContext(
                player,
                mapOf(
                    "player" to player.name,
                    "inventoryType" to topInventory.type.name,
                ),
            ),
        )
    }
}
