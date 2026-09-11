package kr.kjh9211.arcanastory.content

import kr.kjh9211.arcanastory.content.migration.LegacyStoryMapping
import kr.kjh9211.arcanastory.content.migration.LegacyStoryMappingParser
import kr.kjh9211.arcanastory.content.model.ChapterDefinition
import kr.kjh9211.arcanastory.content.model.CutsceneDefinition
import kr.kjh9211.arcanastory.content.parser.ChapterParser
import kr.kjh9211.arcanastory.content.parser.CutsceneParser
import kr.kjh9211.arcanastory.content.yaml.ContentYaml
import kr.kjh9211.arcanastory.content.yaml.YamlNode
import org.yaml.snakeyaml.error.YAMLException
import java.io.File
import java.io.IOException

data class LoadedContent(
    val chapters: List<ChapterDefinition>,
    val cutscenes: List<CutsceneDefinition>,
    /** 파싱 단계에서 발견한 문제. ContentValidator가 검증 결과에 함께 포함한다. */
    val issues: List<ContentIssue>,
)

data class MappingLoadResult(
    val mapping: LegacyStoryMapping?,
    val issues: List<ContentIssue>,
)

/**
 * content root 구조:
 * - `chapters/ ** / *.yml` — 파일 1개에 Chapter 1개
 * - `cutscenes/ ** / *.yml` — 파일 1개에 Cutscene 1개
 *
 * 한 파일의 문법 오류는 해당 파일만 건너뛰고 나머지는 계속 읽는다.
 */
class ContentLoader {

    fun load(root: File): LoadedContent {
        val issues = IssueCollector()
        val chapters = yamlFiles(root, CHAPTERS_DIR).mapNotNull { file -> read(root, file, issues)?.let(ChapterParser::parse) }
        val cutscenes = yamlFiles(root, CUTSCENES_DIR).mapNotNull { file -> read(root, file, issues)?.let(CutsceneParser::parse) }
        return LoadedContent(chapters, cutscenes, issues.issues())
    }

    fun loadMapping(text: String, source: String): MappingLoadResult {
        val issues = IssueCollector()
        val mapping = parseText(text, source, issues)?.let(LegacyStoryMappingParser::parse)
        return MappingLoadResult(mapping, issues.issues())
    }

    fun loadBundledMapping(): MappingLoadResult {
        val stream = ContentLoader::class.java.getResourceAsStream("/$BUNDLED_MAPPING")
            ?: return MappingLoadResult(
                mapping = null,
                issues = listOf(ContentIssue(Severity.ERROR, BUNDLED_MAPPING, "", "번들 매핑 리소스를 찾을 수 없습니다")),
            )
        return loadMapping(stream.use { it.readBytes().toString(Charsets.UTF_8) }, BUNDLED_MAPPING)
    }

    private fun yamlFiles(root: File, directoryName: String): List<File> {
        val directory = File(root, directoryName)
        if (!directory.isDirectory) return emptyList()
        return directory.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in YAML_EXTENSIONS }
            .sortedBy { it.invariantSeparatorsPath }
            .toList()
    }

    private fun read(root: File, file: File, issues: IssueCollector): YamlNode? {
        val source = file.relativeTo(root).invariantSeparatorsPath
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (exception: IOException) {
            issues.error(source, "", "파일을 읽을 수 없습니다: ${exception.message}")
            return null
        }
        return parseText(text, source, issues)
    }

    private fun parseText(text: String, source: String, issues: IssueCollector): YamlNode? {
        val parsed = try {
            ContentYaml.load(text)
        } catch (exception: YAMLException) {
            val detail = exception.message.orEmpty().lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
            issues.error(source, "", "YAML 문법 오류: $detail")
            return null
        }
        return YamlNode(parsed, source, "", issues)
    }

    companion object {
        const val CHAPTERS_DIR = "chapters"
        const val CUTSCENES_DIR = "cutscenes"
        const val BUNDLED_MAPPING = "migration/legacy-story-mapping.yml"

        private val YAML_EXTENSIONS = setOf("yml", "yaml")
    }
}
