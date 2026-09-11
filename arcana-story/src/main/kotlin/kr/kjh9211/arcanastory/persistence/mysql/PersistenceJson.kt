package kr.kjh9211.arcanastory.persistence.mysql

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kr.kjh9211.arcanastory.content.model.RewardComponent
import kr.kjh9211.arcanastory.persistence.PersistenceException
import kr.kjh9211.arcanastory.persistence.SnapshotLocation
import kr.kjh9211.arcanastory.persistence.TemporaryChoice
import kr.kjh9211.arcanastory.persistence.TemporaryFlag
import java.util.UUID

/** JSON 컬럼의 형식을 명시적으로 고정한다(리플렉션 직렬화를 쓰지 않는다). */
internal object PersistenceJson {

    fun stringList(values: Collection<String>): String = JsonArray().apply { values.forEach(::add) }.toString()

    fun parseStringList(json: String): List<String> = JsonParser.parseString(json).asJsonArray.map { it.asString }

    fun uuidList(values: List<UUID>): String = stringList(values.map(UUID::toString))

    fun parseUuidList(json: String): List<UUID> = parseStringList(json).map(UUID::fromString)

    fun location(location: SnapshotLocation): String = JsonObject().apply {
        addProperty("world", location.world)
        addProperty("x", location.x)
        addProperty("y", location.y)
        addProperty("z", location.z)
        addProperty("yaw", location.yaw)
        addProperty("pitch", location.pitch)
    }.toString()

    fun parseLocation(json: String): SnapshotLocation {
        val value = JsonParser.parseString(json).asJsonObject
        return SnapshotLocation(
            world = value["world"].asString,
            x = value["x"].asDouble,
            y = value["y"].asDouble,
            z = value["z"].asDouble,
            yaw = value["yaw"].asFloat,
            pitch = value["pitch"].asFloat,
        )
    }

    fun temporaryChoices(choices: Map<String, TemporaryChoice>): String = JsonObject().apply {
        choices.forEach { (choiceId, choice) ->
            add(
                choiceId,
                JsonObject().apply {
                    addProperty("option", choice.optionId)
                    addProperty("decided_by", choice.decidedBy.toString())
                },
            )
        }
    }.toString()

    fun parseTemporaryChoices(json: String): Map<String, TemporaryChoice> =
        JsonParser.parseString(json).asJsonObject.entrySet().associate { (choiceId, element) ->
            val value = element.asJsonObject
            choiceId to TemporaryChoice(value["option"].asString, UUID.fromString(value["decided_by"].asString))
        }

    fun temporaryFlags(flags: Map<String, TemporaryFlag>): String = JsonObject().apply {
        flags.forEach { (flag, value) ->
            add(
                flag,
                JsonObject().apply {
                    addProperty("value", value.value)
                    addProperty("persist", value.persist)
                },
            )
        }
    }.toString()

    fun parseTemporaryFlags(json: String): Map<String, TemporaryFlag> =
        JsonParser.parseString(json).asJsonObject.entrySet().associate { (flag, element) ->
            val value = element.asJsonObject
            flag to TemporaryFlag(value["value"].asString, value["persist"].asBoolean)
        }

    fun rewardComponent(component: RewardComponent): String = JsonObject().apply {
        when (component) {
            is RewardComponent.Item -> {
                addProperty("type", "item")
                addProperty("item", component.item)
                addProperty("amount", component.amount)
            }
            is RewardComponent.Experience -> {
                addProperty("type", "experience")
                addProperty("points", component.points)
            }
            is RewardComponent.Command -> {
                addProperty("type", "command")
                addProperty("command", component.command)
            }
            is RewardComponent.Money -> {
                addProperty("type", "money")
                addProperty("amount", component.amount)
            }
            is RewardComponent.SharedUnlock -> {
                addProperty("type", component.kind)
                addProperty("id", component.id)
            }
        }
    }.toString()

    fun parseRewardComponent(json: String): RewardComponent {
        val value = JsonParser.parseString(json).asJsonObject
        return when (val type = value["type"].asString) {
            "item" -> RewardComponent.Item(value["item"].asString, value["amount"].asInt)
            "experience" -> RewardComponent.Experience(value["points"].asInt)
            "command" -> RewardComponent.Command(value["command"].asString)
            "money" -> RewardComponent.Money(value["amount"].asDouble)
            "title", "cosmetic", "unlock" -> RewardComponent.SharedUnlock(type, value["id"].asString)
            else -> throw PersistenceException("알 수 없는 보상 payload type '$type'")
        }
    }
}
