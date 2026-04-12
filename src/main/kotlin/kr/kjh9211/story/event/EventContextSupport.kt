package kr.kjh9211.story.event

import kr.kjh9211.story.story.StoryExecutionContext
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.lang.reflect.Method

object EventContextSupport {
    fun createContext(sender: CommandSender?, placeholders: Map<String, String> = emptyMap()): StoryExecutionContext {
        val actualSender = sender ?: Bukkit.getConsoleSender()
        return StoryExecutionContext(actualSender, placeholders)
    }

    fun extractSenderFromMethods(source: Any, vararg methodNames: String): CommandSender? {
        methodNames.forEach { methodName ->
            val value = invokeNoArg(source, methodName) ?: return@forEach
            when (value) {
                is Player -> return value
                is CommandSender -> return value
                else -> {
                    val player = invokeNoArg(value, "getPlayer")
                    if (player is Player) {
                        return player
                    }
                }
            }
        }
        return null
    }

    fun readName(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            else -> invokeNoArg(value, "getName")?.toString() ?: value.toString()
        }
    }

    fun invokeNoArg(instance: Any, methodName: String): Any? {
        return try {
            val method: Method = instance.javaClass.getMethod(methodName)
            method.invoke(instance)
        } catch (_: ReflectiveOperationException) {
            null
        }
    }
}
