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
                    // Try to find a player within the returned object
                    val player = invokeNoArg(value, "getPlayer")
                    if (player is Player) return player

                    // If it's a Resident or Mayor, it might have a getPlayer method
                    // If it's a Town, it has a getMayor which then has a Resident
                    val mayor = invokeNoArg(value, "getMayor")
                    if (mayor != null) {
                        val mayorPlayer = invokeNoArg(mayor, "getPlayer")
                        if (mayorPlayer is Player) return mayorPlayer
                    }

                    val resident = invokeNoArg(value, "getResident")
                    if (resident != null) {
                        val residentPlayer = invokeNoArg(resident, "getPlayer")
                        if (residentPlayer is Player) return residentPlayer
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
