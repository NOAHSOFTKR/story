package kr.kjh9211.arcanastory.content.parser

import kr.kjh9211.arcanastory.content.model.ActorPolicy
import kr.kjh9211.arcanastory.content.model.BranchDefinition
import kr.kjh9211.arcanastory.content.model.BranchRef
import kr.kjh9211.arcanastory.content.model.ChapterDefinition
import kr.kjh9211.arcanastory.content.model.ChapterRewards
import kr.kjh9211.arcanastory.content.model.CheckpointDefinition
import kr.kjh9211.arcanastory.content.model.ChoiceDefinition
import kr.kjh9211.arcanastory.content.model.ChoiceOption
import kr.kjh9211.arcanastory.content.model.DialogueChoice
import kr.kjh9211.arcanastory.content.model.DialogueDefinition
import kr.kjh9211.arcanastory.content.model.DialogueNode
import kr.kjh9211.arcanastory.content.model.EntryDefinition
import kr.kjh9211.arcanastory.content.model.LocationDefinition
import kr.kjh9211.arcanastory.content.model.Prerequisites
import kr.kjh9211.arcanastory.content.model.StepDefinition
import kr.kjh9211.arcanastory.content.model.StepTrigger
import kr.kjh9211.arcanastory.content.yaml.YamlNode
import kr.kjh9211.arcanastory.trigger.StoryTriggerType

/** Chapter 파일 1개를 모델로 읽는다. 구조 오류는 기록만 하고, 참조·정책 검증은 ContentValidator가 한다. */
internal object ChapterParser {
    private val TOP_LEVEL_KEYS = setOf(
        "id", "aliases", "title", "arc", "order", "prerequisites", "entry", "locations",
        "branches", "choices", "checkpoints", "dialogues", "steps", "rewards",
    )

    fun parse(root: YamlNode): ChapterDefinition? {
        if (!root.requireMap()) return null
        root.warnUnknownKeys(TOP_LEVEL_KEYS)
        val id = root.child("id").requiredString() ?: return null
        val rewards = root.child("rewards")
        return ChapterDefinition(
            id = id,
            aliases = root.child("aliases").stringList().toSet(),
            title = root.child("title").string() ?: id,
            arc = root.child("arc").string(),
            order = root.child("order").int(0),
            prerequisites = parsePrerequisites(root.child("prerequisites")),
            entry = parseEntry(root.child("entry")),
            locations = root.child("locations").definitions(::parseLocation),
            branches = root.child("branches").definitions(::parseBranch),
            choices = root.child("choices").definitions(::parseChoice),
            checkpoints = root.child("checkpoints").definitions(::parseCheckpoint),
            dialogues = root.child("dialogues").definitions(::parseDialogue),
            steps = root.child("steps").elements().mapNotNull(::parseStep),
            rewards = if (rewards.isPresent) RewardParser.parse(rewards) else ChapterRewards.NONE,
            source = root.source,
        )
    }

    private fun <T : Any> YamlNode.definitions(parse: (String, YamlNode) -> T?): Map<String, T> {
        val result = linkedMapOf<String, T>()
        entries().forEach { (key, node) -> parse(key, node)?.let { result[key] = it } }
        return result
    }

    private fun parsePrerequisites(node: YamlNode): Prerequisites {
        if (!node.expectMap()) return Prerequisites.NONE
        node.warnUnknownKeys(setOf("chapters", "branches", "flags"))
        val branches = node.child("branches").elements().mapNotNull { element ->
            val raw = element.string() ?: return@mapNotNull null
            val chapterId = raw.substringBeforeLast(':', "")
            val branchId = raw.substringAfterLast(':', "")
            if (chapterId.isEmpty() || branchId.isEmpty()) {
                element.addError("'<chapterId>:<branchId>' 형식이어야 합니다")
                null
            } else {
                BranchRef(chapterId, branchId)
            }
        }
        return Prerequisites(
            chapters = node.child("chapters").stringList().toSet(),
            branches = branches.toSet(),
            flags = node.child("flags").stringList().toSet(),
        )
    }

    private fun parseEntry(node: YamlNode): EntryDefinition? {
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("step", "location"))
        val step = node.child("step").requiredString()
        val location = node.child("location").requiredString()
        return if (step != null && location != null) EntryDefinition(step, location) else null
    }

    private fun parseLocation(id: String, node: YamlNode): LocationDefinition? {
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("world", "x", "y", "z", "yaw", "pitch"))
        val world = node.child("world").requiredString()
        val x = node.child("x").requiredDouble()
        val y = node.child("y").requiredDouble()
        val z = node.child("z").requiredDouble()
        if (world == null || x == null || y == null || z == null) return null
        return LocationDefinition(id, world, x, y, z, node.child("yaw").float(0f), node.child("pitch").float(0f))
    }

    private fun parseBranch(id: String, node: YamlNode): BranchDefinition? {
        val shorthandTitle = node.value as? String
        if (shorthandTitle != null) return BranchDefinition(id, shorthandTitle)
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("title"))
        return BranchDefinition(id, node.child("title").string() ?: id)
    }

    private fun parseChoice(id: String, node: YamlNode): ChoiceDefinition? {
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("prompt", "options"))
        val options = node.child("options").elements().mapNotNull { option ->
            if (!option.requireMap()) return@mapNotNull null
            option.warnUnknownKeys(setOf("id", "text", "branch"))
            val optionId = option.child("id").requiredString()
            val text = option.child("text").requiredString()
            if (optionId == null || text == null) null else ChoiceOption(optionId, text, option.child("branch").string())
        }
        return ChoiceDefinition(id, node.child("prompt").string().orEmpty(), options)
    }

    private fun parseCheckpoint(id: String, node: YamlNode): CheckpointDefinition? {
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("location", "replay_cutscene"))
        val location = node.child("location").requiredString() ?: return null
        return CheckpointDefinition(id, location, node.child("replay_cutscene").string())
    }

    private fun parseDialogue(id: String, node: YamlNode): DialogueDefinition? {
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("start", "nodes"))
        val start = node.child("start").requiredString()
        val nodes = node.child("nodes").definitions(::parseDialogueNode)
        return start?.let { DialogueDefinition(id, it, nodes) }
    }

    private fun parseDialogueNode(id: String, node: YamlNode): DialogueNode? {
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("speaker", "lines", "actions", "next", "choice"))
        val linesNode = node.child("lines")
        val lines = (linesNode.value as? String)?.let { listOf(it) } ?: linesNode.stringList()
        return DialogueNode(
            id = id,
            speaker = node.child("speaker").string(),
            lines = lines,
            actions = ActionParser.parseList(node.child("actions")),
            next = node.child("next").string(),
            choice = parseDialogueChoice(node.child("choice")),
        )
    }

    private fun parseDialogueChoice(node: YamlNode): DialogueChoice? {
        if (!node.expectMap()) return null
        node.warnUnknownKeys(setOf("id", "next"))
        val choiceId = node.child("id").requiredString() ?: return null
        val nextNode = node.child("next")
        val nextByOption = if (nextNode.isPresent) {
            nextNode.entries().associate { (option, target) -> option to target.string() }
        } else {
            null
        }
        return DialogueChoice(choiceId, nextByOption)
    }

    private fun parseStep(node: YamlNode): StepDefinition? {
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("id", "actor", "on_enter", "await", "next"))
        val id = node.child("id").requiredString() ?: return null
        return StepDefinition(
            id = id,
            actor = parseActor(node.child("actor")),
            onEnter = ActionParser.parseList(node.child("on_enter")),
            await = node.child("await").elements().mapNotNull(::parseTrigger),
            next = node.child("next").string(),
        )
    }

    private fun parseActor(node: YamlNode): ActorPolicy = when (val raw = node.string()?.trim()?.lowercase()) {
        null, "leader" -> ActorPolicy.LEADER
        "any" -> ActorPolicy.ANY
        "all" -> ActorPolicy.ALL
        else -> {
            node.addError("actor는 leader, any, all 중 하나여야 합니다 ('$raw')")
            ActorPolicy.LEADER
        }
    }

    private fun parseTrigger(node: YamlNode): StepTrigger? {
        if (!node.requireMap()) return null
        val typeNode = node.child("type")
        val rawType = typeNode.requiredString() ?: return null
        val type = StoryTriggerType.fromYaml(rawType)
        if (type == null) {
            typeNode.addError("알 수 없는 트리거 타입 '$rawType'")
            return null
        }

        val params = linkedMapOf<String, String>()
        node.entries().forEach { (key, child) ->
            when (key) {
                "type", "actions", "next" -> Unit
                in type.requiredParams, in type.optionalParams -> child.string()?.let { params[key] = it }
                else -> child.addWarning("'${type.yamlName}' 트리거에서 사용하지 않는 키입니다")
            }
        }
        val missing = type.requiredParams.filterNot { it in params }
        if (missing.isNotEmpty()) {
            node.addError("'${type.yamlName}' 트리거에 필수 값이 없습니다: ${missing.joinToString()}")
            return null
        }
        return StepTrigger(type, params, ActionParser.parseList(node.child("actions")), node.child("next").string())
    }
}
