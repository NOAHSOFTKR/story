package kr.kjh9211.arcanastory.content

import kr.kjh9211.arcanastory.action.StoryAction
import kr.kjh9211.arcanastory.content.model.ActorPolicy
import kr.kjh9211.arcanastory.content.model.BranchRef
import kr.kjh9211.arcanastory.content.model.ChapterRewards
import kr.kjh9211.arcanastory.content.model.EntryDefinition
import kr.kjh9211.arcanastory.content.model.LocationDefinition
import kr.kjh9211.arcanastory.content.model.Prerequisites
import kr.kjh9211.arcanastory.content.model.RewardComponent
import kr.kjh9211.arcanastory.content.model.StepTrigger
import kr.kjh9211.arcanastory.trigger.StoryTriggerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterParserTest {

    @Test
    fun `Chapter YAML을 typed 모델로 읽는다`() {
        val parsed = parseChapter(
            """
            id: prologue.exile
            aliases: [prologue.old]
            title: 무너진 평화
            arc: prologue
            order: 20
            prerequisites:
              chapters: [prologue.awakening]
              branches: ["prologue.awakening:trust"]
              flags: [met_chief]
            entry: { step: start, location: square }
            locations:
              square: { world: story, x: 0.5, y: 64, z: -2.5, yaw: 90 }
            steps:
              - id: start
                actor: any
                on_enter:
                  - { type: title, subtitle: "&7..." }
                  - type: sequence
                    actions:
                      - { type: wait, ticks: 40 }
                      - { type: set_flag, flag: exiled, persist: true }
                await:
                  - { type: quest_objective_complete, quest: q1, objective: o1, next: finish }
              - id: finish
                on_enter:
                  - { type: complete_chapter }
            rewards:
              first_clear:
                - { type: item, item: "arcana:dragon_core" }
              branches:
                trust:
                  - { type: experience, points: 5 }
            """,
        )

        assertTrue(parsed.issues.isEmpty(), parsed.issues.joinToString("\n"))
        val chapter = requireNotNull(parsed.value)
        assertEquals(setOf("prologue.old"), chapter.aliases)
        assertEquals(
            Prerequisites(setOf("prologue.awakening"), setOf(BranchRef("prologue.awakening", "trust")), setOf("met_chief")),
            chapter.prerequisites,
        )
        assertEquals(EntryDefinition("start", "square"), chapter.entry)
        assertEquals(LocationDefinition("square", "story", 0.5, 64.0, -2.5, 90f, 0f), chapter.locations["square"])
        assertEquals(listOf(ActorPolicy.ANY, ActorPolicy.LEADER), chapter.steps.map { it.actor })
        assertEquals(
            listOf(
                StoryAction.Title("", "&7...", 10, 70, 20),
                StoryAction.Sequence(listOf(StoryAction.Wait(40), StoryAction.SetFlag("exiled", "true", true))),
            ),
            chapter.steps[0].onEnter,
        )
        assertEquals(
            StepTrigger(StoryTriggerType.QUEST_OBJECTIVE_COMPLETE, mapOf("quest" to "q1", "objective" to "o1"), emptyList(), "finish"),
            chapter.steps[0].await.single(),
        )
        assertEquals(
            ChapterRewards(
                listOf(RewardComponent.Item("arcana:dragon_core", 1)),
                mapOf("trust" to listOf(RewardComponent.Experience(5))),
            ),
            chapter.rewards,
        )
    }

    @Test
    fun `알 수 없는 액션·키와 필수 값이 빠진 trigger를 보고한다`() {
        val parsed = parseChapter(
            """
            id: prologue.test
            entry: { step: start, location: square }
            locatoins: {}
            steps:
              - id: start
                on_enter:
                  - { type: explode }
                  - { type: message, text: hi, colour: red }
                await:
                  - { type: quest_complete }
                  - { type: teleport_player }
            """,
        )

        val errors = parsed.issues.filter { it.severity == Severity.ERROR }.map { it.path }
        val warnings = parsed.issues.filter { it.severity == Severity.WARNING }.map { it.path }
        assertEquals(listOf("steps[0].on_enter[0].type", "steps[0].await[0]", "steps[0].await[1].type"), errors)
        assertEquals(listOf("locatoins", "steps[0].on_enter[1].colour"), warnings)
        assertEquals(listOf(StoryAction.Message("hi", kr.kjh9211.arcanastory.action.MessageTarget.SELF)), requireNotNull(parsed.value).steps.single().onEnter)
    }

    @Test
    fun `entry가 없으면 오류를 남기고 entry를 null로 둔다`() {
        val parsed = parseChapter(
            """
            id: prologue.test
            steps: []
            """,
        )

        assertEquals(listOf("entry"), parsed.issues.map { it.path })
        assertNull(requireNotNull(parsed.value).entry)
    }
}
