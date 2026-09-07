package kr.kjh9211.story.story

enum class StoryTriggerType {
    EVENT,
    MANUAL,
    ;

    companion object {
        fun fromConfig(value: String?): StoryTriggerType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EVENT
        }

        fun isSupported(value: String?): Boolean = value.isNullOrBlank() || entries.any { it.name.equals(value, true) }
    }
}
