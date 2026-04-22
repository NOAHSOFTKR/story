package kr.kjh9211.story.story

enum class StoryTriggerType {
    MANUAL,
    ;

    companion object {
        fun fromConfig(value: String?): StoryTriggerType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MANUAL
        }
    }
}
