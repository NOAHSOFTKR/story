package kr.kjh9211.arcanastory.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.security.MessageDigest
import java.sql.Connection

/** 모든 호출은 블로킹 JDBC이므로 메인 스레드가 아닌 비동기 작업에서만 사용한다. */
interface Database : AutoCloseable {
    fun <T> withConnection(block: (Connection) -> T): T

    /** [block]이 예외 없이 끝나면 commit, 예외가 나면 rollback 후 예외를 다시 던진다. */
    fun <T> transaction(block: (Connection) -> T): T
}

class PersistenceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class HikariDatabase private constructor(
    private val dataSource: HikariDataSource,
) : Database {

    override fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)

    override fun <T> transaction(block: (Connection) -> T): T = withConnection { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (throwable: Throwable) {
            runCatching { connection.rollback() }
            throw throwable
        } finally {
            connection.autoCommit = true
        }
    }

    override fun close() = dataSource.close()

    companion object {
        private const val MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver"

        /** 연결할 수 없으면 즉시 예외를 던진다(fail-fast). */
        fun connect(config: DatabaseConfig): HikariDatabase {
            val hikari = HikariConfig().apply {
                poolName = "ArcanaStory"
                jdbcUrl = config.jdbcUrl()
                username = config.username
                password = config.password
                driverClassName = MYSQL_DRIVER
                maximumPoolSize = config.maximumPoolSize
                minimumIdle = minOf(2, config.maximumPoolSize)
                connectionTimeout = config.connectionTimeoutMillis
            }
            return HikariDatabase(HikariDataSource(hikari))
        }
    }
}

internal object Checksums {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))
}
