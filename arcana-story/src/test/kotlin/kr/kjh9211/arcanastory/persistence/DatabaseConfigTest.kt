package kr.kjh9211.arcanastory.persistence

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseConfigTest {

    private fun parse(yaml: String): DatabaseConfigResult =
        DatabaseConfig.parse(YamlConfiguration().apply { loadFromString(yaml.trimIndent()) })

    @Test
    fun `필수 값을 읽고 JDBC URL을 만든다`() {
        val result = parse(
            """
            storage:
              mysql:
                host: db.internal
                port: 3307
                database: arcana_story
                username: arcanastory
                password: secret
                ssl-mode: required
                pool:
                  maximum-size: 8
                  connection-timeout-ms: 3000
            """,
        )

        val config = (result as DatabaseConfigResult.Valid).config
        assertEquals(8, config.maximumPoolSize)
        assertEquals(3000L, config.connectionTimeoutMillis)
        assertEquals(DatabaseConfig.SslMode.REQUIRED, config.sslMode)
        assertEquals(
            "jdbc:mysql://db.internal:3307/arcana_story?characterEncoding=UTF-8&sslMode=REQUIRED&allowPublicKeyRetrieval=false",
            config.jdbcUrl(),
        )
    }

    @Test
    fun `비밀번호는 toString에 노출되지 않는다`() {
        val config = DatabaseConfig("db", 3306, "arcana_story", "user", "super-secret")

        assertFalse("super-secret" in config.toString()) { config.toString() }
    }

    @Test
    fun `설정 자체가 없으면 Invalid`() {
        assertTrue(parse("other: {}") is DatabaseConfigResult.Invalid)
    }

    @Test
    fun `필수 값 누락과 잘못된 값을 모두 보고한다`() {
        val result = parse(
            """
            storage:
              mysql:
                database: "arcana-story"
                port: 70000
                ssl-mode: sometimes
                pool:
                  maximum-size: 99
            """,
        )

        val errors = (result as DatabaseConfigResult.Invalid).errors
        assertEquals(7, errors.size) { errors.joinToString("\n") }
        assertTrue(errors.any { "host" in it })
        assertTrue(errors.any { "username" in it })
        assertTrue(errors.any { "password" in it })
        assertTrue(errors.any { "database" in it })
        assertTrue(errors.any { "port" in it })
        assertTrue(errors.any { "ssl-mode" in it })
        assertTrue(errors.any { "maximum-size" in it })
    }
}
