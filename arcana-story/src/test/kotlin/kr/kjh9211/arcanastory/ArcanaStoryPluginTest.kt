package kr.kjh9211.arcanastory

import kr.kjh9211.arcanastory.config.ServerRealm
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class ArcanaStoryPluginTest {
    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `Quest 없이 기본 설정으로 활성화되고 비활성화된다`() {
        val plugin = MockBukkit.load(ArcanaStoryPlugin::class.java)

        assertTrue(plugin.isEnabled)
        assertEquals(ServerRealm.STORY, plugin.storyConfig.realm)

        server.pluginManager.disablePlugin(plugin)
        assertFalse(plugin.isEnabled)
    }

    @Test
    fun `Quest realm이 STORY면 활성화된다`() {
        MockBukkit.createMockPlugin("Quest").config.set("server.realm", "STORY")

        val plugin = MockBukkit.load(ArcanaStoryPlugin::class.java)

        assertTrue(plugin.isEnabled)
    }

    @Test
    fun `Quest realm이 SURVIVAL이면 스스로 비활성화된다`() {
        MockBukkit.createMockPlugin("Quest").config.set("server.realm", "SURVIVAL")

        val plugin = MockBukkit.load(ArcanaStoryPlugin::class.java)

        assertFalse(plugin.isEnabled)
    }
}
