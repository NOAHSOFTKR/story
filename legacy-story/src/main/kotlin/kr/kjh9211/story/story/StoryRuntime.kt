package kr.kjh9211.story.story

import org.bukkit.entity.Player
import java.util.UUID

class StoryRuntime(
    private val storyRegistryProvider: () -> StoryRegistry,
    private val progressStore: StoryProgressStore,
    private val currentStoryIdProvider: (UUID) -> String?,
) {
    fun runTriggerByEvent(eventKey: String, context: StoryExecutionContext) {
        val player = context.sender as? Player ?: return
        val registry = storyRegistryProvider()
        val currentStory = currentStoryIdProvider(player.uniqueId)
            ?.let(registry::findStory)
            ?: registry.initialStory()
        val stories = buildList {
            currentStory?.let(::add)
            registry.stories()
                .filter { story ->
                    story.id != currentStory?.id && progressStore.isStoryCompleted(player.uniqueId, story.id)
                }
                .filter { story -> story.triggers.any { it.repeatable } }
                .forEach(::add)
        }

        stories.asSequence()
            .filter { it.enabled }
            .flatMap { story -> story.triggers.asSequence().map { trigger -> story to trigger } }
            .filter { (_, trigger) -> trigger.enabled }
            .filter { (_, trigger) -> trigger.type == StoryTriggerType.EVENT }
            .filter { (_, trigger) -> trigger.event.equals(eventKey, ignoreCase = true) }
            .filter { (_, trigger) -> trigger.matchesContext(context) }
            .forEach { (story, trigger) -> executeTrigger(story, trigger, context) }
    }

    fun runManualTrigger(storyId: String, triggerId: String, context: StoryExecutionContext): StoryTriggerRunResult {
        val story = storyRegistryProvider().findStory(storyId) ?: return StoryTriggerRunResult.NOT_FOUND
        val trigger = story.findTrigger(triggerId) ?: return StoryTriggerRunResult.NOT_FOUND
        if (trigger.type != StoryTriggerType.MANUAL) {
            return StoryTriggerRunResult.NOT_MANUAL
        }
        return executeTrigger(story, trigger, context)
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

    private fun executeTrigger(
        story: StoryDefinition,
        trigger: StoryTrigger,
        context: StoryExecutionContext,
    ): StoryTriggerRunResult {
        if (!story.enabled || !trigger.enabled || !trigger.matchesContext(context)) {
            return StoryTriggerRunResult.NOT_ELIGIBLE
        }
        val player = context.sender as? Player ?: return StoryTriggerRunResult.PLAYER_REQUIRED
        val registry = storyRegistryProvider()
        val currentStoryId = currentStoryIdProvider(player.uniqueId)
            ?: registry.initialStory()?.id
            ?: return StoryTriggerRunResult.NOT_ELIGIBLE
        val storyCompleted = progressStore.isStoryCompleted(player.uniqueId, story.id)
        if (!currentStoryId.equals(story.id, ignoreCase = true) && !(trigger.repeatable && storyCompleted)) {
            return StoryTriggerRunResult.NOT_CURRENT
        }
        if (story.previousStoryId != null && !progressStore.isStoryCompleted(player.uniqueId, story.previousStoryId)) {
            return StoryTriggerRunResult.PRECONDITION_NOT_MET
        }
        if (story.requiredFlags.any { !progressStore.hasFlag(player.uniqueId, it) }) {
            return StoryTriggerRunResult.PRECONDITION_NOT_MET
        }
        if (storyCompleted && !trigger.repeatable) {
            return StoryTriggerRunResult.ALREADY_COMPLETED
        }
        if (!trigger.repeatable && progressStore.isTriggerExecuted(player.uniqueId, story.id, trigger.id)) {
            return StoryTriggerRunResult.ALREADY_EXECUTED
        }

        val quest = story.quest?.takeIf { it.enabled }
        val objective = quest?.findObjective(trigger.objectiveId)
        val current = objective?.let { progressStore.objectiveProgress(player.uniqueId, story.id, it.id) }
        val updated = if (objective != null && current != null) {
            (current + trigger.progressAmount).coerceAtMost(objective.target)
        } else {
            null
        }
        val allObjectivesCompleted = quest?.let { configuredQuest ->
            configuredQuest.objectives.isNotEmpty() && configuredQuest.objectives.all { item ->
                if (item.id.equals(objective?.id, ignoreCase = true)) {
                    (updated ?: 0) >= item.target
                } else {
                    progressStore.objectiveProgress(player.uniqueId, story.id, item.id) >= item.target
                }
            }
        } ?: false
        val completedStory = !storyCompleted && (trigger.completesStory || allObjectivesCompleted)

        if (!progressStore.commitTrigger(
                playerUuid = player.uniqueId,
                storyId = story.id,
                triggerId = trigger.id,
                currentStoryId = story.id,
                objectiveId = objective?.id,
                objectiveProgress = updated,
                completedStory = completedStory,
                nextStoryId = if (completedStory) story.nextStoryId else null,
                completionFlags = if (completedStory) story.completionFlags else emptySet(),
                repeatable = trigger.repeatable,
            )
        ) {
            player.sendMessage("§c[Story] 진행 상태를 저장하지 못했습니다. 다시 시도해 주세요.")
            return StoryTriggerRunResult.SAVE_FAILED
        }

        trigger.execute(context)
        if (completedStory) {
            runStoryDoneActions(story, context)
        }
        return StoryTriggerRunResult.EXECUTED
    }

    private fun runStoryDoneActions(story: StoryDefinition, context: StoryExecutionContext) {
        val doneContext = context.copy(
            placeholders = context.placeholders + mapOf(
                "storyId" to story.id,
                "storyName" to story.title,
                "nextStory" to (story.nextStoryId ?: ""),
            ),
        )
        story.triggers
            .asSequence()
            .filter { it.enabled && it.type == StoryTriggerType.EVENT }
            .filter { it.event.equals("story_done", ignoreCase = true) }
            .filter { it.matchesContext(doneContext) }
            .forEach { it.execute(doneContext) }
    }
}

enum class StoryTriggerRunResult {
    EXECUTED,
    NOT_FOUND,
    NOT_MANUAL,
    NOT_ELIGIBLE,
    PLAYER_REQUIRED,
    NOT_CURRENT,
    PRECONDITION_NOT_MET,
    ALREADY_COMPLETED,
    ALREADY_EXECUTED,
    SAVE_FAILED,
}
