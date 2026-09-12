package kr.kjh9211.arcanastory.persistence.mysql

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.Types
import java.time.Instant
import java.util.UUID

private const val MYSQL_DUPLICATE_KEY = 1062

internal fun Connection.update(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).use { statement ->
        statement.bind(args)
        statement.executeUpdate()
    }

internal fun <T> Connection.query(sql: String, vararg args: Any?, mapper: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(args)
        statement.executeQuery().use { resultSet ->
            buildList { while (resultSet.next()) add(mapper(resultSet)) }
        }
    }

/**
 * INSERT를 시도하고, 기본키·유니크키 중복이면 false를 돌려준다. MySQL은 문장 단위로만 롤백하므로
 * 트랜잭션은 계속 사용할 수 있다. (`INSERT IGNORE`는 중복 외 오류까지 경고로 바꾸므로 쓰지 않는다.)
 */
internal fun Connection.insertUnlessDuplicate(sql: String, vararg args: Any?): Boolean = try {
    update(sql, *args)
    true
} catch (exception: SQLIntegrityConstraintViolationException) {
    if (exception.errorCode == MYSQL_DUPLICATE_KEY) false else throw exception
}

private fun PreparedStatement.bind(args: Array<out Any?>) {
    args.forEachIndexed { index, value ->
        val position = index + 1
        when (value) {
            null -> setNull(position, Types.NULL)
            is String -> setString(position, value)
            is Int -> setInt(position, value)
            is Long -> setLong(position, value)
            is Float -> setFloat(position, value)
            is Double -> setDouble(position, value)
            is Boolean -> setBoolean(position, value)
            is ByteArray -> setBytes(position, value)
            is UUID -> setString(position, value.toString())
            is Instant -> setLong(position, value.toEpochMilli())
            else -> throw IllegalArgumentException("지원하지 않는 JDBC 파라미터 타입: ${value::class.java.name}")
        }
    }
}

internal fun ResultSet.uuid(column: String): UUID = UUID.fromString(getString(column))

internal fun ResultSet.uuidOrNull(column: String): UUID? = getString(column)?.let(UUID::fromString)

internal fun ResultSet.instant(column: String): Instant = Instant.ofEpochMilli(getLong(column))

internal fun ResultSet.instantOrNull(column: String): Instant? {
    val millis = getLong(column)
    return if (wasNull()) null else Instant.ofEpochMilli(millis)
}
