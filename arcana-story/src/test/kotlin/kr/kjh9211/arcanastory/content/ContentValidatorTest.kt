package kr.kjh9211.arcanastory.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentValidatorTest {

    private val minimalBody = """
        entry: { step: start, location: spawn }
        locations:
          spawn: { world: story, x: 0, y: 64, z: 0 }
        steps:
          - id: start
            on_enter:
              - { type: complete_chapter }
    """.trimIndent()

    private fun chapter(id: String, body: String = minimalBody, extra: String = ""): String = "id: $id\n$body\n$extra"

    private fun validate(
        chapters: List<String>,
        cutscenes: List<String> = emptyList(),
        questCatalog: QuestCatalog? = null,
        keyCatalog: ResourceKeyCatalog? = null,
    ): ValidationReport = ContentValidator(questCatalog, keyCatalog).validate(loadedContent(chapters, cutscenes))

    private fun assertNoErrors(report: ValidationReport) {
        assertTrue(report.errors.isEmpty()) { report.errors.joinToString("\n") }
    }

    private fun assertHasError(report: ValidationReport, fragment: String) {
        assertTrue(report.errors.any { fragment in it.message }) {
            "'$fragment'을 포함한 오류가 없습니다:\n" + report.issues.joinToString("\n")
        }
    }

    private fun steps(vararg actions: String): String = """
        entry: { step: start, location: spawn }
        locations:
          spawn: { world: story, x: 0, y: 64, z: 0 }
        checkpoints:
          safe: { location: spawn }
        steps:
          - id: start
            on_enter:
    """.trimIndent() + actions.joinToString("") { "\n      - $it" }

    @Test
    fun `최소 Chapter는 오류도 경고도 없다`() {
        val report = validate(listOf(chapter("prologue.test")))

        assertTrue(report.issues.isEmpty()) { report.issues.joinToString("\n") }
    }

    @Test
    fun `clear_inventory는 checkpoint 바로 뒤가 아니면 오류다`() {
        val body = steps(
            "{ type: clear_inventory }",
            "{ type: checkpoint, checkpoint: safe }",
            "{ type: clear_inventory }",
            "{ type: complete_chapter }",
        )

        val report = validate(listOf(chapter("prologue.test", body)))

        assertEquals(listOf("steps[0].on_enter[0]"), report.errors.map { it.path })
    }

    @Test
    fun `중복 지급을 막을 수 없는 보상과 공용 보상은 최초 완료 보상으로 쓸 수 없다`() {
        val rewards = """
            rewards:
              first_clear:
                - { type: item, item: "arcana:dragon_core", amount: 1 }
                - { type: experience, points: 10 }
                - { type: command, command: "give {player} diamond 1" }
                - { type: money, amount: 100 }
                - { type: title, id: world_savior }
        """.trimIndent()

        val report = validate(listOf(chapter("prologue.test", extra = rewards)))

        assertEquals(
            listOf("rewards.first_clear[2]", "rewards.first_clear[3]", "rewards.first_clear[4]"),
            report.errors.map { it.path },
        )
    }

    @Test
    fun `branch 보상은 선언된 branch에만 지정할 수 있다`() {
        val rewards = """
            rewards:
              branches:
                unknown:
                  - { type: experience, points: 1 }
        """.trimIndent()

        val report = validate(listOf(chapter("prologue.test", extra = rewards)))

        assertEquals(listOf("rewards.branches.unknown"), report.errors.map { it.path })
    }

    @Test
    fun `command 액션은 경고만 남긴다`() {
        val body = steps("{ type: command, command: \"say hi\" }", "{ type: complete_chapter }")

        val report = validate(listOf(chapter("prologue.test", body)))

        assertNoErrors(report)
        assertTrue(report.warnings.any { it.path == "steps[0].on_enter[0]" }) { report.issues.joinToString("\n") }
    }

    @Test
    fun `도달 가능한 complete_chapter가 없으면 오류다`() {
        val body = steps("{ type: message, text: hello }")

        assertHasError(validate(listOf(chapter("prologue.test", body))), "complete_chapter")
    }

    @Test
    fun `선언되지 않은 location·dialogue·cutscene·checkpoint 참조는 오류다`() {
        val body = """
            entry: { step: start, location: missing }
            locations:
              spawn: { world: story, x: 0, y: 64, z: 0 }
            steps:
              - id: start
                on_enter:
                  - { type: teleport, location: nowhere }
                  - { type: dialogue, dialogue: nobody }
                  - { type: cutscene, cutscene: prologue.none }
                  - { type: checkpoint, checkpoint: none }
                  - { type: complete_chapter }
        """.trimIndent()

        val report = validate(listOf(chapter("prologue.test", body)))

        assertEquals(
            setOf(
                "entry.location",
                "steps[0].on_enter[0].location",
                "steps[0].on_enter[1].dialogue",
                "steps[0].on_enter[2].cutscene",
                "steps[0].on_enter[3].checkpoint",
            ),
            report.errors.map { it.path }.toSet(),
        )
    }

    @Test
    fun `도달할 수 없는 step은 경고다`() {
        val body = """
            entry: { step: start, location: spawn }
            locations:
              spawn: { world: story, x: 0, y: 64, z: 0 }
            steps:
              - id: start
                on_enter:
                  - { type: complete_chapter }
              - id: orphan
                next: start
        """.trimIndent()

        val report = validate(listOf(chapter("prologue.test", body)))

        assertNoErrors(report)
        assertTrue(report.warnings.any { "orphan" in it.message }) { report.issues.joinToString("\n") }
    }

    @Test
    fun `선행 조건 순환은 오류다`() {
        val first = chapter("arc.first", extra = "prerequisites: { chapters: [arc.second] }")
        val second = chapter("arc.second", extra = "prerequisites: { chapters: [arc.first] }")

        assertHasError(validate(listOf(first, second)), "순환")
    }

    @Test
    fun `alias로 선행 조건을 참조할 수 있고 다른 Chapter id와 충돌하면 오류다`() {
        val renamed = chapter("prologue.exile", extra = "aliases: [prologue.old_exile]")
        val next = chapter("prologue.next", extra = "prerequisites: { chapters: [prologue.old_exile] }")
        assertNoErrors(validate(listOf(renamed, next)))

        val clash = chapter("prologue.other", extra = "aliases: [prologue.exile]")
        assertHasError(validate(listOf(renamed, clash)), "alias 'prologue.exile'")
    }

    @Test
    fun `대화 choice의 next가 모든 option을 다루지 않으면 오류다`() {
        val body = """
            entry: { step: start, location: spawn }
            locations:
              spawn: { world: story, x: 0, y: 64, z: 0 }
            branches:
              trust: { title: 믿는다 }
              distrust: { title: 의심한다 }
            choices:
              chief:
                options:
                  - { id: trust, text: 믿는다, branch: trust }
                  - { id: distrust, text: 의심한다, branch: distrust }
            dialogues:
              talk:
                start: ask
                nodes:
                  ask:
                    lines: ["믿겠느냐?"]
                    choice: { id: chief, next: { trust: null } }
            steps:
              - id: start
                on_enter:
                  - { type: dialogue, dialogue: talk }
                  - { type: complete_chapter }
        """.trimIndent()

        assertHasError(validate(listOf(chapter("prologue.test", body))), "option 'distrust'")
    }

    @Test
    fun `컷신에서 clear_inventory와 command step은 오류다`() {
        val cutscene = """
            id: prologue.wipe
            vanilla:
              - { type: wait, ticks: 20 }
              - { type: clear_inventory }
              - { type: command, command: "say hi" }
        """.trimIndent()

        val report = validate(listOf(chapter("prologue.test")), listOf(cutscene))

        assertEquals(listOf("vanilla[1]", "vanilla[2]"), report.errors.map { it.path })
    }

    @Test
    fun `Chapter가 쓰는 Quest는 STORY scope이고 보상·포기·분기가 없어야 한다`() {
        val body = """
            entry: { step: start, location: spawn }
            locations:
              spawn: { world: story, x: 0, y: 64, z: 0 }
            steps:
              - id: start
                on_enter:
                  - { type: start_quest, quest: survival_quest }
                  - { type: start_quest, quest: loose_quest }
                  - { type: start_quest, quest: good_quest }
                  - { type: start_quest, quest: missing_quest }
                await:
                  - { type: quest_objective_complete, quest: good_quest, objective: nope, next: finish }
              - id: finish
                on_enter:
                  - { type: complete_chapter }
        """.trimIndent()
        val quests = mapOf(
            "survival_quest" to StoryQuestInfo("survival_quest", "SURVIVAL", setOf("a"), hasRewards = false, abandonAllowed = false, hasBranch = false),
            "loose_quest" to StoryQuestInfo("loose_quest", "STORY", setOf("a"), hasRewards = true, abandonAllowed = true, hasBranch = true),
            "good_quest" to StoryQuestInfo("good_quest", "STORY", setOf("talk"), hasRewards = false, abandonAllowed = false, hasBranch = false),
        )

        val report = validate(listOf(chapter("prologue.test", body)), questCatalog = QuestCatalog { quests[it] })

        assertEquals(
            listOf(
                "steps[0].on_enter[0].quest",
                "steps[0].on_enter[1].quest",
                "steps[0].on_enter[1].quest",
                "steps[0].on_enter[1].quest",
                "steps[0].on_enter[3].quest",
                "steps[0].await[0].objective",
            ),
            report.errors.map { it.path },
        )
    }

    @Test
    fun `리소스 키 catalog에 없는 사운드는 오류다`() {
        val body = steps(
            "{ type: sound, sound: \"minecraft:entity.dragon.growl\" }",
            "{ type: sound, sound: \"minecraft:entity.ender_dragon.growl\" }",
            "{ type: complete_chapter }",
        )
        val catalog = ResourceKeyCatalog { kind, key -> kind == ResourceKind.SOUND && key == "minecraft:entity.ender_dragon.growl" }

        val report = validate(listOf(chapter("prologue.test", body)), keyCatalog = catalog)

        assertEquals(listOf("steps[0].on_enter[0].sound"), report.errors.map { it.path })
    }
}
