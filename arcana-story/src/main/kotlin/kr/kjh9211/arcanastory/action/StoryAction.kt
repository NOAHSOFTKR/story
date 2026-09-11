package kr.kjh9211.arcanastory.action

/** Chapter 콘텐츠에서 선언하는 typed action. 실행은 ActionExecutor가 담당한다. */
sealed interface StoryAction {
    data class Message(val text: String, val target: MessageTarget) : StoryAction

    data class Title(
        val title: String,
        val subtitle: String,
        val fadeIn: Int,
        val stay: Int,
        val fadeOut: Int,
    ) : StoryAction

    data class ActionBar(val text: String) : StoryAction

    data class Sound(val sound: String, val volume: Float, val pitch: Float) : StoryAction

    data class Particle(
        val particle: String,
        val count: Int,
        val offsetX: Double,
        val offsetY: Double,
        val offsetZ: Double,
        val speed: Double,
    ) : StoryAction

    data class Effect(val effect: String, val durationTicks: Int, val amplifier: Int) : StoryAction

    data class Teleport(val locationId: String) : StoryAction

    data class Wait(val ticks: Int) : StoryAction

    data class Sequence(val actions: List<StoryAction>) : StoryAction

    data class Dialogue(val dialogueId: String) : StoryAction

    data class Cutscene(val cutsceneId: String) : StoryAction

    data class Checkpoint(val checkpointId: String) : StoryAction

    data class Choice(val choiceId: String) : StoryAction

    /** [persist]가 true면 Chapter 완료 시 History에 영구 commit되고, 아니면 세션 안에서만 유효하다. */
    data class SetFlag(val flag: String, val value: String, val persist: Boolean) : StoryAction

    data class ClearFlag(val flag: String) : StoryAction

    data class StartQuest(val questId: String) : StoryAction

    data class CompleteObjective(val questId: String, val objectiveId: String) : StoryAction

    data class FailQuest(val questId: String) : StoryAction

    data class ResetQuest(val questId: String) : StoryAction

    data class SpawnNpc(val npc: String, val locationId: String) : StoryAction

    data class DespawnNpc(val npc: String) : StoryAction

    data class SpawnMob(val mob: String, val locationId: String, val amount: Int) : StoryAction

    /** 인벤토리를 영구 삭제한다. 복구 기준이 되도록 checkpoint 바로 뒤에서만 허용된다. */
    data object ClearInventory : StoryAction

    /** 직전 checkpoint(없으면 Chapter entry)로 되돌린다. */
    data class Fail(val reason: String) : StoryAction

    data object CompleteChapter : StoryAction

    /** typed action으로 표현할 수 없는 경우를 위한 escape hatch. 콘솔 권한으로 실행된다. */
    data class Command(val command: String) : StoryAction
}

enum class MessageTarget {
    SELF,
    SESSION,
}
