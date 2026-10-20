package com.skippy.app

/**
 * Zeus does not link an "EXAM X" event to the course sessions of subject X: this is a best-effort
 * text match. It works for subjects whose name is used as-is or as a clear substring in the exam
 * title (e.g. "EXAM IGAF" / "IGAF", "EXAM PBS1" / "PBS"). It misses subjects only known by a
 * different code (e.g. "EXAM DRG1" vs "Droit Général"): those fall back to the provisional,
 * past-sessions based calculation until the person maps the exam by hand in Settings.
 */
object ExamMatch {
    const val AUTO_MAPPING = "__AUTO__"
    const val NONE_MAPPING = "__NONE__"

    private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]+")
    private val NON_ALPHA_REGEX = Regex("[^a-z]+")
    private val ROMAN_INDEX_REGEX = Regex("\\b(?:i|ii|iii|iv|v)\\b", RegexOption.IGNORE_CASE)
    private val EXAM_KEY_SUFFIX_REGEX = Regex("@(-?\\d+)$")

    private val EXAM_PREFIX_REGEX = Regex(
        "^(?:\\[?(?:exam|examen|partiel|ds|evaluation|midterm|rattrapage|qcm|cc|controle)\\b\\]?)[^a-z0-9]*",
        RegexOption.IGNORE_CASE
    )
    private val EXAM_ANYWHERE_REGEX = Regex(
        "\\b(?:exam|examen|partiel|ds|evaluation|midterm|final|rattrapage|qcm|cc|controle|continu)\\b",
        RegexOption.IGNORE_CASE
    )

    fun encodeExamKey(title: String, start: Long): String = "$title@$start"

    fun parseExamKey(key: String): Pair<String, Long?> {
        val match = EXAM_KEY_SUFFIX_REGEX.find(key) ?: return key to null
        val start = match.groupValues[1].toLongOrNull() ?: return key to null
        val title = key.substring(0, match.range.first)
        return title to start
    }

    fun displayExamKey(key: String, examStarts: Map<String, List<Long>> = emptyMap()): String {
        val (title, explicitStart) = parseExamKey(key)
        val start = explicitStart ?: examStarts[title]?.singleOrNull()
        return if (start != null && start != Long.MAX_VALUE && start > 86_400_000L) {
            "$title (${fmtDate(start)})"
        } else {
            title
        }
    }

    private fun squash(s: String): String = Repo.plain(s).replace(NON_ALPHANUM_REGEX, "")

    private fun coreAlpha(s: String): String =
        Repo.plain(s).replace(ROMAN_INDEX_REGEX, " ").replace(NON_ALPHA_REGEX, "")

    fun isExamTitle(subject: String): Boolean {
        val p = Repo.plain(parseExamKey(subject).first)
        return p.startsWith("exam") || EXAM_PREFIX_REGEX.containsMatchIn(p)
    }

    /**
     * Determines if a session represents an exam by checking:
     * 1. Activity type ID matching EXAM_TYPE_ID (type 2 in Zeus).
     * 2. Activity type label (customized in Settings) containing "exam".
     * 3. Subject title parsing (starts with "exam", "[exam]", "examen", "partiel", "ds", etc.).
     */
    fun isExamSession(session: Session, typeNames: Map<Int, String> = emptyMap()): Boolean {
        if (session.typeId == EXAM_TYPE_ID) return true
        val customType = typeNames[session.typeId]
        if (customType != null && Repo.plain(customType).contains("exam")) return true
        return isExamTitle(session.subject)
    }

    private fun remainder(examSubject: String): String {
        val p = Repo.plain(parseExamKey(examSubject).first)
        return p.replace(EXAM_PREFIX_REGEX, "")
            .replace(EXAM_ANYWHERE_REGEX, " ")
            .trim()
    }

    fun matches(examSubject: String, courseSubject: String): Boolean {
        val rem = remainder(examSubject)
        val a = squash(rem)
        val b = squash(courseSubject)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.contains(b) || b.contains(a)) return true

        val aAlpha = coreAlpha(rem)
        val bAlpha = coreAlpha(courseSubject)
        if (aAlpha.length >= 2 && bAlpha.length >= 2) {
            if (aAlpha.contains(bAlpha) || bAlpha.contains(aAlpha)) return true
            val aStem = if (aAlpha.length >= 4 && aAlpha.endsWith("s")) aAlpha.dropLast(1) else aAlpha
            val bStem = if (bAlpha.length >= 4 && bAlpha.endsWith("s")) bAlpha.dropLast(1) else bAlpha
            if (aStem.length >= 3 && bStem.length >= 3 && (aStem.contains(bStem) || bStem.contains(aStem))) {
                return true
            }
        }
        return false
    }

    /** Finds all auto-detected exam subjects for a given course subject among available exam subjects. */
    fun findAllAutoMatches(courseSubject: String, availableExams: List<String>): List<String> {
        val exact = availableExams.filter { parseExamKey(it).first.equals(courseSubject, ignoreCase = true) }
        val fuzzy = availableExams.filter {
            !parseExamKey(it).first.equals(courseSubject, ignoreCase = true) && matches(it, courseSubject)
        }
        return (exact + fuzzy).distinct()
    }

    /** Finds the auto-detected exam subject for a given course subject among available exam subjects. */
    fun findAutoMatch(courseSubject: String, availableExams: List<String>): String? =
        findAllAutoMatches(courseSubject, availableExams).firstOrNull()

    /** Resolves the exam titles for a course subject and returns Pair(resolvedExamTitles, isManual). */
    fun resolveExams(
        courseSubject: String,
        mappings: Map<String, List<String>>,
        availableExams: List<String>,
    ): Pair<List<String>, Boolean> {
        val mapping = mappings[courseSubject]
        return when {
            mapping == null -> findAllAutoMatches(courseSubject, availableExams) to false
            mapping.contains(NONE_MAPPING) -> emptyList<String>() to true
            mapping.contains(AUTO_MAPPING) -> findAllAutoMatches(courseSubject, availableExams) to false
            else -> mapping to true
        }
    }

    /** Resolves the exam title for a course subject and returns Pair(resolvedExamTitle, isManual). */
    fun resolveExam(
        courseSubject: String,
        mappings: Map<String, String>,
        availableExams: List<String>,
    ): Pair<String?, Boolean> {
        val listMappings = mappings.mapValues { (_, v) -> listOf(v) }
        val (exams, isManual) = resolveExams(courseSubject, listMappings, availableExams)
        return exams.firstOrNull() to isManual
    }
}
