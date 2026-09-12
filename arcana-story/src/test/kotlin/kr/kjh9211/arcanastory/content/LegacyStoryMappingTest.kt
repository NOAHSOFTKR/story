package kr.kjh9211.arcanastory.content

import kr.kjh9211.arcanastory.content.migration.LegacyRealm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyStoryMappingTest {

    private val loader = ContentLoader()

    @Test
    fun `번들 매핑은 모든 레거시 story를 확정된 서버 귀속으로 분류한다`() {
        val result = loader.loadBundledMapping()

        assertTrue(result.issues.isEmpty()) { result.issues.joinToString("\n") }
        val realms = requireNotNull(result.mapping).entries.mapValues { it.value.realm }
        LEGACY_STORY_IDS.forEach { id -> assertTrue(id in realms) { "매핑에 없는 레거시 story: $id" } }

        listOf("arcana-town-create", "arcana-town-claim", "arcana-town-add-resident", "arcana-nation-create")
            .forEach { assertEquals(LegacyRealm.SURVIVAL, realms[it], it) }
        listOf("arcana-sword-craft", "arcana-luminite-equip")
            .forEach { assertEquals(LegacyRealm.STORY, realms[it], it) }
        listOf("arcana-town-transaction", "arcana-nation-transaction")
            .forEach { assertEquals(LegacyRealm.REMOVED, realms[it], it) }
    }

    @Test
    fun `Chapter가 TBD인 항목은 경고만 남긴다`() {
        val mapping = requireNotNull(loader.loadBundledMapping().mapping)

        val report = ContentValidator().validate(LoadedContent(emptyList(), emptyList(), emptyList()), mapping)

        assertTrue(report.errors.isEmpty()) { report.errors.joinToString("\n") }
        assertEquals(mapping.entries.values.count { it.realm == LegacyRealm.STORY }, report.warnings.size)
    }

    @Test
    fun `realm별 chapter와 선지급 보상 규칙을 검사한다`() {
        val result = loader.loadMapping(
            """
            version: 1
            entries:
              story-missing-chapter: { realm: STORY }
              survival-with-chapter: { realm: SURVIVAL, chapter: prologue.exile }
              removed-with-rewards: { realm: REMOVED, pre_claim_rewards: [first_clear] }
              unknown-realm: { realm: NETHER }
              story-ok: { realm: STORY, chapter: prologue.exile, pre_claim_rewards: [first_clear, distrust.first_clear, bad_key] }
              story-unknown: { realm: STORY, chapter: prologue.none }
            """.trimIndent(),
            "mapping.yml",
        )
        assertEquals(
            listOf(
                "entries.story-missing-chapter.chapter",
                "entries.survival-with-chapter.chapter",
                "entries.removed-with-rewards.pre_claim_rewards",
                "entries.unknown-realm.realm",
            ),
            result.issues.map { it.path },
        )

        val exile = parseChapter(
            """
            id: prologue.exile
            entry: { step: start, location: spawn }
            locations:
              spawn: { world: story, x: 0, y: 64, z: 0 }
            branches:
              distrust: { title: 의심한다 }
            steps:
              - id: start
                on_enter:
                  - { type: complete_chapter }
            """,
        )
        val report = ContentValidator().validate(
            LoadedContent(listOf(requireNotNull(exile.value)), emptyList(), emptyList()),
            requireNotNull(result.mapping),
        )

        assertEquals(
            listOf("entries.story-ok.pre_claim_rewards", "entries.story-unknown.chapter"),
            report.errors.map { it.path },
        )
    }

    private companion object {
        // legacy-story의 actions, stories/example.yml, stories/pending 디렉터리에 있는 story id.
        val LEGACY_STORY_IDS = listOf(
            "arcana-dungeon-discover",
            "arcana-final-boss",
            "arcana-first-battle",
            "arcana-luminite-equip",
            "arcana-memory-restore",
            "arcana-nation-create",
            "arcana-nation-transaction",
            "arcana-prologue-discovery",
            "arcana-prologue-exile",
            "arcana-sword-craft",
            "arcana-town-add-resident",
            "arcana-town-claim",
            "arcana-town-create",
            "arcana-town-transaction",
            "arcana-truth-discover",
            "arcana-world-saved",
            "arcana-towny",
            "arcana-chapter-1",
            "arcana-chapter-2",
        )
    }
}
