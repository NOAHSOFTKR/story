package kr.kjh9211.story.towny

import kr.kjh9211.story.Story
import kr.kjh9211.story.event.EventContextSupport
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventException
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor

class TownyEventBridge(
    private val plugin: Story,
    private val storyRuntime: StoryRuntime,
) {
    private val registrations = listOf(
        TownyEventRegistration("towny_town_create", "com.palmergames.bukkit.towny.event.NewTownEvent"),
        TownyEventRegistration("towny_town_claim", "com.palmergames.bukkit.towny.event.TownClaimEvent"),
        TownyEventRegistration("towny_town_unclaim", "com.palmergames.bukkit.towny.event.TownUnclaimEvent"),
        TownyEventRegistration("towny_town_add_resident", "com.palmergames.bukkit.towny.event.TownAddResidentEvent"),
        TownyEventRegistration("towny_town_remove_resident", "com.palmergames.bukkit.towny.event.TownRemoveResidentEvent"),
        TownyEventRegistration("towny_nation_create", "com.palmergames.bukkit.towny.event.NewNationEvent"),
        TownyEventRegistration("towny_town_transaction", "com.palmergames.bukkit.towny.event.economy.TownTransactionEvent"),
        TownyEventRegistration("towny_nation_transaction", "com.palmergames.bukkit.towny.event.economy.NationTransactionEvent"),
    )

    private val listener = object : Listener {}

    fun registerIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("Towny") == null) {
            plugin.logger.info("Towny not found; Towny story event bridge is disabled.")
            return
        }

        registrations.forEach { registration ->
            val eventClass = resolveEventClass(registration.className) ?: return@forEach
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                EventExecutor { _, event ->
                    if (eventClass.isInstance(event)) {
                        handleTownyEvent(registration.eventKey, event)
                    }
                },
                plugin,
                true,
            )
        }

        plugin.logger.info("Registered Towny event bridge for ${registrations.size} event keys.")
    }

    private fun resolveEventClass(className: String): Class<out Event>? {
        return try {
            val rawClass = Class.forName(className)
            @Suppress("UNCHECKED_CAST")
            rawClass as? Class<out Event>
        } catch (_: ClassNotFoundException) {
            plugin.logger.warning("Towny event class not found: $className")
            null
        }
    }

    private fun handleTownyEvent(eventKey: String, event: Event) {
        try {
            val sender = EventContextSupport.extractSenderFromMethods(event, "getPlayer", "getResident", "getMayor")
            val townName = EventContextSupport.readName(EventContextSupport.invokeNoArg(event, "getTown"))
            val nationName = EventContextSupport.readName(EventContextSupport.invokeNoArg(event, "getNation"))
            storyRuntime.runTriggerByEvent(
                eventKey,
                EventContextSupport.createContext(
                    sender,
                    mapOfNotNull("player" to sender?.name, "town" to townName, "nation" to nationName),
                ),
            )
        } catch (exception: Exception) {
            throw EventException(exception)
        }
    }
}

data class TownyEventRegistration(
    val eventKey: String,
    val className: String,
)

private fun mapOfNotNull(vararg entries: Pair<String, String?>): Map<String, String> {
    return entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
}
