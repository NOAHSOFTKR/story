package kr.kjh9211.arcanastory.content.model

/**
 * ArcanaStory가 소유하는 컷신 정의. `vanilla` step은 CutThin step 형식과 호환되며 renderer adapter가
 * 변환한다. 이 모델에는 CutThin 타입을 두지 않아, 나중에 CutThin을 흡수해도 콘텐츠를 바꾸지 않는다.
 */
data class CutsceneDefinition(
    val id: String,
    val name: String,
    val freeze: Boolean,
    val vanillaSteps: List<Map<String, Any?>>,
    /** Arcana 모드 전용 연출 트랙 참조. 없으면 모드 클라이언트도 vanilla step으로 재생한다. */
    val modTrack: String?,
    val source: String,
)
