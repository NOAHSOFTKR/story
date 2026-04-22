package kr.kjh9211.story.story

class StoryRegistry(
    private val loadedStories: List<StoryDefinition>,
) {
    private val cachedOrderedStories: List<StoryDefinition> by lazy {
        val byId = loadedStories.associateBy { it.id.lowercase() }
        val visited = linkedSetOf<String>()
        val ordered = mutableListOf<StoryDefinition>()

        loadedStories
            .filter { it.previousStoryId.isNullOrBlank() || !byId.containsKey(it.previousStoryId.lowercase()) }
            .sortedBy { it.id }
            .forEach { start ->
                var current: StoryDefinition? = start
                while (current != null && visited.add(current.id.lowercase())) {
                    ordered += current
                    current = current.nextStoryId?.let { byId[it.lowercase()] }
                }
            }

        loadedStories
            .sortedBy { it.id }
            .forEach { story ->
                if (visited.add(story.id.lowercase())) {
                    ordered += story
                }
            }
        ordered
    }

    fun stories(): List<StoryDefinition> = loadedStories

    fun storyIds(): List<String> = loadedStories.map { it.id }

    fun findStory(storyId: String?): StoryDefinition? {
        if (storyId == null) {
            return null
        }
        return loadedStories.firstOrNull { it.id.equals(storyId, ignoreCase = true) }
    }

    fun findAction(storyId: String, triggerId: String, actionId: String): StoryAction? {
        return findStory(storyId)
            ?.findTrigger(triggerId)
            ?.actions
            ?.firstOrNull { it.id.equals(actionId, ignoreCase = true) }
    }

    fun orderedStories(): List<StoryDefinition> = cachedOrderedStories

    fun totalStories(): Int = cachedOrderedStories.size

    fun progressIndex(storyId: String?): Int? {
        if (storyId == null) {
            return null
        }
        val index = cachedOrderedStories.indexOfFirst { it.id.equals(storyId, ignoreCase = true) }
        return if (index >= 0) index + 1 else null
    }

    fun nextStory(storyId: String?): StoryDefinition? {
        return findStory(storyId)?.nextStoryId?.let { findStory(it) }
    }

    fun previousStory(storyId: String?): StoryDefinition? {
        return findStory(storyId)?.previousStoryId?.let { findStory(it) }
    }
}
