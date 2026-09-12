package kr.kjh9211.arcanastory.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentLoaderTest {

    @Test
    fun `chapters와 cutscenes 아래의 YAML만 재귀로 읽는다`(@TempDir root: File) {
        write(root, "chapters/prologue/a.yml", "id: prologue.a\nentry: { step: s, location: l }\nsteps: []")
        write(root, "chapters/prologue/readme.txt", "ignored")
        write(root, "cutscenes/prologue/wipe.yaml", "id: prologue.wipe\nvanilla:\n  - { type: wait, ticks: 1 }")
        write(root, "other/ignored.yml", "id: ignored")

        val content = ContentLoader().load(root)

        assertEquals(listOf("prologue.a"), content.chapters.map { it.id })
        assertEquals("chapters/prologue/a.yml", content.chapters.single().source)
        assertEquals(listOf("prologue.wipe"), content.cutscenes.map { it.id })
        assertTrue(content.issues.isEmpty(), content.issues.joinToString("\n"))
    }

    @Test
    fun `YAML 문법 오류와 중복 키는 파일 단위로 보고하고 나머지 파일은 계속 읽는다`(@TempDir root: File) {
        // 레거시 arcana-prologue-exile.yml(main)에 있던 콜론 누락과 같은 형태
        write(root, "chapters/broken.yml", "id: prologue.broken\nactions:\n  message-chief-2\n    id: message-chief-2\n")
        write(root, "chapters/duplicate.yml", "id: prologue.duplicate\nid: prologue.duplicate2\n")
        write(root, "chapters/ok.yml", "id: prologue.ok\nentry: { step: s, location: l }\n")

        val content = ContentLoader().load(root)

        assertEquals(listOf("prologue.ok"), content.chapters.map { it.id })
        assertEquals(
            listOf("chapters/broken.yml", "chapters/duplicate.yml"),
            content.issues.filter { it.severity == Severity.ERROR }.map { it.source },
        )
    }

    private fun write(root: File, path: String, text: String) {
        File(root, path).apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }
}
