package kr.kjh9211.arcanastory.content

import kr.kjh9211.arcanastory.action.StoryAction
import kr.kjh9211.arcanastory.content.migration.LegacyRealm
import kr.kjh9211.arcanastory.content.migration.LegacyStoryMapping
import kr.kjh9211.arcanastory.content.model.ChapterDefinition
import kr.kjh9211.arcanastory.content.model.CutsceneDefinition
import kr.kjh9211.arcanastory.content.model.DialogueChoice
import kr.kjh9211.arcanastory.content.model.DialogueDefinition
import kr.kjh9211.arcanastory.content.model.RewardComponent
import kr.kjh9211.arcanastory.content.model.RewardDelivery
import kr.kjh9211.arcanastory.content.model.RewardKeys
import kr.kjh9211.arcanastory.content.model.StepDefinition
import kr.kjh9211.arcanastory.content.model.StepTrigger
import kr.kjh9211.arcanastory.trigger.StoryTriggerType

/**
 * 콘텐츠의 참조 무결성과 ArcanaStory 정책을 검증한다.
 *
 * [questCatalog]·[keyCatalog]가 없으면 해당 외부 참조 검사는 건너뛴다(오프라인 검증).
 */
class ContentValidator(
    private val questCatalog: QuestCatalog? = null,
    private val keyCatalog: ResourceKeyCatalog? = null,
) {

    fun validate(content: LoadedContent, mapping: LegacyStoryMapping? = null): ValidationReport {
        val issues = IssueCollector()
        content.issues.forEach(issues::add)

        val chapters = indexChapters(content.chapters, issues)
        val cutsceneIds = indexCutscenes(content.cutscenes, issues)
        content.chapters.forEach { chapter ->
            ChapterValidation(chapter, chapters, cutsceneIds, questCatalog, keyCatalog, issues).run()
        }
        checkPrerequisiteCycles(content.chapters, chapters, issues)
        mapping?.let { checkMapping(it, chapters, issues) }

        return ValidationReport(issues.issues())
    }

    /** id와 alias를 모두 키로 갖는 인덱스. */
    private fun indexChapters(chapters: List<ChapterDefinition>, issues: IssueCollector): Map<String, ChapterDefinition> {
        val index = linkedMapOf<String, ChapterDefinition>()
        chapters.forEach { chapter ->
            val existing = index.putIfAbsent(chapter.id, chapter)
            if (existing != null) {
                issues.error(chapter.source, "id", "Chapter id '${chapter.id}'가 ${existing.source}와 중복됩니다")
            }
        }
        chapters.forEach { chapter ->
            chapter.aliases.forEach { alias ->
                val existing = index.putIfAbsent(alias, chapter)
                if (existing != null) {
                    issues.error(chapter.source, "aliases", "alias '$alias'가 ${existing.source}의 id 또는 alias와 충돌합니다")
                }
            }
        }
        return index
    }

    private fun indexCutscenes(cutscenes: List<CutsceneDefinition>, issues: IssueCollector): Set<String> {
        val index = linkedMapOf<String, CutsceneDefinition>()
        cutscenes.forEach { cutscene ->
            val existing = index.putIfAbsent(cutscene.id, cutscene)
            if (existing != null) {
                issues.error(cutscene.source, "id", "Cutscene id '${cutscene.id}'가 ${existing.source}와 중복됩니다")
            }
            checkCutscene(cutscene, issues)
        }
        return index.keys
    }

    private fun checkCutscene(cutscene: CutsceneDefinition, issues: IssueCollector) {
        if (!ContentIds.QUALIFIED.matches(cutscene.id)) {
            issues.error(cutscene.source, "id", "Cutscene id '${cutscene.id}'는 소문자·숫자·밑줄을 점으로 구분한 형식이어야 합니다")
        }
        if (cutscene.vanillaSteps.isEmpty()) {
            issues.error(cutscene.source, "vanilla", "vanilla step이 하나 이상 필요합니다")
        }
        cutscene.vanillaSteps.forEachIndexed { index, step ->
            val path = "vanilla[$index]"
            val type = step["type"]?.toString()?.trim()?.lowercase()
            when {
                type.isNullOrEmpty() -> issues.error(cutscene.source, path, "step에 type이 없습니다")
                type in FORBIDDEN_CUTSCENE_STEPS -> issues.error(
                    cutscene.source,
                    path,
                    "컷신에서는 '$type' step을 사용할 수 없습니다. 인벤토리 조작·명령은 checkpoint와 결합된 Story action으로 처리하세요",
                )
            }
        }
    }

    private fun checkPrerequisiteCycles(
        chapters: List<ChapterDefinition>,
        index: Map<String, ChapterDefinition>,
        issues: IssueCollector,
    ) {
        val graph = chapters.associate { chapter ->
            val required = chapter.prerequisites.chapters + chapter.prerequisites.branches.map { it.chapterId }
            chapter.id to required.mapNotNull { index[it]?.id }.filter { it != chapter.id }.distinct()
        }
        val finished = mutableSetOf<String>()
        val visiting = linkedSetOf<String>()
        val reported = mutableSetOf<Set<String>>()

        fun visit(id: String) {
            if (id in finished) return
            if (id in visiting) {
                val cycle = visiting.dropWhile { it != id } + id
                if (reported.add(cycle.toSet())) {
                    val chapter = index.getValue(id)
                    issues.error(chapter.source, "prerequisites", "선행 조건이 순환합니다: ${cycle.joinToString(" -> ")}")
                }
                return
            }
            visiting += id
            graph[id].orEmpty().forEach(::visit)
            visiting -= id
            finished += id
        }

        graph.keys.forEach(::visit)
    }

    private fun checkMapping(mapping: LegacyStoryMapping, index: Map<String, ChapterDefinition>, issues: IssueCollector) {
        for (entry in mapping.entries.values) {
            if (entry.realm != LegacyRealm.STORY) continue
            val path = "entries.${entry.legacyStoryId}"
            val chapterId = entry.chapterId
            if (chapterId == null) {
                issues.warning(mapping.source, path, "chapter가 아직 TBD입니다. 진행 데이터 이관 시 이 항목은 건너뜁니다")
                continue
            }
            val chapter = index[chapterId]
            if (chapter == null) {
                issues.error(mapping.source, "$path.chapter", "존재하지 않는 Chapter '$chapterId'")
                continue
            }
            for (reward in entry.preClaimRewards) {
                val branchId = when {
                    reward == RewardKeys.FIRST_CLEAR -> null
                    reward.endsWith(".${RewardKeys.FIRST_CLEAR}") -> reward.removeSuffix(".${RewardKeys.FIRST_CLEAR}")
                    else -> {
                        issues.error(
                            mapping.source,
                            "$path.pre_claim_rewards",
                            "'$reward'는 '${RewardKeys.FIRST_CLEAR}' 또는 '<branchId>.${RewardKeys.FIRST_CLEAR}' 형식이어야 합니다",
                        )
                        continue
                    }
                }
                if (branchId != null && branchId !in chapter.branches) {
                    issues.error(mapping.source, "$path.pre_claim_rewards", "Chapter '${chapter.id}'에 branch '$branchId'가 없습니다")
                }
            }
        }
    }

    private companion object {
        val FORBIDDEN_CUTSCENE_STEPS = setOf("clear_inventory", "clearinventory", "command", "cmd")
    }
}

private class ChapterValidation(
    private val chapter: ChapterDefinition,
    private val chapters: Map<String, ChapterDefinition>,
    private val cutsceneIds: Set<String>,
    private val questCatalog: QuestCatalog?,
    private val keyCatalog: ResourceKeyCatalog?,
    private val issues: IssueCollector,
) {
    /** questId → 처음 참조한 경로. 같은 quest 문제를 한 번만 보고하기 위함이다. */
    private val questReferences = linkedMapOf<String, String>()
    private val objectiveReferences = mutableListOf<ObjectiveReference>()

    private data class ObjectiveReference(val questId: String, val objectiveId: String, val path: String)

    fun run() {
        checkIds()
        checkPrerequisites()
        checkChoices()
        checkCheckpoints()
        checkDialogues()
        checkSteps()
        checkRewards()
        checkQuests()
    }

    private fun error(path: String, message: String) = issues.error(chapter.source, path, message)

    private fun warning(path: String, message: String) = issues.warning(chapter.source, path, message)

    private fun checkIds() {
        if (!ContentIds.QUALIFIED.matches(chapter.id)) {
            error("id", "Chapter id '${chapter.id}'는 소문자·숫자·밑줄을 점으로 구분한 형식이어야 합니다 (예: prologue.exile)")
        }
        chapter.aliases.filterNot(ContentIds.QUALIFIED::matches).forEach { error("aliases", "alias '$it'의 형식이 올바르지 않습니다") }

        fun checkLocal(path: String, ids: Collection<String>) {
            ids.filterNot(ContentIds.LOCAL::matches).forEach { error("$path.$it", "id '$it'는 소문자·숫자·밑줄만 사용할 수 있습니다") }
        }
        checkLocal("locations", chapter.locations.keys)
        checkLocal("branches", chapter.branches.keys)
        checkLocal("choices", chapter.choices.keys)
        checkLocal("checkpoints", chapter.checkpoints.keys)
        checkLocal("dialogues", chapter.dialogues.keys)
        chapter.dialogues.values.forEach { checkLocal("dialogues.${it.id}.nodes", it.nodes.keys) }
        chapter.choices.values.forEach { choice -> checkLocal("choices.${choice.id}.options", choice.options.map { it.id }) }
        chapter.steps.forEachIndexed { index, step ->
            if (!ContentIds.LOCAL.matches(step.id)) error("steps[$index].id", "id '${step.id}'는 소문자·숫자·밑줄만 사용할 수 있습니다")
        }
    }

    private fun checkPrerequisites() {
        val prerequisites = chapter.prerequisites
        prerequisites.chapters.forEach { id ->
            val target = chapters[id]
            when {
                target == null -> error("prerequisites.chapters", "존재하지 않는 Chapter '$id'")
                target === chapter -> error("prerequisites.chapters", "자기 자신을 선행 조건으로 지정할 수 없습니다")
            }
        }
        prerequisites.branches.forEach { reference ->
            val target = chapters[reference.chapterId]
            when {
                target == null -> error("prerequisites.branches", "존재하지 않는 Chapter '${reference.chapterId}'")
                reference.branchId !in target.branches ->
                    error("prerequisites.branches", "Chapter '${target.id}'에 branch '${reference.branchId}'가 없습니다")
            }
        }
        prerequisites.flags.filterNot(ContentIds.LOCAL::matches).forEach { error("prerequisites.flags", "flag id '$it'의 형식이 올바르지 않습니다") }
    }

    private fun checkChoices() {
        chapter.choices.values.forEach { choice ->
            val path = "choices.${choice.id}"
            if (choice.options.isEmpty()) error("$path.options", "선택지가 하나 이상 필요합니다")
            choice.options.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { error("$path.options", "option id '$it'가 중복됩니다") }
            choice.options.forEach { option ->
                val branchId = option.branchId
                if (branchId != null && branchId !in chapter.branches) {
                    error("$path.options", "option '${option.id}'의 branch '$branchId'가 선언되지 않았습니다")
                }
            }
        }
        val usedBranches = chapter.choices.values.flatMap { it.branchIds }.toSet()
        chapter.branches.keys.filterNot { it in usedBranches }.forEach { warning("branches.$it", "어떤 선택지에서도 사용하지 않는 branch입니다") }
    }

    private fun checkCheckpoints() {
        chapter.checkpoints.values.forEach { checkpoint ->
            val path = "checkpoints.${checkpoint.id}"
            requireLocation("$path.location", checkpoint.locationId)
            checkpoint.replayCutsceneId?.let { requireCutscene("$path.replay_cutscene", it) }
        }
    }

    private fun checkDialogues() {
        chapter.dialogues.values.forEach { dialogue ->
            val path = "dialogues.${dialogue.id}"
            if (dialogue.startNodeId !in dialogue.nodes) error("$path.start", "존재하지 않는 node '${dialogue.startNodeId}'")
            dialogue.nodes.values.forEach { node ->
                val nodePath = "$path.nodes.${node.id}"
                val next = node.next
                val choice = node.choice
                if (next != null && next !in dialogue.nodes) error("$nodePath.next", "존재하지 않는 node '$next'")
                if (next != null && choice != null) error(nodePath, "choice와 next를 함께 지정할 수 없습니다")
                if (choice != null) checkDialogueChoice(dialogue, "$nodePath.choice", choice)
                checkActions("$nodePath.actions", node.actions)
            }
        }
    }

    private fun checkDialogueChoice(dialogue: DialogueDefinition, path: String, dialogueChoice: DialogueChoice) {
        val choice = chapter.choices[dialogueChoice.choiceId]
        if (choice == null) {
            error("$path.id", "선언되지 않은 choice '${dialogueChoice.choiceId}'")
            return
        }
        val nextByOption = dialogueChoice.nextByOption ?: return
        val optionIds = choice.options.map { it.id }.toSet()
        nextByOption.keys.filterNot { it in optionIds }.forEach { error("$path.next.$it", "choice '${choice.id}'에 없는 option입니다") }
        optionIds.filterNot { it in nextByOption }.forEach {
            error("$path.next", "option '$it'의 다음 node가 지정되지 않았습니다 (대화를 끝내려면 null)")
        }
        nextByOption.forEach { (option, target) ->
            if (target != null && target !in dialogue.nodes) error("$path.next.$option", "존재하지 않는 node '$target'")
        }
    }

    private fun checkSteps() {
        val steps = chapter.steps.associateBy { it.id }
        if (chapter.steps.isEmpty()) error("steps", "step이 하나 이상 필요합니다")
        chapter.steps.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { error("steps", "step id '$it'가 중복됩니다") }

        chapter.entry?.let { entry ->
            if (entry.stepId !in steps) error("entry.step", "존재하지 않는 step '${entry.stepId}'")
            requireLocation("entry.location", entry.locationId)
        }

        chapter.steps.forEachIndexed { index, step ->
            val path = "steps[$index]"
            step.next?.let { if (it !in steps) error("$path.next", "존재하지 않는 step '$it'") }
            checkActions("$path.on_enter", step.onEnter)
            step.await.forEachIndexed { triggerIndex, trigger ->
                val triggerPath = "$path.await[$triggerIndex]"
                trigger.next?.let { if (it !in steps) error("$triggerPath.next", "존재하지 않는 step '$it'") }
                checkTrigger(triggerPath, trigger)
                checkActions("$triggerPath.actions", trigger.actions)
            }
            if (step.await.isEmpty() && step.next == null && !containsCompleteChapter(step.onEnter)) {
                warning(path, "await·next·complete_chapter가 모두 없어 진행이 이 step에서 멈춥니다")
            }
        }

        val entryStep = chapter.entry?.stepId?.let(steps::get) ?: return
        val reachable = reachableSteps(entryStep, steps)
        chapter.steps.filterNot { it.id in reachable }.forEach { warning("steps", "entry에서 도달할 수 없는 step '${it.id}'") }

        val completesInSteps = chapter.steps
            .filter { it.id in reachable }
            .any { step -> containsCompleteChapter(step.onEnter) || step.await.any { containsCompleteChapter(it.actions) } }
        val completesInDialogues = chapter.dialogues.values.any { dialogue -> dialogue.nodes.values.any { containsCompleteChapter(it.actions) } }
        if (!completesInSteps && !completesInDialogues) error("steps", "entry에서 도달 가능한 complete_chapter 액션이 없습니다")
    }

    private fun reachableSteps(entry: StepDefinition, steps: Map<String, StepDefinition>): Set<String> {
        val visited = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(entry))
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            if (!visited.add(step.id)) continue
            (listOfNotNull(step.next) + step.await.mapNotNull { it.next })
                .mapNotNull(steps::get)
                .forEach(queue::addLast)
        }
        return visited
    }

    private fun containsCompleteChapter(actions: List<StoryAction>): Boolean = actions.any { action ->
        action is StoryAction.CompleteChapter || (action is StoryAction.Sequence && containsCompleteChapter(action.actions))
    }

    private fun checkTrigger(path: String, trigger: StepTrigger) {
        val params = trigger.params
        when (trigger.type) {
            StoryTriggerType.DIALOGUE_END -> params["dialogue"]?.let {
                if (it !in chapter.dialogues) error("$path.dialogue", "선언되지 않은 dialogue '$it'")
            }
            StoryTriggerType.CUTSCENE_END -> params["cutscene"]?.let { requireCutscene("$path.cutscene", it) }
            StoryTriggerType.CHOICE_MADE -> {
                val choiceId = params["choice"] ?: return
                val choice = chapter.choices[choiceId]
                if (choice == null) {
                    error("$path.choice", "선언되지 않은 choice '$choiceId'")
                    return
                }
                val option = params["option"]
                if (option != null && choice.options.none { it.id == option }) {
                    error("$path.option", "choice '$choiceId'에 없는 option '$option'")
                }
            }
            StoryTriggerType.TIMER -> if ((params["ticks"]?.toIntOrNull() ?: 0) < 1) {
                error("$path.ticks", "ticks는 1 이상의 정수여야 합니다")
            }
            StoryTriggerType.QUEST_START,
            StoryTriggerType.QUEST_COMPLETE,
            StoryTriggerType.QUEST_FAIL,
            -> params["quest"]?.let { referenceQuest("$path.quest", it) }
            StoryTriggerType.QUEST_OBJECTIVE_COMPLETE -> {
                val questId = params["quest"] ?: return
                referenceQuest("$path.quest", questId)
                params["objective"]?.let { objectiveReferences += ObjectiveReference(questId, it, "$path.objective") }
            }
            StoryTriggerType.CUSTOM -> Unit
        }
    }

    private fun checkActions(path: String, actions: List<StoryAction>) {
        actions.forEachIndexed { index, action ->
            val actionPath = "$path[$index]"
            when (action) {
                is StoryAction.Message, is StoryAction.ActionBar, is StoryAction.DespawnNpc, is StoryAction.Fail -> Unit
                StoryAction.CompleteChapter -> Unit
                is StoryAction.Title -> if (action.title.isBlank() && action.subtitle.isBlank()) {
                    warning(actionPath, "title과 subtitle이 모두 비어 있습니다")
                }
                is StoryAction.Sound -> {
                    checkResourceKey("$actionPath.sound", ResourceKind.SOUND, action.sound)
                    if (action.volume < 0f) error("$actionPath.volume", "volume은 0 이상이어야 합니다")
                }
                is StoryAction.Particle -> {
                    checkResourceKey("$actionPath.particle", ResourceKind.PARTICLE, action.particle)
                    if (action.count < 1) error("$actionPath.count", "count는 1 이상이어야 합니다")
                }
                is StoryAction.Effect -> {
                    checkResourceKey("$actionPath.effect", ResourceKind.EFFECT, action.effect)
                    if (action.durationTicks < 1) error("$actionPath.duration", "duration(tick)은 1 이상이어야 합니다")
                    if (action.amplifier < 0) error("$actionPath.amplifier", "amplifier는 0 이상이어야 합니다")
                }
                is StoryAction.Teleport -> requireLocation("$actionPath.location", action.locationId)
                is StoryAction.Wait -> if (action.ticks < 1) error("$actionPath.ticks", "ticks는 1 이상이어야 합니다")
                is StoryAction.Sequence -> checkActions("$actionPath.actions", action.actions)
                is StoryAction.Dialogue -> if (action.dialogueId !in chapter.dialogues) {
                    error("$actionPath.dialogue", "선언되지 않은 dialogue '${action.dialogueId}'")
                }
                is StoryAction.Cutscene -> requireCutscene("$actionPath.cutscene", action.cutsceneId)
                is StoryAction.Checkpoint -> if (action.checkpointId !in chapter.checkpoints) {
                    error("$actionPath.checkpoint", "선언되지 않은 checkpoint '${action.checkpointId}'")
                }
                is StoryAction.Choice -> if (action.choiceId !in chapter.choices) {
                    error("$actionPath.choice", "선언되지 않은 choice '${action.choiceId}'")
                }
                is StoryAction.SetFlag -> checkFlag("$actionPath.flag", action.flag)
                is StoryAction.ClearFlag -> checkFlag("$actionPath.flag", action.flag)
                is StoryAction.StartQuest -> referenceQuest("$actionPath.quest", action.questId)
                is StoryAction.FailQuest -> referenceQuest("$actionPath.quest", action.questId)
                is StoryAction.ResetQuest -> referenceQuest("$actionPath.quest", action.questId)
                is StoryAction.CompleteObjective -> {
                    referenceQuest("$actionPath.quest", action.questId)
                    objectiveReferences += ObjectiveReference(action.questId, action.objectiveId, "$actionPath.objective")
                }
                is StoryAction.SpawnNpc -> requireLocation("$actionPath.location", action.locationId)
                is StoryAction.SpawnMob -> {
                    requireLocation("$actionPath.location", action.locationId)
                    if (action.amount < 1) error("$actionPath.amount", "amount는 1 이상이어야 합니다")
                }
                StoryAction.ClearInventory -> if (actions.getOrNull(index - 1) !is StoryAction.Checkpoint) {
                    error(actionPath, "clear_inventory는 같은 액션 목록에서 checkpoint 바로 뒤에만 사용할 수 있습니다")
                }
                is StoryAction.Command -> warning(actionPath, "command는 escape hatch입니다. 가능하면 typed action을 사용하세요")
            }
        }
    }

    private fun checkRewards() {
        checkRewardComponents("rewards.first_clear", chapter.rewards.firstClear)
        chapter.rewards.branchFirstClear.forEach { (branchId, components) ->
            val path = "rewards.branches.$branchId"
            if (branchId !in chapter.branches) error(path, "선언되지 않은 branch '$branchId'")
            checkRewardComponents(path, components)
        }
    }

    private fun checkRewardComponents(path: String, components: List<RewardComponent>) {
        components.forEachIndexed { index, component ->
            val componentPath = "$path[$index]"
            when (component.delivery) {
                RewardDelivery.PLAYERDATA -> Unit
                RewardDelivery.AT_MOST_ONCE ->
                    error(componentPath, "command·money처럼 중복 지급을 막을 수 없는 보상은 최초 완료 보상으로 사용할 수 없습니다")
                RewardDelivery.EXTERNAL_IDEMPOTENT ->
                    error(componentPath, "칭호·코스메틱·공용 해금 보상은 ArcanaCore 멱등 grant API가 제공되기 전까지 사용할 수 없습니다")
            }
            when (component) {
                is RewardComponent.Item -> {
                    if (!ContentIds.ITEM_ID.matches(component.item)) {
                        error("$componentPath.item", "아이템 id '${component.item}'는 namespace:id 형식이어야 합니다")
                    } else if (component.item.startsWith("minecraft:") && keyCatalog != null &&
                        !keyCatalog.contains(ResourceKind.MATERIAL, component.item)
                    ) {
                        error("$componentPath.item", "알 수 없는 아이템 '${component.item}'")
                    }
                    if (component.amount < 1) error("$componentPath.amount", "amount는 1 이상이어야 합니다")
                }
                is RewardComponent.Experience -> if (component.points < 1) error("$componentPath.points", "points는 1 이상이어야 합니다")
                is RewardComponent.Command, is RewardComponent.Money, is RewardComponent.SharedUnlock -> Unit
            }
        }
    }

    private fun checkQuests() {
        val catalog = questCatalog ?: return
        for ((questId, path) in questReferences) {
            val quest = catalog.find(questId)
            if (quest == null) {
                error(path, "Quest '$questId'를 찾을 수 없습니다")
                continue
            }
            if (!quest.scope.equals("STORY", ignoreCase = true)) {
                error(path, "Quest '$questId'의 scope가 ${quest.scope}입니다. Chapter에서 사용하는 quest는 STORY scope여야 합니다")
            }
            if (quest.hasRewards) error(path, "Quest '$questId'에 rewards가 있습니다. Story 보상은 Chapter 최초 완료 보상으로만 지급합니다")
            if (quest.abandonAllowed) error(path, "Quest '$questId'는 abandon_allowed가 true입니다. Story quest는 플레이어가 포기할 수 없어야 합니다")
            if (quest.hasBranch) error(path, "Quest '$questId'에 branch가 있습니다. 분기는 ArcanaStory choice로 처리합니다")
        }
        for (reference in objectiveReferences) {
            val quest = catalog.find(reference.questId) ?: continue
            if (reference.objectiveId !in quest.objectiveIds) {
                error(reference.path, "Quest '${reference.questId}'에 objective '${reference.objectiveId}'가 없습니다")
            }
        }
    }

    private fun referenceQuest(path: String, questId: String) {
        questReferences.putIfAbsent(questId, path)
    }

    private fun requireLocation(path: String, locationId: String) {
        if (locationId !in chapter.locations) error(path, "선언되지 않은 location '$locationId'")
    }

    private fun requireCutscene(path: String, cutsceneId: String) {
        if (cutsceneId !in cutsceneIds) error(path, "존재하지 않는 cutscene '$cutsceneId'")
    }

    private fun checkFlag(path: String, flag: String) {
        if (!ContentIds.LOCAL.matches(flag)) error(path, "flag id '$flag'는 소문자·숫자·밑줄만 사용할 수 있습니다")
    }

    private fun checkResourceKey(path: String, kind: ResourceKind, key: String) {
        if (!ContentIds.RESOURCE_KEY.matches(key)) {
            error(path, "리소스 키 '$key'의 형식이 올바르지 않습니다 (소문자 namespaced key)")
            return
        }
        if (keyCatalog != null && !keyCatalog.contains(kind, key)) error(path, "알 수 없는 ${kind.name.lowercase()} '$key'")
    }
}
