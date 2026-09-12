package kr.kjh9211.arcanastory.scheduling

import org.bukkit.plugin.Plugin

/** 블로킹 I/O는 [runAsync], 도메인 상태 변경은 [runSync](메인 스레드)에서 실행한다. */
interface TaskScheduler {
    fun runAsync(task: () -> Unit)

    fun runSync(task: () -> Unit)
}

class BukkitTaskScheduler(private val plugin: Plugin) : TaskScheduler {
    override fun runAsync(task: () -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { task() })
    }

    override fun runSync(task: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable { task() })
    }
}
