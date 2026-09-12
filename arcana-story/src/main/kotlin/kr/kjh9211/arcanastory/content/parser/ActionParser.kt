package kr.kjh9211.arcanastory.content.parser

import kr.kjh9211.arcanastory.action.MessageTarget
import kr.kjh9211.arcanastory.action.StoryAction
import kr.kjh9211.arcanastory.content.yaml.YamlNode

internal object ActionParser {

    fun parseList(node: YamlNode): List<StoryAction> = node.elements().mapNotNull(::parse)

    fun parse(node: YamlNode): StoryAction? {
        if (!node.requireMap()) return null
        val typeNode = node.child("type")
        val type = typeNode.requiredString() ?: return null
        val spec = specs[type.trim().lowercase()]
        if (spec == null) {
            typeNode.addError("알 수 없는 액션 타입 '$type'")
            return null
        }
        node.warnUnknownKeys(spec.keys + "type")
        return spec.build(node)
    }

    private class Spec(val keys: Set<String>, val build: (YamlNode) -> StoryAction?)

    private fun reference(key: String, factory: (String) -> StoryAction) =
        Spec(setOf(key)) { node -> node.child(key).requiredString()?.let(factory) }

    private val specs: Map<String, Spec> = mapOf(
        "message" to Spec(setOf("text", "target")) { node ->
            val text = node.child("text").requiredString() ?: return@Spec null
            StoryAction.Message(text, parseMessageTarget(node.child("target")))
        },
        "title" to Spec(setOf("title", "subtitle", "fade_in", "stay", "fade_out")) { node ->
            StoryAction.Title(
                title = node.child("title").string().orEmpty(),
                subtitle = node.child("subtitle").string().orEmpty(),
                fadeIn = node.child("fade_in").int(10),
                stay = node.child("stay").int(70),
                fadeOut = node.child("fade_out").int(20),
            )
        },
        "actionbar" to reference("text") { StoryAction.ActionBar(it) },
        "sound" to Spec(setOf("sound", "volume", "pitch")) { node ->
            val sound = node.child("sound").requiredString() ?: return@Spec null
            StoryAction.Sound(sound, node.child("volume").float(1f), node.child("pitch").float(1f))
        },
        "particle" to Spec(setOf("particle", "count", "offset_x", "offset_y", "offset_z", "speed")) { node ->
            val particle = node.child("particle").requiredString() ?: return@Spec null
            StoryAction.Particle(
                particle = particle,
                count = node.child("count").int(1),
                offsetX = node.child("offset_x").double(0.0),
                offsetY = node.child("offset_y").double(0.0),
                offsetZ = node.child("offset_z").double(0.0),
                speed = node.child("speed").double(0.0),
            )
        },
        "effect" to Spec(setOf("effect", "duration", "amplifier")) { node ->
            val effect = node.child("effect").requiredString()
            val duration = node.child("duration").requiredInt()
            if (effect == null || duration == null) return@Spec null
            StoryAction.Effect(effect, duration, node.child("amplifier").int(0))
        },
        "teleport" to reference("location") { StoryAction.Teleport(it) },
        "wait" to Spec(setOf("ticks")) { node -> node.child("ticks").requiredInt()?.let { StoryAction.Wait(it) } },
        "sequence" to Spec(setOf("actions")) { node -> StoryAction.Sequence(parseList(node.child("actions"))) },
        "dialogue" to reference("dialogue") { StoryAction.Dialogue(it) },
        "cutscene" to reference("cutscene") { StoryAction.Cutscene(it) },
        "checkpoint" to reference("checkpoint") { StoryAction.Checkpoint(it) },
        "choice" to reference("choice") { StoryAction.Choice(it) },
        "set_flag" to Spec(setOf("flag", "value", "persist")) { node ->
            val flag = node.child("flag").requiredString() ?: return@Spec null
            StoryAction.SetFlag(flag, node.child("value").string() ?: "true", node.child("persist").boolean(false))
        },
        "clear_flag" to reference("flag") { StoryAction.ClearFlag(it) },
        "start_quest" to reference("quest") { StoryAction.StartQuest(it) },
        "complete_objective" to Spec(setOf("quest", "objective")) { node ->
            val quest = node.child("quest").requiredString()
            val objective = node.child("objective").requiredString()
            if (quest == null || objective == null) return@Spec null
            StoryAction.CompleteObjective(quest, objective)
        },
        "fail_quest" to reference("quest") { StoryAction.FailQuest(it) },
        "reset_quest" to reference("quest") { StoryAction.ResetQuest(it) },
        "spawn_npc" to Spec(setOf("npc", "location")) { node ->
            val npc = node.child("npc").requiredString()
            val location = node.child("location").requiredString()
            if (npc == null || location == null) return@Spec null
            StoryAction.SpawnNpc(npc, location)
        },
        "despawn_npc" to reference("npc") { StoryAction.DespawnNpc(it) },
        "spawn_mob" to Spec(setOf("mob", "location", "amount")) { node ->
            val mob = node.child("mob").requiredString()
            val location = node.child("location").requiredString()
            if (mob == null || location == null) return@Spec null
            StoryAction.SpawnMob(mob, location, node.child("amount").int(1))
        },
        "clear_inventory" to Spec(emptySet()) { StoryAction.ClearInventory },
        "fail" to Spec(setOf("reason")) { node -> StoryAction.Fail(node.child("reason").string().orEmpty()) },
        "complete_chapter" to Spec(emptySet()) { StoryAction.CompleteChapter },
        "command" to reference("command") { StoryAction.Command(it) },
    )

    private fun parseMessageTarget(node: YamlNode): MessageTarget = when (val raw = node.string()?.trim()?.lowercase()) {
        null, "self" -> MessageTarget.SELF
        "session" -> MessageTarget.SESSION
        else -> {
            node.addError("target은 self 또는 session이어야 합니다 ('$raw')")
            MessageTarget.SELF
        }
    }
}
