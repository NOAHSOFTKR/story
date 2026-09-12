package kr.kjh9211.arcanastory.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealmGuardTest {

    @Test
    fun `STORY realm과 Quest STORY realm이면 통과한다`() {
        val check = RealmGuard.check(ServerRealm.STORY, QuestRealmState.Configured("STORY"))

        assertTrue(check.accepted)
        assertTrue(check.warnings.isEmpty())
    }

    @Test
    fun `ArcanaStory realm이 SURVIVAL이면 거부한다`() {
        val check = RealmGuard.check(ServerRealm.SURVIVAL, QuestRealmState.Configured("STORY"))

        assertFalse(check.accepted)
        assertEquals(1, check.errors.size)
    }

    @Test
    fun `Quest가 SURVIVAL realm이면 거부한다`() {
        val check = RealmGuard.check(ServerRealm.STORY, QuestRealmState.Configured("SURVIVAL"))

        assertFalse(check.accepted)
    }

    @Test
    fun `Quest realm 미설정은 Quest 기본값 SURVIVAL로 간주해 거부한다`() {
        assertFalse(RealmGuard.check(ServerRealm.STORY, QuestRealmState.Configured(null)).accepted)
        assertFalse(RealmGuard.check(ServerRealm.STORY, QuestRealmState.Configured(" ")).accepted)
    }

    @Test
    fun `Quest realm 값을 해석할 수 없으면 거부한다`() {
        assertFalse(RealmGuard.check(ServerRealm.STORY, QuestRealmState.Configured("GLOBAL")).accepted)
    }

    @Test
    fun `Quest가 설치되지 않았으면 경고만 남기고 통과한다`() {
        val check = RealmGuard.check(ServerRealm.STORY, QuestRealmState.NotInstalled)

        assertTrue(check.accepted)
        assertEquals(1, check.warnings.size)
    }
}
