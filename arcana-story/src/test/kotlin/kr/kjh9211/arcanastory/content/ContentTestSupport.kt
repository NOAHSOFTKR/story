package kr.kjh9211.arcanastory.content

import kr.kjh9211.arcanastory.content.model.ChapterDefinition
import kr.kjh9211.arcanastory.content.model.CutsceneDefinition
import kr.kjh9211.arcanastory.content.parser.ChapterParser
import kr.kjh9211.arcanastory.content.parser.CutsceneParser
import kr.kjh9211.arcanastory.content.yaml.ContentYaml
import kr.kjh9211.arcanastory.content.yaml.YamlNode

internal data class Parsed<T>(val value: T?, val issues: List<ContentIssue>)

internal fun parseChapter(yaml: String, source: String = "chapters/test.yml"): Parsed<ChapterDefinition> {
    val issues = IssueCollector()
    val chapter = ChapterParser.parse(YamlNode(ContentYaml.load(yaml.trimIndent()), source, "", issues))
    return Parsed(chapter, issues.issues())
}

internal fun parseCutscene(yaml: String, source: String = "cutscenes/test.yml"): Parsed<CutsceneDefinition> {
    val issues = IssueCollector()
    val cutscene = CutsceneParser.parse(YamlNode(ContentYaml.load(yaml.trimIndent()), source, "", issues))
    return Parsed(cutscene, issues.issues())
}

internal fun loadedContent(chapters: List<String>, cutscenes: List<String> = emptyList()): LoadedContent {
    val parsedChapters = chapters.mapIndexed { index, yaml -> parseChapter(yaml, "chapters/chapter$index.yml") }
    val parsedCutscenes = cutscenes.mapIndexed { index, yaml -> parseCutscene(yaml, "cutscenes/cutscene$index.yml") }
    return LoadedContent(
        chapters = parsedChapters.mapNotNull { it.value },
        cutscenes = parsedCutscenes.mapNotNull { it.value },
        issues = parsedChapters.flatMap { it.issues } + parsedCutscenes.flatMap { it.issues },
    )
}
