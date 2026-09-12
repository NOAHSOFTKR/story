package kr.kjh9211.arcanastory.persistence.migration

import kr.kjh9211.arcanastory.persistence.Checksums

class MigrationException(message: String) : RuntimeException(message)

data class Migration(
    val version: Int,
    val description: String,
    val statements: List<String>,
    /** 공백·줄바꿈 차이를 무시한 내용 checksum. Windows(CRLF) 체크아웃과 CI(LF)에서 같은 값이 나온다. */
    val checksum: String,
)

object MigrationScripts {
    const val MYSQL_RESOURCE_DIRECTORY = "db/migration/mysql"

    /** 실행 순서대로 나열한다. jar 안의 리소스 디렉터리는 나열할 수 없으므로 새 파일은 여기에 추가한다. */
    val MYSQL_FILES = listOf(
        "V001__initial_schema.sql",
    )

    private val FILE_NAME = Regex("^V(\\d{3})__([a-z0-9_]+)\\.sql$")
    private val WHITESPACE = Regex("\\s+")

    fun loadMySql(classLoader: ClassLoader = MigrationScripts::class.java.classLoader): List<Migration> {
        val migrations = MYSQL_FILES.map { fileName ->
            val path = "$MYSQL_RESOURCE_DIRECTORY/$fileName"
            val text = classLoader.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw MigrationException("마이그레이션 리소스를 찾을 수 없습니다: $path")
            parse(fileName, text)
        }
        validateOrder(migrations)
        return migrations
    }

    fun parse(fileName: String, sql: String): Migration {
        val match = FILE_NAME.matchEntire(fileName)
            ?: throw MigrationException("마이그레이션 파일 이름은 V###__설명.sql 형식이어야 합니다: $fileName")
        val statements = split(sql)
        if (statements.isEmpty()) throw MigrationException("실행할 SQL이 없습니다: $fileName")
        return Migration(
            version = match.groupValues[1].toInt(),
            description = match.groupValues[2],
            statements = statements,
            checksum = Checksums.sha256(statements.joinToString("\n;\n") { it.replace(WHITESPACE, " ") }),
        )
    }

    /**
     * `--`로 시작하는 주석 줄을 제거하고, 줄 끝의 `;`를 기준으로 문장을 나눈다. DDL 전용이며 문자열 리터럴
     * 안의 `;`나 줄 중간의 `;`는 지원하지 않는다.
     */
    fun split(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        sql.lineSequence()
            .map { it.trimEnd() }
            .filterNot { it.isBlank() || it.trimStart().startsWith("--") }
            .forEach { line ->
                current.appendLine(line)
                if (line.endsWith(";")) {
                    statements += current.toString().trim().removeSuffix(";").trim()
                    current.clear()
                }
            }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let { statements += it }
        return statements
    }

    internal fun validateOrder(migrations: List<Migration>) {
        migrations.forEachIndexed { index, migration ->
            val expected = index + 1
            if (migration.version != expected) {
                throw MigrationException("마이그레이션 버전은 1부터 빠짐없이 증가해야 합니다: V${migration.version} (기대값 V$expected)")
            }
        }
    }
}
