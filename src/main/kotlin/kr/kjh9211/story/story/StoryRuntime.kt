package kr.kjh9211.story.story

import org.bukkit.entity.Player
import java.util.UUID

class StoryRuntime(
    private val storyRegistryProvider: () -> StoryRegistry,
    private val progressStore: StoryProgressStore,
    private val currentStoryIdProvider: (UUID) -> String?,
    private val setCurrentStoryId: (UUID, String?) -> Unit,
) {
    fun runTriggerByEvent(eventKey: String, context: StoryExecutionContext) {
        val player = context.sender as? Player ?: return
        val registry = storyRegistryProvider()
        val currentStoryId = currentStoryIdProvider(player.uniqueId)
            ?: registry.orderedStories().firstOrNull()?.id
            ?: return

        val story = registry.findStory(currentStoryId) ?: return
        if (!story.enabled) return

        story.triggers
            .asSequence()
            .filter { it.enabled }
            .filter { it.event.equals(eventKey, ignoreCase = true) }
            .forEach { trigger -> executeTrigger(story, trigger, context) }
    }

    fun runTriggerByStoryEvent(storyId: String, eventKey: String, context: StoryExecutionContext) {
        val story = storyRegistryProvider().findStory(storyId) ?: return
        story.triggers
            .asSequence()
            .filter { it.enabled }
            .filter { it.event.equals(eventKey, ignoreCase = true) }
            .forEach { trigger -> executeTrigger(story, trigger, context) }
    }

    fun questProgress(player: Player, storyId: String?): StoryQuestProgress? {
        val story = storyRegistryProvider().findStory(storyId) ?: return null
        val quest = story.quest?.takeIf { it.enabled } ?: return null
        val objectives = quest.objectives.map { objective ->
            val current = progressStore.objectiveProgress(player.uniqueId, story.id, objective.id)
            ObjectiveProgress(
                objective = objective,
                current = current.coerceAtMost(objective.target),
                completed = current >= objective.target,
            )
        }
        return StoryQuestProgress(
            story = story,
            completed = progressStore.isStoryCompleted(player.uniqueId, story.id),
            objectives = objectives,
        )
    }

    private fun executeTrigger(story: StoryDefinition, trigger: StoryTrigger, context: StoryExecutionContext) {
        val player = context.sender as? Player
        if (player != null && currentStoryIdProvider(player.uniqueId) == null) {
            setCurrentStoryId(player.uniqueId, story.id)
        }
        trigger.execute(context)
        updateQuestProgress(story, trigger, context)
    }

    private fun updateQuestProgress(story: StoryDefinition, trigger: StoryTrigger, context: StoryExecutionContext) {
        val player = context.sender as? Player ?: return
        val quest = story.quest?.takeIf { it.enabled } ?: return
        val objective = quest.findObjective(trigger.objectiveId) ?: return

        val current = progressStore.objectiveProgress(player.uniqueId, story.id, objective.id)
        val updated = (current + trigger.progressAmount).coerceAtMost(objective.target)
        progressStore.setObjectiveProgress(player.uniqueId, story.id, objective.id, updated)

        val allCompleted = quest.objectives.all { item ->
            progressStore.objectiveProgress(player.uniqueId, story.id, item.id) >= item.target
        }
        if (!allCompleted || progressStore.isStoryCompleted(player.uniqueId, story.id)) {
            return
        }

        progressStore.setStoryCompleted(player.uniqueId, story.id, true)
        if (currentStoryIdProvider(player.uniqueId)?.equals(story.id, ignoreCase = true) == true) {
            setCurrentStoryId(player.uniqueId, story.nextStoryId ?: story.id)
        }

        val doneContext = context.copy(
            placeholders = context.placeholders + mapOf(
                "storyId" to story.id,
                "storyName" to story.title,
                "nextStory" to (story.nextStoryId ?: ""),
            ),
        )
        runTriggerByStoryEvent(story.id, "story_done", doneContext)
    }
}
