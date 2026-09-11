package kr.kjh9211.arcanastory.history

import kr.kjh9211.arcanastory.persistence.ChapterCompletionCommit
import kr.kjh9211.arcanastory.persistence.CommitOutcome
import kr.kjh9211.arcanastory.persistence.HistoryRepository
import kr.kjh9211.arcanastory.scheduling.TaskScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.logging.Logger

class HistoryCacheTest {

    private class ManualScheduler : TaskScheduler {
        private val async = ArrayDeque<() -> Unit>()
        private val sync = ArrayDeque<() -> Unit>()

        override fun runAsync(task: () -> Unit) {
            async.addLast(task)
        }

        override fun runSync(task: () -> Unit) {
            sync.addLast(task)
        }

        /** 비동기 작업만 실행하고 결과 반영(sync)은 미룬다. */
        fun runAsyncOnly() {
            while (async.isNotEmpty()) async.removeFirst().invoke()
        }

        fun drain() {
            while (async.isNotEmpty() || sync.isNotEmpty()) {
                while (async.isNotEmpty()) async.removeFirst().invoke()
                while (sync.isNotEmpty()) sync.removeFirst().invoke()
            }
        }
    }

    private class FakeRepository : HistoryRepository {
        var failure: Throwable? = null
        var loads = 0
        val histories = mutableMapOf<UUID, StoryHistory>()

        override fun load(playerId: UUID): StoryHistory {
            loads++
            failure?.let { throw it }
            return histories[playerId] ?: StoryHistory.empty(playerId)
        }

        override fun commitChapterCompletion(commit: ChapterCompletionCommit): CommitOutcome =
            throw UnsupportedOperationException()
    }

    private val playerId = UUID.randomUUID()
    private val repository = FakeRepository()
    private val scheduler = ManualScheduler()
    private val cache = HistoryCache(repository, scheduler, Logger.getLogger("HistoryCacheTest"))

    @Test
    fun `로드가 끝나면 캐시에 담고 콜백을 부른다`() {
        var loaded: StoryHistory? = null

        cache.load(playerId, onLoaded = { loaded = it })
        assertNull(cache.get(playerId))

        scheduler.drain()

        assertSame(cache.get(playerId), loaded)
        assertTrue(cache.isLoaded(playerId))
    }

    @Test
    fun `로드 도중 나가면 결과를 버린다`() {
        cache.load(playerId)
        scheduler.runAsyncOnly()

        cache.unload(playerId)
        scheduler.drain()

        assertNull(cache.get(playerId))
    }

    @Test
    fun `나갔다 다시 들어오면 마지막 로드 결과만 반영한다`() {
        val first = StoryHistory.empty(playerId)
        val second = StoryHistory.empty(playerId).copy(flags = mapOf("second" to "true"))
        repository.histories[playerId] = first

        cache.load(playerId)
        scheduler.runAsyncOnly()
        cache.unload(playerId)

        repository.histories[playerId] = second
        cache.load(playerId)
        scheduler.drain()

        assertEquals(mapOf("second" to "true"), cache.get(playerId)?.flags)
        assertEquals(2, repository.loads)
    }

    @Test
    fun `로드가 실패하면 캐시에 담지 않고 실패 콜백을 부른다`() {
        repository.failure = IllegalStateException("DB down")
        var failure: Throwable? = null

        cache.load(playerId, onFailed = { failure = it })
        scheduler.drain()

        assertNull(cache.get(playerId))
        assertEquals("DB down", failure?.message)
    }

    @Test
    fun `replace는 추적 중인 플레이어에게만 적용된다`() {
        val updated = StoryHistory.empty(playerId).copy(flags = mapOf("done" to "true"))

        cache.replace(updated)
        assertNull(cache.get(playerId))

        cache.load(playerId)
        scheduler.drain()
        cache.replace(updated)

        assertEquals(mapOf("done" to "true"), cache.get(playerId)?.flags)
    }
}
