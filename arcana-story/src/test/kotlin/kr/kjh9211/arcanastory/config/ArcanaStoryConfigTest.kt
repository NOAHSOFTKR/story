package kr.kjh9211.arcanastory.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArcanaStoryConfigTest {

    private fun parse(yaml: String): ConfigParseResult =
        ArcanaStoryConfig.parse(YamlConfiguration().apply { loadFromString(yaml) })

    @Test
    fun `STORY realm은 대소문자와 공백을 무시하고 해석한다`() {
        val result = parse("server:\n  realm: ' story '")

        assertEquals(ConfigParseResult.Valid(ArcanaStoryConfig(ServerRealm.STORY)), result)
    }

    @Test
    fun `SURVIVAL도 설정 값으로는 유효하다 (거부는 RealmGuard 책임)`() {
        val result = parse("server:\n  realm: SURVIVAL")

        assertEquals(ConfigParseResult.Valid(ArcanaStoryConfig(ServerRealm.SURVIVAL)), result)
    }

    @Test
    fun `realm이 없으면 Invalid`() {
        assertTrue(parse("server: {}") is ConfigParseResult.Invalid)
    }

    @Test
    fun `알 수 없는 realm은 Invalid`() {
        assertTrue(parse("server:\n  realm: GLOBAL") is ConfigParseResult.Invalid)
    }
}
