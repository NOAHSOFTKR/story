package kr.kjh9211.story.placeholder

import kr.kjh9211.story.Story
import kr.kjh9211.story.story.StoryProgressStore
import kr.kjh9211.story.story.StoryRegistry
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

class StoryPlaceholderExpansion(
    private val plugin: Story,
    private val storyRegistryProvider: () -> StoryRegistry,
    private val storyProgressStore: StoryProgressStore,
) : PlaceholderExpansion() {

    @Volatile
    private var lastUpdatedAt: Long = System.currentTimeMillis()

    override fun getIdentifier(): String = "story"

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ").ifBlank { "unknown" }

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val pieces = params.split(":")
        val registry = storyRegistryProvider()
        val playerUuid = player?.uniqueId
        val currentStoryId = playerUuid?.let { storyProgressStore.currentStoryId(it) }
        val currentStory = registry.findStory(currentStoryId)
        val currentIndex = registry.progressIndex(currentStoryId)
        val totalStories = registry.totalStories()
        val questProgress = if (player != null) {
            currentStoryId?.let { storyId ->
                val story = registry.findStory(storyId)
                val quest = story?.quest?.takeIf { it.enabled }
                if (story != null && quest != null) {
                    val objectives = quest.objectives.map { objective ->
                        val current = storyProgressStore.objectiveProgress(playerUuid, story.id, objective.id)
                        current.coerceAtMost(objective.target) to objective.target
                    }
                    Triple(
                        objectives.count { (current, target) -> current >= target },
                        objectives.size,
                        storyProgressStore.isStoryCompleted(playerUuid, story.id),
                    )
                } else {
                    null
                }
            }
        } else {
            null
        }
        return when (pieces.firstOrNull()?.lowercase()) {
            "last_update" -> lastUpdatedAt.toString()
            "story_enabled" -> registry.findStory(pieces.getOrNull(1))?.enabled?.toString()
            "story_title" -> registry.findStory(pieces.getOrNull(1))?.title
            "current_id" -> currentStory?.id
            "current_title" -> currentStory?.title
            "current_index" -> currentIndex?.toString()
            "total_count" -> totalStories.toString()
            "progress_text" -> if (currentIndex != null) "$currentIndex/$totalStories" else null
            "quest_done_count" -> questProgress?.first?.toString()
            "quest_total_count" -> questProgress?.second?.toString()
            "quest_status" -> when (questProgress?.third) {
                true -> "COMPLETED"
                false -> if ((questProgress?.first ?: 0) > 0) "IN_PROGRESS" else "NOT_STARTED"
                null -> null
            }
            "player_uuid" -> playerUuid?.toString()
            "trigger_enabled" -> registry.findStory(pieces.getOrNull(1))
                ?.findTrigger(pieces.getOrNull(2))
                ?.enabled
                ?.toString()
            "action_enabled" -> {
                val storyId = pieces.getOrNull(1) ?: return null
                val triggerId = pieces.getOrNull(2) ?: return null
                val actionId = pieces.getOrNull(3) ?: return null
                registry.findAction(storyId, triggerId, actionId)?.enabled?.toString()
            }
            else -> null
        }
    }

    fun markUpdated() {
        lastUpdatedAt = System.currentTimeMillis()
    }
}
