package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.history.ParticipantRole
import kr.kjh9211.arcanastory.persistence.Database
import kr.kjh9211.arcanastory.persistence.InventorySnapshotKind
import kr.kjh9211.arcanastory.persistence.InventorySnapshotRecord
import kr.kjh9211.arcanastory.persistence.SessionSnapshotRecord
import kr.kjh9211.arcanastory.persistence.SessionSnapshotRepository
import kr.kjh9211.arcanastory.persistence.SnapshotState
import kr.kjh9211.arcanastory.persistence.StoredSession
import java.sql.Connection
import java.util.UUID

class MySqlSessionSnapshotRepository(
    private val database: Database,
) : SessionSnapshotRepository {

    override fun save(snapshot: SessionSnapshotRecord, inventory: InventorySnapshotRecord?) {
        require(inventory == null || inventory.playerId == snapshot.playerId) {
            "인벤토리 snapshot의 플레이어가 session snapshot과 다릅니다"
        }
        database.transaction { connection ->
            upsertSnapshot(connection, snapshot)
            inventory?.let { upsertInventory(connection, it) }
        }
    }

    override fun load(playerId: UUID): StoredSession? = database.withConnection { connection ->
        val snapshot = connection.query(
            "SELECT player_uuid, run_id, chapter_id, content_hash, state, checkpoint_id, step_id, location_json, " +
                "temp_choices_json, temp_flags_json, pending_branches_json, quest_segment_json, participant_role, updated_at " +
                "FROM arcanastory_session_snapshot WHERE player_uuid = ?",
            playerId,
        ) { rs ->
            SessionSnapshotRecord(
                playerId = rs.uuid("player_uuid"),
                runId = rs.uuid("run_id"),
                chapterId = rs.getString("chapter_id"),
                contentHash = rs.getString("content_hash"),
                state = SnapshotState.valueOf(rs.getString("state")),
                checkpointId = rs.getString("checkpoint_id"),
                stepId = rs.getString("step_id"),
                location = rs.getString("location_json")?.let(PersistenceJson::parseLocation),
                temporaryChoices = PersistenceJson.parseTemporaryChoices(rs.getString("temp_choices_json")),
                temporaryFlags = PersistenceJson.parseTemporaryFlags(rs.getString("temp_flags_json")),
                pendingBranches = PersistenceJson.parseStringList(rs.getString("pending_branches_json")).toSet(),
                questSegment = PersistenceJson.parseStringList(rs.getString("quest_segment_json")),
                role = ParticipantRole.valueOf(rs.getString("participant_role")),
                updatedAt = rs.instant("updated_at"),
            )
        }.singleOrNull() ?: return@withConnection null

        val inventories = connection.query(
            "SELECT player_uuid, kind, run_id, checkpoint_id, payload_format, data_version, payload, checksum, " +
                "xp_level, xp_progress, captured_at FROM arcanastory_inventory_snapshot WHERE player_uuid = ?",
            playerId,
        ) { rs ->
            InventorySnapshotRecord(
                playerId = rs.uuid("player_uuid"),
                kind = InventorySnapshotKind.valueOf(rs.getString("kind")),
                runId = rs.uuid("run_id"),
                checkpointId = rs.getString("checkpoint_id"),
                payloadFormat = rs.getString("payload_format"),
                dataVersion = rs.getInt("data_version"),
                payload = rs.getBytes("payload"),
                checksum = rs.getString("checksum"),
                xpLevel = rs.getInt("xp_level"),
                xpProgress = rs.getFloat("xp_progress"),
                capturedAt = rs.instant("captured_at"),
            )
        }.associateBy { it.kind }

        StoredSession(snapshot, inventories)
    }

    override fun delete(playerId: UUID) {
        database.transaction { connection ->
            connection.update("DELETE FROM arcanastory_inventory_snapshot WHERE player_uuid = ?", playerId)
            connection.update("DELETE FROM arcanastory_session_snapshot WHERE player_uuid = ?", playerId)
        }
    }

    private fun upsertSnapshot(connection: Connection, snapshot: SessionSnapshotRecord) {
        connection.update(
            "INSERT INTO arcanastory_session_snapshot (player_uuid, run_id, chapter_id, content_hash, state, " +
                "checkpoint_id, step_id, location_json, temp_choices_json, temp_flags_json, pending_branches_json, " +
                "quest_segment_json, participant_role, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE run_id = VALUES(run_id), chapter_id = VALUES(chapter_id), " +
                "content_hash = VALUES(content_hash), state = VALUES(state), checkpoint_id = VALUES(checkpoint_id), " +
                "step_id = VALUES(step_id), location_json = VALUES(location_json), " +
                "temp_choices_json = VALUES(temp_choices_json), temp_flags_json = VALUES(temp_flags_json), " +
                "pending_branches_json = VALUES(pending_branches_json), quest_segment_json = VALUES(quest_segment_json), " +
                "participant_role = VALUES(participant_role), updated_at = VALUES(updated_at)",
            snapshot.playerId,
            snapshot.runId,
            snapshot.chapterId,
            snapshot.contentHash,
            snapshot.state.name,
            snapshot.checkpointId,
            snapshot.stepId,
            snapshot.location?.let(PersistenceJson::location),
            PersistenceJson.temporaryChoices(snapshot.temporaryChoices),
            PersistenceJson.temporaryFlags(snapshot.temporaryFlags),
            PersistenceJson.stringList(snapshot.pendingBranches),
            PersistenceJson.stringList(snapshot.questSegment),
            snapshot.role.name,
            snapshot.updatedAt,
        )
    }

    private fun upsertInventory(connection: Connection, inventory: InventorySnapshotRecord) {
        connection.update(
            "INSERT INTO arcanastory_inventory_snapshot (player_uuid, kind, run_id, checkpoint_id, payload_format, " +
                "data_version, payload, checksum, xp_level, xp_progress, captured_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE run_id = VALUES(run_id), checkpoint_id = VALUES(checkpoint_id), " +
                "payload_format = VALUES(payload_format), data_version = VALUES(data_version), payload = VALUES(payload), " +
                "checksum = VALUES(checksum), xp_level = VALUES(xp_level), xp_progress = VALUES(xp_progress), " +
                "captured_at = VALUES(captured_at)",
            inventory.playerId,
            inventory.kind.name,
            inventory.runId,
            inventory.checkpointId,
            inventory.payloadFormat,
            inventory.dataVersion,
            inventory.payload,
            inventory.checksum,
            inventory.xpLevel,
            inventory.xpProgress,
            inventory.capturedAt,
        )
    }
}
