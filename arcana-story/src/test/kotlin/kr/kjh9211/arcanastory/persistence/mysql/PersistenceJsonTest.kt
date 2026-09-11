package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.content.model.RewardComponent
import kr.kjh9211.arcanastory.persistence.SnapshotLocation
import kr.kjh9211.arcanastory.persistence.TemporaryChoice
import kr.kjh9211.arcanastory.persistence.TemporaryFlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kr.kjh9211.arcanastory.persistence.PersistenceException
import java.util.UUID

class PersistenceJsonTest {

    @Test
    fun `보상 payload를 왕복 변환한다`() {
        val components = listOf(
            RewardComponent.Item("arcana:dragon_core", 3),
            RewardComponent.Experience(50),
            RewardComponent.Command("say hi"),
            RewardComponent.Money(12.5),
            RewardComponent.SharedUnlock("title", "world_savior"),
        )

        components.forEach { component ->
            assertEquals(component, PersistenceJson.parseRewardComponent(PersistenceJson.rewardComponent(component)))
        }
    }

    @Test
    fun `알 수 없는 보상 payload는 예외다`() {
        assertThrows<PersistenceException> { PersistenceJson.parseRewardComponent("""{"type":"bitcoin","amount":1}""") }
    }

    @Test
    fun `세션 snapshot JSON을 왕복 변환한다`() {
        val decidedBy = UUID.randomUUID()
        val choices = mapOf("chief_words" to TemporaryChoice("distrust", decidedBy))
        val flags = mapOf("prologue_exiled" to TemporaryFlag("true", persist = true))
        val location = SnapshotLocation("story_prologue", 0.5, 64.0, -2.5, 90f, 12f)

        assertEquals(choices, PersistenceJson.parseTemporaryChoices(PersistenceJson.temporaryChoices(choices)))
        assertEquals(flags, PersistenceJson.parseTemporaryFlags(PersistenceJson.temporaryFlags(flags)))
        assertEquals(location, PersistenceJson.parseLocation(PersistenceJson.location(location)))
    }

    @Test
    fun `UUID 목록을 왕복 변환한다`() {
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())

        assertEquals(ids, PersistenceJson.parseUuidList(PersistenceJson.uuidList(ids)))
        assertEquals(emptyList<UUID>(), PersistenceJson.parseUuidList(PersistenceJson.uuidList(emptyList())))
    }
}
