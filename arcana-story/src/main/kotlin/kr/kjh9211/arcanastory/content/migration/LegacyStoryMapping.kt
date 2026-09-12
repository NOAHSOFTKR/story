package kr.kjh9211.arcanastory.content.migration

import kr.kjh9211.arcanastory.content.model.RewardKeys
import kr.kjh9211.arcanastory.content.yaml.YamlNode

/** 레거시 `story` 단계가 새 구조에서 어느 서버에 귀속되는지. */
enum class LegacyRealm {
    STORY,
    SURVIVAL,

    /** Story에서 제거된 단계(예: town/nation transaction). 이관하지 않는다. */
    REMOVED,
}

data class LegacyMappingEntry(
    val legacyStoryId: String,
    val realm: LegacyRealm,
    /** STORY이면서 Chapter가 아직 확정되지 않았으면(TBD) null. */
    val chapterId: String?,
    /** 레거시에서 이미 지급한 것으로 보고 이관 시 claim만 기록할 보상 키(`first_clear`, `<branchId>.first_clear`). */
    val preClaimRewards: List<String>,
)

/** `legacyStoryId → chapterId` 매핑. Chapter 구성·번호는 콘텐츠 기획에 따라 바뀔 수 있어 콘텐츠와 분리해 관리한다. */
data class LegacyStoryMapping(
    val version: Int,
    val entries: Map<String, LegacyMappingEntry>,
    val source: String,
)

internal object LegacyStoryMappingParser {
    const val TBD = "TBD"

    fun parse(root: YamlNode): LegacyStoryMapping? {
        if (!root.requireMap()) return null
        root.warnUnknownKeys(setOf("version", "entries"))
        val version = root.child("version").requiredInt() ?: return null
        val entries = linkedMapOf<String, LegacyMappingEntry>()
        root.child("entries").entries().forEach { (legacyStoryId, node) ->
            parseEntry(legacyStoryId, node)?.let { entries[legacyStoryId] = it }
        }
        return LegacyStoryMapping(version, entries, root.source)
    }

    private fun parseEntry(legacyStoryId: String, node: YamlNode): LegacyMappingEntry? {
        if (!node.requireMap()) return null
        node.warnUnknownKeys(setOf("realm", "chapter", "pre_claim_rewards"))

        val realmNode = node.child("realm")
        val rawRealm = realmNode.requiredString() ?: return null
        val realm = LegacyRealm.entries.firstOrNull { it.name.equals(rawRealm.trim(), ignoreCase = true) }
        if (realm == null) {
            realmNode.addError("realm은 STORY, SURVIVAL, REMOVED 중 하나여야 합니다 ('$rawRealm')")
            return null
        }

        val chapterNode = node.child("chapter")
        val rawChapter = chapterNode.string()?.trim()?.takeIf { it.isNotEmpty() }
        val rewardsNode = node.child("pre_claim_rewards")

        if (realm != LegacyRealm.STORY) {
            if (rawChapter != null) {
                chapterNode.addError("$realm 항목에는 chapter를 지정할 수 없습니다")
                return null
            }
            if (rewardsNode.isPresent) {
                rewardsNode.addError("$realm 항목에는 pre_claim_rewards를 지정할 수 없습니다")
                return null
            }
            return LegacyMappingEntry(legacyStoryId, realm, null, emptyList())
        }

        if (rawChapter == null) {
            chapterNode.addError("STORY 항목에는 chapter가 필요합니다 (미정이면 $TBD)")
            return null
        }
        val preClaimRewards = if (rewardsNode.isPresent) rewardsNode.stringList() else listOf(RewardKeys.FIRST_CLEAR)
        return LegacyMappingEntry(legacyStoryId, realm, rawChapter.takeUnless { it == TBD }, preClaimRewards)
    }
}
