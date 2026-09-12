package kr.kjh9211.arcanastory.content.yaml

import kr.kjh9211.arcanastory.content.IssueCollector
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * SnakeYAML이 만든 plain Map/List 트리를 YAML 경로를 추적하며 읽는다.
 *
 * Bukkit `YamlConfiguration`은 키의 `.`을 경로 구분자로 해석하므로 쓰지 않는다. 타입이 맞지 않으면
 * [IssueCollector]에 기록하고 기본값 또는 null을 돌려준다.
 */
class YamlNode(
    val value: Any?,
    val source: String,
    val path: String,
    private val issues: IssueCollector,
) {
    val isPresent: Boolean
        get() = value != null

    fun child(key: String): YamlNode = YamlNode((value as? Map<*, *>)?.get(key), source, childPath(key), issues)

    /** 맵이면 true. 값이 있는데 맵이 아니면 오류를 기록한다. 값이 없으면 오류 없이 false. */
    fun expectMap(): Boolean {
        if (value is Map<*, *>) return true
        if (value != null) addError("key: value 형태의 맵이어야 합니다")
        return false
    }

    /** 반드시 맵이어야 하는 노드. 값이 없어도 오류를 기록한다. */
    fun requireMap(): Boolean {
        if (value == null) {
            addError("비어 있습니다")
            return false
        }
        return expectMap()
    }

    fun entries(): List<Pair<String, YamlNode>> {
        if (!expectMap()) return emptyList()
        return (value as Map<*, *>).entries.map { (key, child) ->
            val name = key.toString()
            name to YamlNode(child, source, childPath(name), issues)
        }
    }

    fun elements(): List<YamlNode> {
        val list = when (value) {
            null -> return emptyList()
            is List<*> -> value
            else -> {
                addError("리스트여야 합니다")
                return emptyList()
            }
        }
        return list.mapIndexed { index, element -> YamlNode(element, source, "$path[$index]", issues) }
    }

    fun string(): String? = when (value) {
        null -> null
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> {
            addError("문자열이어야 합니다")
            null
        }
    }

    fun requiredString(): String? {
        if (value == null) {
            addError("필수 값입니다")
            return null
        }
        val text = string() ?: return null
        if (text.isBlank()) {
            addError("비어 있을 수 없습니다")
            return null
        }
        return text
    }

    fun stringList(): List<String> = elements().mapNotNull { it.string() }

    fun intOrNull(): Int? = when (value) {
        null -> null
        is Int -> value
        is Long -> if (value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) {
            value.toInt()
        } else {
            addError("정수 범위를 벗어났습니다")
            null
        }
        else -> {
            addError("정수여야 합니다")
            null
        }
    }

    fun int(default: Int): Int = intOrNull() ?: default

    fun requiredInt(): Int? {
        if (value == null) {
            addError("필수 값입니다")
            return null
        }
        return intOrNull()
    }

    fun doubleOrNull(): Double? = when (value) {
        null -> null
        is Number -> value.toDouble()
        else -> {
            addError("숫자여야 합니다")
            null
        }
    }

    fun double(default: Double): Double = doubleOrNull() ?: default

    fun requiredDouble(): Double? {
        if (value == null) {
            addError("필수 값입니다")
            return null
        }
        return doubleOrNull()
    }

    fun float(default: Float): Float = doubleOrNull()?.toFloat() ?: default

    fun boolean(default: Boolean): Boolean = when (value) {
        null -> default
        is Boolean -> value
        else -> {
            addError("true 또는 false여야 합니다")
            default
        }
    }

    /** 오타를 잡기 위해 허용되지 않은 키를 경고한다. 맵이 아니면 아무 것도 하지 않는다. */
    fun warnUnknownKeys(allowed: Set<String>) {
        val map = value as? Map<*, *> ?: return
        map.keys.map { it.toString() }
            .filterNot { it in allowed }
            .forEach { child(it).addWarning("알 수 없는 키입니다") }
    }

    fun addError(message: String) = issues.error(source, path, message)

    fun addWarning(message: String) = issues.warning(source, path, message)

    private fun childPath(key: String): String = if (path.isEmpty()) key else "$path.$key"
}

internal object ContentYaml {
    /** 중복 키를 허용하지 않는 안전한(임의 객체 생성 불가) 로더. */
    fun load(text: String): Any? {
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
        return Yaml(SafeConstructor(options)).load<Any?>(text)
    }
}
