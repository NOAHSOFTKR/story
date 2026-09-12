package kr.kjh9211.arcanastory.config

/** Story Paper가 아닌 서버(특히 Survival)에 잘못 배포되었을 때 Story 데이터가 생성되지 않도록 막는다. */
object RealmGuard {
    fun check(storyRealm: ServerRealm, questRealm: QuestRealmState): RealmCheck {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (storyRealm != ServerRealm.STORY) {
            errors += "ArcanaStory는 Story Paper 전용입니다 (${ArcanaStoryConfig.REALM_PATH}=$storyRealm)."
        }

        when (questRealm) {
            QuestRealmState.NotInstalled ->
                warnings += "Quest 플러그인이 없어 Quest realm 교차 검증을 건너뜁니다."

            is QuestRealmState.Configured -> {
                // Quest는 server.realm이 없으면 SURVIVAL로 동작하므로 같은 규칙으로 해석한다.
                val raw = questRealm.raw?.takeIf { it.isNotBlank() } ?: ServerRealm.SURVIVAL.name
                when (val parsed = ServerRealm.parse(raw)) {
                    null -> errors += "Quest의 ${ArcanaStoryConfig.REALM_PATH} 값 '$raw'을 해석할 수 없습니다."
                    ServerRealm.STORY -> Unit
                    else -> errors += "Quest가 $parsed realm으로 동작 중입니다. Story Paper에서는 Quest의 " +
                        "${ArcanaStoryConfig.REALM_PATH}도 STORY여야 합니다 (미설정 시 Quest 기본값은 SURVIVAL)."
                }
            }
        }

        return RealmCheck(errors, warnings)
    }
}

sealed interface QuestRealmState {
    data object NotInstalled : QuestRealmState

    /** [raw]는 Quest config의 `server.realm` 원본 값이며, 설정되지 않았으면 null이다. */
    data class Configured(val raw: String?) : QuestRealmState
}

data class RealmCheck(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val accepted: Boolean
        get() = errors.isEmpty()
}
