package kr.kjh9211.story.event

import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryExecutionContext
import kr.kjh9211.story.story.StoryRuntime
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor

/**
 * Quest 엔진(kr.kjh9211:quest) 소프트 디펜던시 Bridge. story의 COMPLETE_QUEST 트리거가 서드파티
 * Quests(me.blackvein/me.pikamug, [PluginEventBridge]의 리플렉션 등록) 대신 이 프로젝트의
 * QuestCompleteEvent/QuestRewardClaimEvent를 직접 구독하도록 전환한다(ROADMAP.md Phase 4).
 *
 * 이벤트 키는 CutThin의 "cutscene_end:<id>" 관례를 따라 퀘스트 id로 구분한다 — 서드파티 경로는
 * 퀘스트별 구분이 불가능해 아무 퀘스트 완료에나 반응했지만, 이쪽은 story 설정에서 특정 퀘스트를
 * 지정할 수 있다.
 */
class QuestEventBridge(
    private val plugin: Story,
    private val storyRuntime: StoryRuntime,
) : Listener {

    fun registerIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("Quest") == null) {
            plugin.logger.info("Quest not found; Quest engine story bridge is disabled.")
            return
        }
        register("kr.kjh9211.quest.event.QuestCompleteEvent", "quest_complete")
        register("kr.kjh9211.quest.event.QuestRewardClaimEvent", "quest_reward_claim")
    }

    private fun register(className: String, eventPrefix: String) {
        val eventClass = resolveEventClass(className)
        if (eventClass == null) {
            plugin.logger.warning("Quest event class not found: $className")
            return
        }
        Bukkit.getPluginManager().registerEvent(
            eventClass,
            this,
            EventPriority.MONITOR,
            EventExecutor { _, event -> handle(eventPrefix, event) },
            plugin,
            true,
        )
    }

    private fun handle(eventPrefix: String, event: Event) {
        val player = EventContextSupport.extractSenderFromMethods(event, "getPlayer") as? Player ?: return
        val quest = EventContextSupport.invokeNoArg(event, "getQuest") ?: return
        val questId = EventContextSupport.invokeNoArg(quest, "getId")?.toString() ?: return
        val display = EventContextSupport.invokeNoArg(quest, "getDisplay")
        val questName = EventContextSupport.invokeNoArg(display ?: quest, "getName")?.toString() ?: questId
        storyRuntime.runTriggerByEvent(
            "$eventPrefix:$questId",
            StoryExecutionContext(player, mapOf("player" to player.name, "quest" to questId, "questName" to questName)),
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
