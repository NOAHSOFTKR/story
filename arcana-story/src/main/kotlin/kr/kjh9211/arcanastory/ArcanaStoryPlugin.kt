package kr.kjh9211.arcanastory

import kr.kjh9211.arcanastory.config.ArcanaStoryConfig
import kr.kjh9211.arcanastory.config.ConfigParseResult
import kr.kjh9211.arcanastory.config.QuestRealmState
import kr.kjh9211.arcanastory.config.RealmGuard
import org.bukkit.plugin.java.JavaPlugin

/**
 * Composition root. 각 Manager는 이후 PR에서 이곳에 enable 순서대로 조립된다.
 *
 * MockBukkit이 ByteBuddy로 플러그인 클래스를 서브클래싱해 로드하므로 `open`이어야 한다.
 */
open class ArcanaStoryPlugin : JavaPlugin() {

    lateinit var storyConfig: ArcanaStoryConfig
        private set

    override fun onEnable() {
        saveDefaultConfig()

        storyConfig = when (val parsed = ArcanaStoryConfig.parse(config)) {
            is ConfigParseResult.Valid -> parsed.config
            is ConfigParseResult.Invalid -> {
                shutdown(listOf(parsed.reason))
                return
            }
        }

        val realmCheck = RealmGuard.check(storyConfig.realm, questRealmState())
        realmCheck.warnings.forEach(logger::warning)
        if (!realmCheck.accepted) {
            shutdown(realmCheck.errors)
            return
        }

        logger.info("ArcanaStory enabled (realm=${storyConfig.realm}).")
    }

    private fun questRealmState(): QuestRealmState {
        val quest = server.pluginManager.getPlugin(QUEST_PLUGIN_NAME) ?: return QuestRealmState.NotInstalled
        return QuestRealmState.Configured(quest.config.getString(ArcanaStoryConfig.REALM_PATH))
    }

    private fun shutdown(reasons: List<String>) {
        reasons.forEach(logger::severe)
        logger.severe("ArcanaStory를 비활성화합니다.")
        server.pluginManager.disablePlugin(this)
    }

    private companion object {
        const val QUEST_PLUGIN_NAME = "Quest"
    }
}
