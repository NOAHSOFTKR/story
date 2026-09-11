package kr.kjh9211.arcanastory.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SampleContentTest {

    @Test
    fun `저장소 샘플 콘텐츠는 경고 없이 검증을 통과한다`() {
        val root = File("samples/content")
        assertTrue(root.isDirectory) { "samples/content를 찾을 수 없습니다 (working dir: ${File("").absolutePath})" }

        val content = ContentLoader().load(root)
        val report = ContentValidator().validate(content)

        assertEquals(setOf("prologue.awakening", "prologue.exile"), content.chapters.map { it.id }.toSet())
        assertEquals(listOf("prologue.memory_wipe"), content.cutscenes.map { it.id })
        assertTrue(report.issues.isEmpty()) { report.issues.joinToString("\n") }
    }
}
