package kr.kjh9211.arcanastory.persistence.migration

import kr.kjh9211.arcanastory.persistence.Database
import kr.kjh9211.arcanastory.persistence.mysql.query
import kr.kjh9211.arcanastory.persistence.mysql.update
import java.sql.Connection
import java.time.Clock

data class MigrationResult(
    val appliedVersions: List<Int>,
    val currentVersion: Int,
)

/**
 * 버전 순서대로 MySQL 마이그레이션을 적용한다.
 *
 * - 서버 여러 대가 동시에 시작해도 한 곳만 적용하도록 `GET_LOCK`을 잡는다.
 * - 이미 적용한 마이그레이션의 내용이 바뀌었거나, DB가 이 플러그인보다 새로운 버전이면 시작을 거부한다.
 * - MySQL DDL은 트랜잭션으로 묶이지 않으므로 스크립트는 `IF NOT EXISTS`로 재실행 가능하게 작성한다.
 */
class MigrationRunner(
    private val database: Database,
    private val migrations: List<Migration>,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun migrate(): MigrationResult = database.withConnection { connection ->
        acquireLock(connection)
        try {
            ensureVersionTable(connection)
            val applied = appliedChecksums(connection)
            verifyApplied(applied)

            val pending = migrations.filter { it.version !in applied }.sortedBy { it.version }
            pending.forEach { migration -> apply(connection, migration) }

            MigrationResult(
                appliedVersions = pending.map { it.version },
                currentVersion = (applied.keys + pending.map { it.version }).maxOrNull() ?: 0,
            )
        } finally {
            releaseLock(connection)
        }
    }

    private fun verifyApplied(applied: Map<Int, String>) {
        val known = migrations.associateBy { it.version }
        applied.keys.filterNot { it in known }.maxOrNull()?.let { version ->
            throw MigrationException(
                "DB에 이 플러그인이 모르는 스키마 버전 V$version 이 적용되어 있습니다. 더 새로운 ArcanaStory가 사용한 DB일 수 있습니다",
            )
        }
        applied.forEach { (version, checksum) ->
            if (known.getValue(version).checksum != checksum) {
                throw MigrationException("이미 적용된 마이그레이션 V$version 의 내용이 변경되었습니다 (checksum 불일치)")
            }
        }
    }

    private fun apply(connection: Connection, migration: Migration) {
        migration.statements.forEach { sql -> connection.createStatement().use { it.execute(sql) } }
        connection.update(
            "INSERT INTO arcanastory_schema_version (version, description, checksum, applied_at) VALUES (?, ?, ?, ?)",
            migration.version,
            migration.description,
            migration.checksum,
            clock.millis(),
        )
    }

    private fun ensureVersionTable(connection: Connection) {
        connection.createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS arcanastory_schema_version (
                    version     INT          NOT NULL,
                    description VARCHAR(191) NOT NULL,
                    checksum    CHAR(64)     NOT NULL,
                    applied_at  BIGINT       NOT NULL,
                    PRIMARY KEY (version)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.trimIndent(),
            )
        }
    }

    private fun appliedChecksums(connection: Connection): Map<Int, String> =
        connection.query("SELECT version, checksum FROM arcanastory_schema_version") { rs ->
            rs.getInt("version") to rs.getString("checksum")
        }.toMap()

    private fun acquireLock(connection: Connection) {
        val acquired = connection.query("SELECT GET_LOCK(?, ?) AS acquired", LOCK_NAME, LOCK_TIMEOUT_SECONDS) { it.getInt("acquired") }
            .singleOrNull() == 1
        if (!acquired) throw MigrationException("마이그레이션 잠금을 ${LOCK_TIMEOUT_SECONDS}초 안에 얻지 못했습니다")
    }

    private fun releaseLock(connection: Connection) {
        runCatching { connection.query("SELECT RELEASE_LOCK(?) AS released", LOCK_NAME) { it.getInt("released") } }
    }

    private companion object {
        const val LOCK_NAME = "arcanastory_schema_migration"
        const val LOCK_TIMEOUT_SECONDS = 60
    }
}
