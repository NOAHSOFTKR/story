package kr.kjh9211.arcanastory.config

import org.bukkit.configuration.ConfigurationSection

data class ArcanaStoryConfig(
    val realm: ServerRealm,
) {
    companion object {
        const val REALM_PATH = "server.realm"

        fun parse(section: ConfigurationSection): ConfigParseResult {
            val raw = section.getString(REALM_PATH)
            if (raw.isNullOrBlank()) {
                return ConfigParseResult.Invalid("'$REALM_PATH' 설정이 없습니다. Story Paper에서는 STORY로 지정해야 합니다.")
            }
            val realm = ServerRealm.parse(raw)
                ?: return ConfigParseResult.Invalid("알 수 없는 '$REALM_PATH' 값 '$raw' (SURVIVAL 또는 STORY)")
            return ConfigParseResult.Valid(ArcanaStoryConfig(realm))
        }
    }
}

sealed interface ConfigParseResult {
    data class Valid(val config: ArcanaStoryConfig) : ConfigParseResult

    data class Invalid(val reason: String) : ConfigParseResult
}
