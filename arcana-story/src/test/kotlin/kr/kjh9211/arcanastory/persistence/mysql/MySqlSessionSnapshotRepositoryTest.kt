package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.history.ParticipantRole
import kr.kjh9211.arcanastory.persistence.InventorySnapshotKind
import kr.kjh9211.arcanastory.persistence.InventorySnapshotRecord
import kr.kjh9211.arcanastory.persistence.SessionSnapshotRecord
import kr.kjh9211.arcanastory.persistence.SnapshotLocation
import kr.kjh9211.arcanastory.persistence.SnapshotState
import kr.kjh9211.arcanastory.persistence.TemporaryChoice
import kr.kjh9211.arcanastory.persistence.TemporaryFlag
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MySqlSessionSnapshotRepositoryTest {

    private val playerId = UUID.randomUUID()
    private val runId = UUID.randomUUID()
    private val at = Instant.ofEpochMilli(1_700_000_000_000)

    private fun snapshot(
        state: SnapshotState = SnapshotState.ACTIVE,
        checkpointId: String? = null,
        stepId: String = "confront_chief",
    ) = SessionSnapshotRecord(
        playerId = playerId,
        runId = runId,
        chapterId = "prologue.exile",
        contentHash = "hash-1",
        state = state,
        checkpointId = checkpointId,
        stepId = stepId,
        location = SnapshotLocation("story_prologue", 0.5, 64.0, -2.5, 90f, 0f),
        temporaryChoices = mapOf("chief_words" to TemporaryChoice("distrust", playerId)),
        temporaryFlags = mapOf("prologue_exiled" to TemporaryFlag("true", persist = true)),
        pendingBranches = setOf("distrust"),
        questSegment = listOf("story_prologue_hide_luminite"),
        role = ParticipantRole.LEADER,
        updatedAt = at,
    )

    private fun inventory(kind: InventorySnapshotKind, payload: ByteArray, checkpointId: String? = null) =
        InventorySnapshotRecord.create(
            playerId = playerId,
            kind = kind,
            runId = runId,
            checkpointId = checkpointId,
            payloadFormat = "paper-bytes-v1",
            dataVersion = 4189,
            payload = payload,
            xpLevel = 12,
            xpProgress = 0.25f,
            capturedAt = at,
        )

    @Test
    fun `세션과 인벤토리 snapshot을 함께 저장하고 읽는다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlSessionSnapshotRepository(database)
            val payload = ByteArray(64) { it.toByte() }

            repository.save(snapshot(), inventory(InventorySnapshotKind.ENTRY, payload))

            val stored = repository.load(playerId)!!
            assertEquals(snapshot(), stored.snapshot)
            val entry = stored.inventories.getValue(InventorySnapshotKind.ENTRY)
            assertArrayEquals(payload, entry.payload)
            assertTrue(entry.isIntact)
            assertEquals(12, entry.xpLevel)
            assertEquals(0.25f, entry.xpProgress)
        }
    }

    @Test
    fun `checkpoint에 도달하면 같은 플레이어의 snapshot을 덮어쓰고 종류별로 보관한다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlSessionSnapshotRepository(database)
            repository.save(snapshot(), inventory(InventorySnapshotKind.ENTRY, byteArrayOf(1, 2, 3)))

            repository.save(
                snapshot(state = SnapshotState.INTERRUPTED, checkpointId = "before_exile", stepId = "hide_luminite"),
                inventory(InventorySnapshotKind.CHECKPOINT, byteArrayOf(9, 9), checkpointId = "before_exile"),
            )

            val stored = repository.load(playerId)!!
            assertEquals(SnapshotState.INTERRUPTED, stored.snapshot.state)
            assertEquals("before_exile", stored.snapshot.checkpointId)
            assertEquals("hide_luminite", stored.snapshot.stepId)
            assertEquals(
                setOf(InventorySnapshotKind.ENTRY, InventorySnapshotKind.CHECKPOINT),
                stored.inventories.keys,
            )
            assertArrayEquals(byteArrayOf(9, 9), stored.inventories.getValue(InventorySnapshotKind.CHECKPOINT).payload)
        }
    }

    @Test
    fun `삭제하면 인벤토리 snapshot도 함께 지운다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlSessionSnapshotRepository(database)
            repository.save(snapshot(), inventory(InventorySnapshotKind.ENTRY, byteArrayOf(7)))

            repository.delete(playerId)

            assertNull(repository.load(playerId))
            val remaining = database.withConnection { connection ->
                connection.query("SELECT COUNT(*) AS count FROM arcanastory_inventory_snapshot WHERE player_uuid = ?", playerId) {
                    it.getInt("count")
                }.single()
            }
            assertEquals(0, remaining)
        }
    }

    @Test
    fun `손상된 payload는 isIntact가 false다`() {
        MySqlTestSupport.migratedDatabase().use { database ->
            val repository = MySqlSessionSnapshotRepository(database)
            repository.save(snapshot(), inventory(InventorySnapshotKind.ENTRY, byteArrayOf(1, 2, 3)))
            database.withConnection { connection ->
                connection.update(
                    "UPDATE arcanastory_inventory_snapshot SET payload = ? WHERE player_uuid = ? AND kind = 'ENTRY'",
                    byteArrayOf(4, 5, 6),
                    playerId,
                )
            }

            val entry = repository.load(playerId)!!.inventories.getValue(InventorySnapshotKind.ENTRY)

            assertTrue(!entry.isIntact)
        }
    }
}
