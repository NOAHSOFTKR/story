package kr.kjh9211.story.event

import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.Inventory

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
        val result = EventContextSupport.itemIdentifier(event.currentItem ?: event.recipe.result) ?: return
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
        if (topInventory.type != InventoryType.CHEST || event.clickedInventory != topInventory) {
            return
        }
        val itemId = EventContextSupport.itemIdentifier(event.cursor) ?: return
        val containerId = containerIdentifier(topInventory) ?: return
        val beforeCount = countMatchingItems(topInventory, itemId)

        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline || event.isCancelled) {
                return@Runnable
            }
            if (countMatchingItems(topInventory, itemId) <= beforeCount) {
                return@Runnable
            }
            storyRuntime.runTriggerByEvent(
                "block_interact_chest",
                EventContextSupport.createContext(
                    player,
                    mapOf(
                        "player" to player.name,
                        "item" to itemId,
                        "container" to containerId,
                    ),
                ),
            )
        })
    }

    @EventHandler(ignoreCancelled = true)
    fun onArmorInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != player.inventory ||
            (event.slotType != InventoryType.SlotType.ARMOR && !event.isShiftClick)
        ) {
            return
        }
        dispatchArmorChangeIfNeeded(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onArmorInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        if (event.item == null) {
            return
        }
        dispatchArmorChangeIfNeeded(event.player)
    }

    private fun dispatchArmorChangeIfNeeded(player: Player) {
        val before = armorIdentifiers(player)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) {
                return@Runnable
            }
            val after = armorIdentifiers(player)
            if (before == after) {
                return@Runnable
            }
            storyRuntime.runTriggerByEvent(
                "itemsadder_item_equip",
                EventContextSupport.createContext(player, mapOf("player" to player.name) + after),
            )
        })
    }

    private fun armorIdentifiers(player: Player): Map<String, String> {
        return mapOfNotNull(
            "armor.helmet" to EventContextSupport.itemIdentifier(player.inventory.helmet),
            "armor.chestplate" to EventContextSupport.itemIdentifier(player.inventory.chestplate),
            "armor.leggings" to EventContextSupport.itemIdentifier(player.inventory.leggings),
            "armor.boots" to EventContextSupport.itemIdentifier(player.inventory.boots),
        )
    }

    private fun containerIdentifier(inventory: Inventory): String? {
        val holder = inventory.holder as? BlockInventoryHolder ?: return null
        val location = holder.block.location
        val world = location.world ?: return null
        return "${world.name}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    private fun countMatchingItems(inventory: Inventory, itemId: String): Int {
        return inventory.contents.sumOf { stack ->
            if (EventContextSupport.itemIdentifier(stack)?.equals(itemId, ignoreCase = true) == true) stack.amount else 0
        }
    }

    private fun mapOfNotNull(vararg entries: Pair<String, String?>): Map<String, String> {
        return entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
    }
}
