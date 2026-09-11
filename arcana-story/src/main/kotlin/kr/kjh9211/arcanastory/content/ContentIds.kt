package kr.kjh9211.arcanastory.content

internal object ContentIds {
    /**
     * Chapter·Cutscene id. History 키로 영구 저장되므로 번호가 아닌 의미 id를 점으로 구분해 쓴다
     * (예: `prologue.exile`). 표시 순서·번호는 `arc`/`order`로만 표현한다.
     */
    val QUALIFIED = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)*$")

    /** Chapter 내부 id: step, checkpoint, branch, choice, option, dialogue, node, location, flag. */
    val LOCAL = Regex("^[a-z0-9_]+$")

    /** namespace를 생략할 수 있는 리소스 키: sound, particle, effect. */
    val RESOURCE_KEY = Regex("^([a-z0-9_.-]+:)?[a-z0-9_./-]+$")

    /** namespace가 필수인 아이템 id: `minecraft:diamond`, `arcana:dragon_core`. */
    val ITEM_ID = Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")
}
