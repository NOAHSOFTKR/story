package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.persistence.DatabaseConfig
import kr.kjh9211.arcanastory.persistence.HikariDatabase
import kr.kjh9211.arcanastory.persistence.migration.MigrationRunner
import kr.kjh9211.arcanastory.persistence.migration.MigrationScripts
import org.opentest4j.TestAbortedException
import org.testcontainers.DockerClientFactory
import org.testcontainers.mysql.MySQLContainer
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * MySQL 통합 테스트용 컨테이너. 테스트 클래스마다 빈 schema를 새로 만들어 서로 간섭하지 않게 한다.
 *
 * Docker가 없으면 로컬에서는 테스트를 건너뛰지만, CI(`ARCANASTORY_REQUIRE_DOCKER=true`)에서는 실패시킨다.
 * production 스키마·마이그레이션은 반드시 실제 MySQL에서 검증해야 하기 때문이다.
 */
internal object MySqlTestSupport {
    private const val MYSQL_PORT = 3306
    private const val ROOT_PASSWORD = "arcanastory"
    private const val REQUIRE_DOCKER_ENV = "ARCANASTORY_REQUIRE_DOCKER"

    private val schemaCounter = AtomicInteger()

    private val container: MySQLContainer? by lazy {
        if (!DockerClientFactory.instance().isDockerAvailable) {
            null
        } else {
            // schema를 테스트마다 새로 만들기 위해 root로 접속한다.
            MySQLContainer("mysql:8.0.36")
                .withUsername("root")
                .withPassword(ROOT_PASSWORD)
                .also { it.start() }
        }
    }

    /** 마이그레이션이 적용되지 않은 빈 schema. */
    fun freshDatabase(): HikariDatabase {
        val running = container ?: unavailable()
        val schema = "arcanastory_test_${schemaCounter.incrementAndGet()}"
        DriverManager.getConnection(running.jdbcUrl, "root", ROOT_PASSWORD).use { connection ->
            connection.createStatement().use {
                it.execute("CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_bin")
            }
        }
        return HikariDatabase.connect(
            DatabaseConfig(
                host = running.host,
                port = running.getMappedPort(MYSQL_PORT),
                database = schema,
                username = "root",
                password = ROOT_PASSWORD,
                sslMode = DatabaseConfig.SslMode.DISABLED,
                allowPublicKeyRetrieval = true,
                maximumPoolSize = 3,
            ),
        )
    }

    /** 최신 스키마까지 마이그레이션된 schema. */
    fun migratedDatabase(): HikariDatabase = freshDatabase().also {
        MigrationRunner(it, MigrationScripts.loadMySql()).migrate()
    }

    private fun unavailable(): Nothing {
        val message = "Docker를 사용할 수 없어 MySQL 통합 테스트를 실행하지 못했습니다"
        if (System.getenv(REQUIRE_DOCKER_ENV).equals("true", ignoreCase = true)) {
            throw AssertionError("$message ($REQUIRE_DOCKER_ENV=true)")
        }
        throw TestAbortedException(message)
    }
}
