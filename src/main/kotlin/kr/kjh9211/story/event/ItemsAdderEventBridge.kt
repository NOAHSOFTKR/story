package kr.kjh9211.story.event

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

    private var customStackClass: Class<*>? = null
    private var byItemStack: java.lang.reflect.Method? = null
    private var getNamespacedID: java.lang.reflect.Method? = null
    private var getId: java.lang.reflect.Method? = null
    private var itemsAdderChecked = false

    private fun resolveItemsAdderId(item: ItemStack?): String? {
        if (item == null || item.type.isAir) {
            return null
        }

        if (!itemsAdderChecked) {
            try {
                customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack")
                byItemStack = customStackClass?.getMethod("byItemStack", ItemStack::class.java)
                getNamespacedID = customStackClass?.getMethod("getNamespacedID")
                getId = customStackClass?.getMethod("getId")
            } catch (_: Exception) {
            }
            itemsAdderChecked = true
        }

        val stackClass = customStackClass ?: return null
        val byItem = byItemStack ?: return null

        return try {
            val customStack = byItem.invoke(null, item) ?: return null
            val nsId = getNamespacedID ?: getId
            nsId?.invoke(customStack)?.toString()
        } catch (_: Exception) {
            null
        }
    }
}
