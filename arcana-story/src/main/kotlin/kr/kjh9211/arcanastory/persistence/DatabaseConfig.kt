package kr.kjh9211.arcanastory.persistence

import org.bukkit.configuration.ConfigurationSection

/**
 * 운영 환경의 호스팅 MySQL 접속 정보. ArcanaStory 전용 schema를 사용하며, 모든 테이블은 `arcanastory_`
 * 접두사를 가진다. Story Progress는 Survival과 공유하지 않는다.
 */
class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val sslMode: SslMode = SslMode.PREFERRED,
    val allowPublicKeyRetrieval: Boolean = false,
    val maximumPoolSize: Int = DEFAULT_POOL_SIZE,
    val connectionTimeoutMillis: Long = DEFAULT_CONNECTION_TIMEOUT_MILLIS,
) {
    fun jdbcUrl(): String =
        "jdbc:mysql://$host:$port/$database?characterEncoding=UTF-8" +
            "&sslMode=${sslMode.name}&allowPublicKeyRetrieval=$allowPublicKeyRetrieval"

    /** 비밀번호를 로그에 남기지 않는다. */
    override fun toString(): String =
        "DatabaseConfig(host=$host, port=$port, database=$database, username=$username, sslMode=$sslMode, " +
            "maximumPoolSize=$maximumPoolSize, connectionTimeoutMillis=$connectionTimeoutMillis)"

    enum class SslMode {
        DISABLED,
        PREFERRED,
        REQUIRED,
        VERIFY_CA,
        VERIFY_IDENTITY,
    }

    companion object {
        const val SECTION = "storage.mysql"
        private const val DEFAULT_POOL_SIZE = 5
        private const val DEFAULT_CONNECTION_TIMEOUT_MILLIS = 5_000L
        private val DATABASE_NAME = Regex("^[A-Za-z0-9_]+$")

        fun parse(root: ConfigurationSection): DatabaseConfigResult {
            val section = root.getConfigurationSection(SECTION)
                ?: return DatabaseConfigResult.Invalid(listOf("'$SECTION' 설정이 없습니다"))
            val errors = mutableListOf<String>()

            fun required(key: String): String? {
                val value = section.getString(key)?.takeIf { it.isNotBlank() }
                if (value == null) errors += "'$SECTION.$key' 값이 필요합니다"
                return value
            }

            val host = required("host")
            val database = required("database")?.also {
                if (!DATABASE_NAME.matches(it)) errors += "'$SECTION.database'에는 영문·숫자·밑줄만 사용할 수 있습니다"
            }
            val username = required("username")
            val password = section.getString("password")
            if (password == null) errors += "'$SECTION.password' 값이 필요합니다 (비밀번호가 없으면 빈 문자열)"

            val port = section.getInt("port", 3306)
            if (port !in 1..65535) errors += "'$SECTION.port'는 1~65535여야 합니다"

            val rawSslMode = section.getString("ssl-mode") ?: SslMode.PREFERRED.name
            val sslMode = SslMode.entries.firstOrNull { it.name.equals(rawSslMode.trim().replace('-', '_'), ignoreCase = true) }
            if (sslMode == null) errors += "'$SECTION.ssl-mode' 값 '$rawSslMode'을 알 수 없습니다"

            val poolSize = section.getInt("pool.maximum-size", DEFAULT_POOL_SIZE)
            if (poolSize !in 1..20) errors += "'$SECTION.pool.maximum-size'는 1~20이어야 합니다"
            val timeout = section.getLong("pool.connection-timeout-ms", DEFAULT_CONNECTION_TIMEOUT_MILLIS)
            if (timeout < 250) errors += "'$SECTION.pool.connection-timeout-ms'는 250 이상이어야 합니다"

            if (host == null || database == null || username == null || password == null || sslMode == null || errors.isNotEmpty()) {
                return DatabaseConfigResult.Invalid(errors)
            }
            return DatabaseConfigResult.Valid(
                DatabaseConfig(
                    host = host,
                    port = port,
                    database = database,
                    username = username,
                    password = password,
                    sslMode = sslMode,
                    allowPublicKeyRetrieval = section.getBoolean("allow-public-key-retrieval", false),
                    maximumPoolSize = poolSize,
                    connectionTimeoutMillis = timeout,
                ),
            )
        }
    }
}

sealed interface DatabaseConfigResult {
    data class Valid(val config: DatabaseConfig) : DatabaseConfigResult

    data class Invalid(val errors: List<String>) : DatabaseConfigResult
}
