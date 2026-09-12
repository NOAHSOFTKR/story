package kr.kjh9211.arcanastory.config

/** Paper 서버의 역할. Quest 플러그인의 `ServerRealm`과 같은 값을 사용하지만 컴파일 의존은 두지 않는다. */
enum class ServerRealm {
    SURVIVAL,
    STORY,
    ;

    companion object {
        fun parse(value: String?): ServerRealm? {
            val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
    }
}
