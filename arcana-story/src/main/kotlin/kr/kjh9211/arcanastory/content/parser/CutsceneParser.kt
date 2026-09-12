package kr.kjh9211.arcanastory.content.parser

import kr.kjh9211.arcanastory.content.model.CutsceneDefinition
import kr.kjh9211.arcanastory.content.yaml.YamlNode

internal object CutsceneParser {

    fun parse(root: YamlNode): CutsceneDefinition? {
        if (!root.requireMap()) return null
        root.warnUnknownKeys(setOf("id", "name", "freeze", "vanilla", "mod"))
        val id = root.child("id").requiredString() ?: return null

        // vanilla step의 세부 필드는 renderer adapter(CutThin 형식)가 해석하므로 원본 맵으로 보관한다.
        val steps = root.child("vanilla").elements().mapNotNull { step ->
            if (!step.requireMap()) {
                null
            } else {
                (step.value as Map<*, *>).entries.associate { (key, value) -> key.toString() to value }
            }
        }

        val mod = root.child("mod")
        mod.warnUnknownKeys(setOf("track"))
        val track = if (mod.expectMap()) mod.child("track").string() else null

        return CutsceneDefinition(
            id = id,
            name = root.child("name").string() ?: id,
            freeze = root.child("freeze").boolean(true),
            vanillaSteps = steps,
            modTrack = track,
            source = root.source,
        )
    }
}
