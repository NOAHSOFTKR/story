package kr.kjh9211.arcanastory.persistence.mysql

import kr.kjh9211.arcanastory.persistence.Database
import kr.kjh9211.arcanastory.persistence.RunEndReason
import kr.kjh9211.arcanastory.persistence.RunRecord
import kr.kjh9211.arcanastory.persistence.RunRepository
import java.time.Instant
import java.util.UUID

class MySqlRunRepository(
    private val database: Database,
) : RunRepository {

    override fun start(run: RunRecord) {
        database.withConnection { connection ->
            connection.update(
                "INSERT INTO arcanastory_run (run_id, chapter_id, content_hash, participants_json, leader_history_json, " +
                    "started_at, ended_at, end_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                run.runId,
                run.chapterId,
                run.contentHash,
                PersistenceJson.uuidList(run.participants),
                PersistenceJson.uuidList(run.leaderHistory),
                run.startedAt,
                run.endedAt,
                run.endReason?.name,
            )
        }
    }

    override fun end(runId: UUID, reason: RunEndReason, endedAt: Instant, leaderHistory: List<UUID>): Boolean =
        database.withConnection { connection ->
            connection.update(
                "UPDATE arcanastory_run SET ended_at = ?, end_reason = ?, leader_history_json = ? " +
                    "WHERE run_id = ? AND ended_at IS NULL",
                endedAt,
                reason.name,
                PersistenceJson.uuidList(leaderHistory),
                runId,
            ) == 1
        }

    override fun find(runId: UUID): RunRecord? = database.withConnection { connection ->
        connection.query(
            "SELECT run_id, chapter_id, content_hash, participants_json, leader_history_json, started_at, ended_at, end_reason " +
                "FROM arcanastory_run WHERE run_id = ?",
            runId,
        ) { rs ->
            RunRecord(
                runId = rs.uuid("run_id"),
                chapterId = rs.getString("chapter_id"),
                contentHash = rs.getString("content_hash"),
                participants = PersistenceJson.parseUuidList(rs.getString("participants_json")),
                leaderHistory = PersistenceJson.parseUuidList(rs.getString("leader_history_json")),
                startedAt = rs.instant("started_at"),
                endedAt = rs.instantOrNull("ended_at"),
                endReason = rs.getString("end_reason")?.let(RunEndReason::valueOf),
            )
        }.singleOrNull()
    }
}
