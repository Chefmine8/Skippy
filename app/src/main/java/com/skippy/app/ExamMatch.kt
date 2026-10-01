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

    private fun squash(s: String): String = Repo.plain(s).replace(Regex("[^a-z0-9]+"), "")

    fun isExamTitle(subject: String): Boolean = Repo.plain(subject).startsWith("exam")

    /**
     * Determines if a session represents an exam by checking:
     * 1. Activity type ID matching EXAM_TYPE_ID (type 2 in Zeus).
     * 2. Activity type label (customized in Settings) containing "exam".
     * 3. Subject title parsing (starts with "exam").
     */
    fun isExamSession(session: Session, typeNames: Map<Int, String> = emptyMap()): Boolean {
        if (session.typeId == EXAM_TYPE_ID) return true
        val customType = typeNames[session.typeId]
        if (customType != null && Repo.plain(customType).contains("exam")) return true
        return isExamTitle(session.subject)
    }

    private fun remainder(examSubject: String): String =
        Repo.plain(examSubject).removePrefix("exam").trim()

    fun matches(examSubject: String, courseSubject: String): Boolean {
        val a = squash(remainder(examSubject))
        val b = squash(courseSubject)
        if (a.isEmpty() || b.isEmpty()) return false
        return a.contains(b) || b.contains(a)
    }

    /** Finds the auto-detected exam subject for a given course subject among available exam subjects. */
    fun findAutoMatch(courseSubject: String, availableExams: List<String>): String? =
        availableExams.firstOrNull { it.equals(courseSubject, ignoreCase = true) }
            ?: availableExams.firstOrNull { matches(it, courseSubject) }

    /** Resolves the exam title for a course subject and returns Pair(resolvedExamTitle, isManual). */
    fun resolveExam(
        courseSubject: String,
        mappings: Map<String, String>,
        availableExams: List<String>,
    ): Pair<String?, Boolean> {
        val mapping = mappings[courseSubject]
        return when {
            mapping == NONE_MAPPING -> null to true
            (mapping != null) && (mapping != AUTO_MAPPING) -> mapping to true
            else -> findAutoMatch(courseSubject, availableExams) to false
        }
    }
}
