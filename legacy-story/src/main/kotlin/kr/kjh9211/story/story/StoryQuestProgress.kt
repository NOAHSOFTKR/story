package kr.kjh9211.story.story

data class StoryQuestProgress(
    val story: StoryDefinition,
    val completed: Boolean,
    val objectives: List<ObjectiveProgress>,
) {
    val completedObjectives: Int
        get() = objectives.count { it.completed }

    val totalObjectives: Int
        get() = objectives.size
}

data class ObjectiveProgress(
    val objective: StoryObjective,
    val current: Int,
    val completed: Boolean,
)
