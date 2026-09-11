package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.persistence.migration.MigrationException
import kr.kjh9211.arcanastory.persistence.migration.MigrationRunner
import kr.kjh9211.arcanastory.persistence.migration.MigrationScripts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MigrationRunnerMySqlTest {

    private val migrations = MigrationScripts.loadMySql()

    @Test
    fun `빈 schema에 전체 스키마를 만들고 다시 실행하면 아무 것도 적용하지 않는다`() {
        MySqlTestSupport.freshDatabase().use { database ->
            val first = MigrationRunner(database, migrations).migrate()
            assertEquals(listOf(1), first.appliedVersions)
            assertEquals(1, first.currentVersion)
            assertEquals(EXPECTED_TABLES, tables(database))

            val second = MigrationRunner(database, migrations).migrate()
            assertEquals(emptyList<Int>(), second.appliedVersions)
            assertEquals(1, second.currentVersion)
        }
    }

    @Test
    fun `이미 적용된 마이그레이션의 내용이 바뀌면 거부한다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            database.withConnection { connection ->
                connection.update("UPDATE arcanastory_schema_version SET checksum = ? WHERE version = 1", "0".repeat(64))
            }

            val failure = assertThrows<MigrationException> { MigrationRunner(database, migrations).migrate() }
            assertEquals(true, "checksum" in failure.message.orEmpty())
        }
    }

    @Test
    fun `플러그인이 모르는 더 새로운 스키마 버전이면 거부한다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            database.withConnection { connection ->
                connection.update(
                    "INSERT INTO arcanastory_schema_version (version, description, checksum, applied_at) VALUES (?, ?, ?, ?)",
                    99,
                    "from_the_future",
                    "1".repeat(64),
                    0L,
                )
            }

            assertThrows<MigrationException> { MigrationRunner(database, migrations).migrate() }
        }
    }

    private fun tables(database: kr.kjh9211.arcanastory.persistence.Database): Set<String> =
        database.withConnection { connection ->
            connection.query(
                "SELECT table_name AS name FROM information_schema.tables WHERE table_schema = DATABASE()",
            ) { it.getString("name").lowercase() }.toSet()
        }

    private companion object {
        val EXPECTED_TABLES = setOf(
            "arcanastory_schema_version",
            "arcanastory_player",
            "arcanastory_chapter_completion",
            "arcanastory_branch_completion",
            "arcanastory_choice_event",
            "arcanastory_canonical_choice",
            "arcanastory_flag",
            "arcanastory_run",
            "arcanastory_run_commit",
            "arcanastory_admin_audit",
            "arcanastory_reward_claim",
            "arcanastory_reward_delivery",
            "arcanastory_reward_delivery_attempt",
            "arcanastory_session_snapshot",
            "arcanastory_inventory_snapshot",
        )
    }
}
