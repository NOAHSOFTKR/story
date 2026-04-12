package kr.kjh9211.story.story

enum class ActionType {
    MESSAGE,
    COMMANDS,
    ;

    companion object {
        fun fromConfig(value: String?): ActionType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MESSAGE
        }
    }
}
