package kr.kjh9211.story.event

import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor

class PluginEventBridge(
    private val plugin: Story,
    private val storyRuntime: StoryRuntime,
) {
    private val listener = object : Listener {}

    private val registrations = listOf(
        ReflectiveEventRegistration(
            eventKey = "mythicmobs_mob_death",
            classNames = listOf(
                "io.lumine.mythic.bukkit.events.MythicMobDeathEvent",
                "io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDeathEvent",
            ),
            contextFactory = { event ->
                val sender = EventContextSupport.extractSenderFromMethods(event, "getKiller", "getPlayer")
                val mobType = EventContextSupport.readStableId(EventContextSupport.invokeNoArg(event, "getMobType"))
                    ?: EventContextSupport.readStableId(EventContextSupport.invokeNoArg(event, "getMob"))
                EventContextSupport.createContext(
                    sender,
                    mapOfNotNull("player" to sender?.name, "mob" to mobType),
                )
            },
        ),
        ReflectiveEventRegistration(
            eventKey = "quests_quest_complete",
            classNames = listOf(
                "me.blackvein.quests.events.QuestCompleteEvent",
                "me.blackvein.quests.events.quest.QuestCompleteEvent",
                "me.pikamug.quests.events.QuestCompleteEvent",
                "me.pikamug.quests.events.quest.QuestCompleteEvent",
            ),
            contextFactory = { event ->
                val sender = EventContextSupport.extractSenderFromMethods(event, "getPlayer")
                val questName = EventContextSupport.readStableId(EventContextSupport.invokeNoArg(event, "getQuest"))
                    ?: EventContextSupport.readStableId(EventContextSupport.invokeNoArg(event, "getQuestName"))
                EventContextSupport.createContext(
                    sender,
                    mapOfNotNull("player" to sender?.name, "quest" to questName),
                )
            },
        ),
        ReflectiveEventRegistration(
            eventKey = "cutscene_fire",
            classNames = listOf("kr.kjh9211.cutthin.event.CutsceneFireEvent"),
            contextFactory = { event ->
                val sender = EventContextSupport.extractSenderFromMethods(event, "getPlayer")
                val cutsceneId = EventContextSupport.readStableId(EventContextSupport.invokeNoArg(event, "getCutscene"))
                val key = EventContextSupport.invokeNoArg(event, "getKey")?.toString()
                val placeholders = EventContextSupport.readStringMap(EventContextSupport.invokeNoArg(event, "getPlaceholders"))
                EventContextSupport.createContext(
                    sender,
                    placeholders + mapOfNotNull(
                        "player" to sender?.name,
                        "cutscene" to cutsceneId,
                        "cutsceneKey" to key,
                    ),
                )
            },
        ),
        ReflectiveEventRegistration(
            eventKey = "cutscene_end",
            classNames = listOf("kr.kjh9211.cutthin.event.CutsceneEndEvent"),
            contextFactory = { event ->
                val sender = EventContextSupport.extractSenderFromMethods(event, "getPlayer")
                val cutsceneId = EventContextSupport.readStableId(EventContextSupport.invokeNoArg(event, "getCutscene"))
                val reason = EventContextSupport.invokeNoArg(event, "getReason")?.toString()
                EventContextSupport.createContext(
                    sender,
                    mapOfNotNull(
                        "player" to sender?.name,
                        "cutscene" to cutsceneId,
                        "cutsceneReason" to reason,
                    ),
                )
            },
        ),
    )

    fun register() {
        registrations.forEach { registration ->
            val eventClass = registration.classNames.firstNotNullOfOrNull(::resolveEventClass)
            if (eventClass == null) {
                plugin.logger.warning(
                    "No compatible event class is available for '${registration.eventKey}'; matching story triggers are inactive.",
                )
                return@forEach
            }
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                EventExecutor { _, event ->
                    if (eventClass.isInstance(event)) {
                        storyRuntime.runTriggerByEvent(registration.eventKey, registration.contextFactory(event))
                    }
                },
                plugin,
                true,
            )
        }
    }

    private fun resolveEventClass(className: String): Class<out Event>? {
        return try {
            val rawClass = Class.forName(className)
            @Suppress("UNCHECKED_CAST")
            rawClass as? Class<out Event>
        } catch (_: ClassNotFoundException) {
            null
        }
    }
}

data class ReflectiveEventRegistration(
    val eventKey: String,
    val classNames: List<String>,
    val contextFactory: (Event) -> kr.kjh9211.story.story.StoryExecutionContext,
)

private fun mapOfNotNull(vararg entries: Pair<String, String?>): Map<String, String> {
    return entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
}
