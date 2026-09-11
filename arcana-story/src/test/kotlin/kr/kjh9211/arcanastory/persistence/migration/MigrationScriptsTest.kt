package kr.kjh9211.arcanastory.persistence.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MigrationScriptsTest {

    @Test
    fun `주석을 제거하고 줄 끝 세미콜론으로 문장을 나눈다`() {
        val statements = MigrationScripts.split(
            """
            -- 주석
            CREATE TABLE a (
                id INT
            );

            CREATE TABLE b (id INT);
            """.trimIndent(),
        )

        assertEquals(2, statements.size)
        assertTrue(statements[0].startsWith("CREATE TABLE a"))
        assertEquals("CREATE TABLE b (id INT)", statements[1])
    }

    @Test
    fun `checksum은 줄바꿈과 들여쓰기 차이를 무시한다`() {
        val lf = MigrationScripts.parse("V001__initial_schema.sql", "CREATE TABLE a (\n  id INT\n);\n")
        val crlf = MigrationScripts.parse("V001__initial_schema.sql", "CREATE TABLE a (\r\n      id INT\r\n);\r\n")

        assertEquals(lf.checksum, crlf.checksum)
    }

    @Test
    fun `내용이 달라지면 checksum도 달라진다`() {
        val original = MigrationScripts.parse("V001__initial_schema.sql", "CREATE TABLE a (id INT);")
        val changed = MigrationScripts.parse("V001__initial_schema.sql", "CREATE TABLE a (id BIGINT);")

        assertTrue(original.checksum != changed.checksum)
    }

    @Test
    fun `파일 이름 형식과 버전 순서를 검증한다`() {
        assertThrows<MigrationException> { MigrationScripts.parse("initial.sql", "CREATE TABLE a (id INT);") }
        assertThrows<MigrationException> { MigrationScripts.parse("V001__Initial.sql", "CREATE TABLE a (id INT);") }
        assertThrows<MigrationException> { MigrationScripts.parse("V001__empty.sql", "-- 주석만 있다\n") }

        val second = MigrationScripts.parse("V003__later.sql", "CREATE TABLE b (id INT);")
        assertThrows<MigrationException> {
            MigrationScripts.validateOrder(listOf(MigrationScripts.parse("V001__first.sql", "CREATE TABLE a (id INT);"), second))
        }
    }

    @Test
    fun `번들 MySQL 마이그레이션을 읽는다`() {
        val migrations = MigrationScripts.loadMySql()

        assertEquals(listOf(1), migrations.map { it.version })
        val initial = migrations.single()
        assertEquals("initial_schema", initial.description)
        assertEquals(14, initial.statements.size) { initial.statements.joinToString("\n\n") }
        assertTrue(initial.statements.all { it.startsWith("CREATE TABLE IF NOT EXISTS arcanastory_") })
    }
}
