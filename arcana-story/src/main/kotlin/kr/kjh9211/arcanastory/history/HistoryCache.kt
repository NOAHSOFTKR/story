package kr.kjh9211.arcanastory.history

import kr.kjh9211.arcanastory.persistence.HistoryRepository
import kr.kjh9211.arcanastory.scheduling.TaskScheduler
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 온라인 플레이어의 [StoryHistory]를 메모리에 들고 있는다.
 *
 * 모든 상태 변경은 메인 스레드에서만 일어나고, DB 로드만 비동기다. 로드가 끝나기 전에 플레이어가 나가거나
 * 다시 접속하면 세대(generation) 비교로 오래된 결과를 버린다.
 */
class HistoryCache(
    private val repository: HistoryRepository,
    private val scheduler: TaskScheduler,
    private val logger: Logger,
) {
    private val entries = HashMap<UUID, StoryHistory>()
    private val generations = HashMap<UUID, Long>()
    private var nextGeneration = 0L

    fun get(playerId: UUID): StoryHistory? = entries[playerId]

    fun isLoaded(playerId: UUID): Boolean = playerId in entries

    /** join 시 호출한다. 로드가 끝나면 메인 스레드에서 [onLoaded] 또는 [onFailed]를 부른다. */
    fun load(
        playerId: UUID,
        onLoaded: (StoryHistory) -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
    ) {
        val generation = ++nextGeneration
        generations[playerId] = generation
        scheduler.runAsync {
            val result = runCatching { repository.load(playerId) }
            scheduler.runSync {
                if (generations[playerId] != generation) return@runSync
                result
                    .onSuccess { history ->
                        entries[playerId] = history
                        onLoaded(history)
                    }
                    .onFailure { error ->
                        logger.log(Level.SEVERE, "Story History를 불러오지 못했습니다: $playerId", error)
                        onFailed(error)
                    }
            }
        }
    }

    /** commit 이후 최신 History로 교체한다. 이미 나간 플레이어면 무시한다. */
    fun replace(history: StoryHistory) {
        if (history.playerId in generations) entries[history.playerId] = history
    }

    fun unload(playerId: UUID) {
        entries.remove(playerId)
        generations.remove(playerId)
    }

    fun clear() {
        entries.clear()
        generations.clear()
    }
}
