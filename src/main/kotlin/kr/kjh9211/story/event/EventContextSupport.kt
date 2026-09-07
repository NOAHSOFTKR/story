package kr.kjh9211.story.event

import kr.kjh9211.story.story.StoryExecutionContext
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
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

    fun readStableId(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            else -> listOf("getInternalName", "getId", "getName")
                .asSequence()
                .mapNotNull { method -> invokeNoArg(value, method)?.toString() }
                .firstOrNull { it.isNotBlank() }
        }
    }

    fun readStringMap(value: Any?): Map<String, String> {
        val source = value as? Map<*, *> ?: return emptyMap()
        return source.mapNotNull { (key, item) ->
            val stringKey = key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val stringValue = item?.toString() ?: return@mapNotNull null
            stringKey to stringValue
        }.toMap()
    }

    fun itemIdentifier(item: ItemStack?): String? {
        if (item == null || item.type.isAir) {
            return null
        }
        val customId = try {
            val customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack")
            val byItemStack = customStackClass.getMethod("byItemStack", ItemStack::class.java)
            val customStack = byItemStack.invoke(null, item)
            customStack?.let { invokeNoArg(it, "getNamespacedID")?.toString() ?: invokeNoArg(it, "getId")?.toString() }
        } catch (_: ReflectiveOperationException) {
            null
        }
        return customId ?: item.type.name
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
