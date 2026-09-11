package kr.kjh9211.arcanastory.content.parser

import kr.kjh9211.arcanastory.content.model.ChapterRewards
import kr.kjh9211.arcanastory.content.model.RewardComponent
import kr.kjh9211.arcanastory.content.yaml.YamlNode

internal object RewardParser {

    fun parse(node: YamlNode): ChapterRewards {
        if (!node.expectMap()) return ChapterRewards.NONE
        node.warnUnknownKeys(setOf("first_clear", "branches"))
        val branches = linkedMapOf<String, List<RewardComponent>>()
        node.child("branches").entries().forEach { (branchId, child) -> branches[branchId] = parseComponents(child) }
        return ChapterRewards(parseComponents(node.child("first_clear")), branches)
    }

    private fun parseComponents(node: YamlNode): List<RewardComponent> = node.elements().mapNotNull(::parseComponent)

    // 금지된 보상 유형(command, money, title/cosmetic/unlock)도 모델로 읽어 두고, 거부는 ContentValidator가 한다.
    private fun parseComponent(node: YamlNode): RewardComponent? {
        if (!node.requireMap()) return null
        val typeNode = node.child("type")
        val type = typeNode.requiredString()?.trim()?.lowercase() ?: return null
        return when (type) {
            "item" -> {
                node.warnUnknownKeys(setOf("type", "item", "amount"))
                node.child("item").requiredString()?.let { RewardComponent.Item(it, node.child("amount").int(1)) }
            }
            "experience" -> {
                node.warnUnknownKeys(setOf("type", "points"))
                node.child("points").requiredInt()?.let { RewardComponent.Experience(it) }
            }
            "command" -> {
                node.warnUnknownKeys(setOf("type", "command"))
                node.child("command").requiredString()?.let { RewardComponent.Command(it) }
            }
            "money" -> {
                node.warnUnknownKeys(setOf("type", "amount"))
                node.child("amount").requiredDouble()?.let { RewardComponent.Money(it) }
            }
            "title", "cosmetic", "unlock" -> {
                node.warnUnknownKeys(setOf("type", "id"))
                node.child("id").requiredString()?.let { RewardComponent.SharedUnlock(type, it) }
            }
            else -> {
                typeNode.addError("알 수 없는 보상 타입 '$type'")
                null
            }
        }
    }
}
