package kr.kjh9211.arcanastory.content.model

import kr.kjh9211.arcanastory.action.StoryAction
import kr.kjh9211.arcanastory.trigger.StoryTriggerType

data class ChapterDefinition(
    val id: String,
    /** 이전에 쓰던 id. id를 바꿔도 History가 계속 인식되도록 한다. */
    val aliases: Set<String>,
    val title: String,
    val arc: String?,
    /** 표시 순서. History 키에는 사용하지 않는다. */
    val order: Int,
    val prerequisites: Prerequisites,
    /** 필수 값이 빠져 파싱하지 못했으면 null(오류는 이미 기록됨). */
    val entry: EntryDefinition?,
    val locations: Map<String, LocationDefinition>,
    val branches: Map<String, BranchDefinition>,
    val choices: Map<String, ChoiceDefinition>,
    val checkpoints: Map<String, CheckpointDefinition>,
    val dialogues: Map<String, DialogueDefinition>,
    val steps: List<StepDefinition>,
    val rewards: ChapterRewards,
    val source: String,
)

data class Prerequisites(
    val chapters: Set<String>,
    val branches: Set<BranchRef>,
    val flags: Set<String>,
) {
    companion object {
        val NONE = Prerequisites(emptySet(), emptySet(), emptySet())
    }
}

/** YAML 표기는 `<chapterId>:<branchId>`. */
data class BranchRef(val chapterId: String, val branchId: String)

data class EntryDefinition(val stepId: String, val locationId: String)

data class LocationDefinition(
    val id: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

data class BranchDefinition(val id: String, val title: String)

data class ChoiceDefinition(
    val id: String,
    val prompt: String,
    val options: List<ChoiceOption>,
) {
    /** 이 선택으로 갈 수 있는 branch 전체. "모든 가능성을 본 자" 같은 도전과제 판정의 기준이다. */
    val branchIds: Set<String>
        get() = options.mapNotNull { it.branchId }.toSet()
}

data class ChoiceOption(val id: String, val text: String, val branchId: String?)

data class CheckpointDefinition(
    val id: String,
    val locationId: String,
    /** 복구 시 처음부터 다시 재생할 컷신. */
    val replayCutsceneId: String?,
)

data class DialogueDefinition(
    val id: String,
    val startNodeId: String,
    val nodes: Map<String, DialogueNode>,
)

data class DialogueNode(
    val id: String,
    val speaker: String?,
    val lines: List<String>,
    val actions: List<StoryAction>,
    val next: String?,
    val choice: DialogueChoice?,
)

data class DialogueChoice(
    val choiceId: String,
    /** option id → 다음 node id(null이면 대화 종료). 맵 자체가 null이면 모든 option에서 대화가 끝난다. */
    val nextByOption: Map<String, String?>?,
)

enum class ActorPolicy {
    /** Session Leader의 이벤트만 step을 진행시킨다(Solo에서는 본인). */
    LEADER,

    /** 참가자 누구의 이벤트든 진행시킨다. */
    ANY,

    /** CONNECTED 참가자 전원이 충족해야 진행한다. */
    ALL,
}

data class StepDefinition(
    val id: String,
    val actor: ActorPolicy,
    val onEnter: List<StoryAction>,
    val await: List<StepTrigger>,
    val next: String?,
)

data class StepTrigger(
    val type: StoryTriggerType,
    val params: Map<String, String>,
    val actions: List<StoryAction>,
    /** null이면 소속 step의 next를 따른다. */
    val next: String?,
)
