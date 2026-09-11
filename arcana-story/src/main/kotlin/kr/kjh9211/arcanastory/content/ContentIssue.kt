package kr.kjh9211.arcanastory.content

enum class Severity {
    ERROR,
    WARNING,
}

/** [source]는 content root 기준 파일 경로, [path]는 파일 안의 YAML 경로(예: `steps[1].await[0].quest`)다. */
data class ContentIssue(
    val severity: Severity,
    val source: String,
    val path: String,
    val message: String,
) {
    override fun toString(): String = buildString {
        append('[').append(severity).append("] ").append(source)
        if (path.isNotEmpty()) append(" @ ").append(path)
        append(": ").append(message)
    }
}

class IssueCollector {
    private val collected = mutableListOf<ContentIssue>()

    fun add(issue: ContentIssue) {
        collected += issue
    }

    fun error(source: String, path: String, message: String) = add(ContentIssue(Severity.ERROR, source, path, message))

    fun warning(source: String, path: String, message: String) = add(ContentIssue(Severity.WARNING, source, path, message))

    fun issues(): List<ContentIssue> = collected.toList()
}

data class ValidationReport(
    val issues: List<ContentIssue>,
) {
    val errors: List<ContentIssue>
        get() = issues.filter { it.severity == Severity.ERROR }

    val warnings: List<ContentIssue>
        get() = issues.filter { it.severity == Severity.WARNING }

    val hasErrors: Boolean
        get() = issues.any { it.severity == Severity.ERROR }
}
