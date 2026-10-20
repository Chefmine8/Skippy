package com.skippy.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Attendance figures for one (subject, activity type) pair, computed over the WHOLE term:
 * [total] is every session of that pair up to the subject's first "Examen" (idType 2) session,
 * not just the ones already past. The skip budget is fixed once and does not grow back.
 */
data class SubjectStats(
    val subject: String,
    val typeId: Int,
    val typeLabel: String,
    val present: Int,      // PRESENT + JUSTIFIED, past sessions only
    val absent: Int,       // ABSENT, past sessions only
    val pending: Int,      // past sessions not answered yet (informational, does not affect the budget)
    val upcoming: Int,     // sessions not started/finished yet, within the term
    val total: Int,        // every session of this pair within the term (past + upcoming)
    val allowed: Int,      // floor(total * (100-pct)/100): total absences allowed for the whole term
    val margin: Int,       // allowed - absent; <0 means the term is already unrecoverable for this pair
    val examFound: Boolean,// false => no "Examen" session seen yet for this subject: total is only an estimate
    val pref: Int,         // per subject: 0 important, 1 normal, 2 skip first
    val mappedExamTitle: String? = null,
    val isExamManual: Boolean = false,
    val associatedExams: List<SubjectExamInfo> = emptyList(),
    val targetExam: SubjectExamInfo? = null,
    val allExamsPassed: Boolean = false,
    val originalSubjects: List<String> = listOf(subject),
    val examPeriodStats: Map<String, ExamPeriodStats> = emptyMap(),
    val endOfTermStats: ExamPeriodStats? = null,
) {
    val key: String get() = Stats.key(subject, typeId)
    val title: String get() = "$subject · $typeLabel"
    val answered: Int get() = present + absent
    val rate: Int? get() = if (answered == 0) null else (present * 100f / answered).roundToInt()

    fun forExam(exam: SubjectExamInfo?): SubjectStats {
        if (exam == null) {
            val ep = endOfTermStats ?: return this
            return copy(
                present = ep.present,
                absent = ep.absent,
                pending = ep.pending,
                upcoming = ep.upcoming,
                total = ep.total,
                allowed = ep.allowed,
                margin = ep.margin,
                mappedExamTitle = associatedExams.firstOrNull()?.indexedLabel,
                targetExam = null,
            )
        }
        val ep = examPeriodStats[exam.indexedLabel] ?: return this
        return copy(
            present = ep.present,
            absent = ep.absent,
            pending = ep.pending,
            upcoming = ep.upcoming,
            total = ep.total,
            allowed = ep.allowed,
            margin = ep.margin,
            mappedExamTitle = exam.indexedLabel,
            targetExam = exam,
        )
    }
}

enum class Badge { SKIP_SUGGESTED, CAN_SKIP, ATTEND, IMPORTANT, MUST_ATTEND }

data class TodayItem(val session: Session, val status: Status?, val badge: Badge?)

object Stats {
    fun key(subject: String, typeId: Int) = "$subject#$typeId"
    fun key(s: Session) = key(s.subject, s.typeId)

    private fun mergeContiguousExamStarts(list: List<Session>): List<Long> {
        val sorted = list.sortedWith(compareBy({ it.start }, { it.end }))
        if (sorted.isEmpty()) return emptyList()
        val starts = mutableListOf<Long>()
        var clusterStart = sorted.first().start
        var clusterEnd = maxOf(sorted.first().start, sorted.first().end)
        starts.add(clusterStart)
        for (i in 1 until sorted.size) {
            val s = sorted[i]
            val sEnd = maxOf(s.start, s.end)
            if (s.start <= clusterEnd) {
                if (sEnd > clusterEnd) clusterEnd = sEnd
            } else {
                clusterStart = s.start
                clusterEnd = sEnd
                starts.add(clusterStart)
            }
        }
        return starts
    }

    /** All distinct "Examen" session start times per exam title, sorted chronologically. */
    fun examAllStartTimes(
        sessions: List<Session>,
        typeNames: Map<Int, String> = emptyMap(),
        extraExamTitles: Set<String> = emptySet(),
    ): Map<String, List<Long>> {
        val detectedExams = sessions.filter { ExamMatch.isExamSession(it, typeNames) }
        val detectedTitles = detectedExams.map { it.subject }.toSet()
        val extraExams = if (extraExamTitles.isEmpty()) emptyList() else {
            sessions.filter { it.subject in extraExamTitles && it.subject !in detectedTitles }
        }
        return (detectedExams + extraExams)
            .groupBy { it.subject }
            .mapValues { (_, list) -> mergeContiguousExamStarts(list) }
            .filterValues { it.isNotEmpty() }
    }

    /** Earliest "Examen" session start time per exam title. */
    fun examCutoffs(sessions: List<Session>, typeNames: Map<Int, String> = emptyMap()): Map<String, Long> =
        examAllStartTimes(sessions, typeNames).mapValues { (_, starts) -> starts.first() }

    /** Builds subject merges when two or more subjects share an exam. */
    fun resolveSubjectMerges(
        sessions: List<Session>,
        examMappings: Map<String, List<String>>,
        availableExams: List<String>,
        typeNames: Map<Int, String> = emptyMap(),
    ): Map<String, String> {
        val courseSubjects = sessions.filter { !ExamMatch.isExamSession(it, typeNames) }
            .map { it.subject }
            .distinct()

        val subjectExams = courseSubjects.associateWith { subj ->
            ExamMatch.resolveExams(subj, examMappings, availableExams).first
        }

        val parent = mutableMapOf<String, String>()
        fun find(s: String): String {
            val p = parent.getOrPut(s) { s }
            if (p == s) return s
            val root = find(p)
            parent[s] = root
            return root
        }
        fun union(a: String, b: String) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootA] = rootB
        }

        val examToSubjects = mutableMapOf<String, MutableList<String>>()
        for ((subj, exams) in subjectExams) {
            for (exam in exams) {
                val examTitle = ExamMatch.parseExamKey(exam).first
                if (examTitle.isNotBlank()) {
                    examToSubjects.getOrPut(examTitle) { mutableListOf() }.add(subj)
                }
            }
        }

        for (subjs in examToSubjects.values) {
            val first = subjs.first()
            for (i in 1 until subjs.size) {
                union(first, subjs[i])
            }
        }

        val components = mutableMapOf<String, MutableList<String>>()
        for (subj in courseSubjects) {
            components.getOrPut(find(subj)) { mutableListOf() }.add(subj)
        }

        val result = mutableMapOf<String, String>()
        for ((_, group) in components) {
            if (group.size == 1) {
                result[group.first()] = group.first()
            } else {
                val groupExams = group.flatMap { subjectExams[it].orEmpty() }
                    .map { ExamMatch.parseExamKey(it).first }
                    .distinct()
                val commonExam = groupExams.firstOrNull()?.replace(Regex("(?i)^exam\\s*"), "")?.trim()
                val mergedName = if (!commonExam.isNullOrBlank() && (group.any { it.contains(commonExam, ignoreCase = true) } || commonExam.length > 2)) {
                    commonExam
                } else {
                    group.sorted().joinToString(" / ")
                }
                for (s in group) {
                    result[s] = mergedName
                }
            }
        }
        return result
    }

    /** Resolves multi-hop manual subject merges (e.g. A -> B and B -> C => A -> C). */
    fun resolveTransitiveMerges(merges: Map<String, String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (start in merges.keys) {
            var current = start
            val visited = mutableSetOf<String>()
            while (current in merges && current !in visited) {
                visited.add(current)
                current = merges[current] ?: break
            }
            if (current != start) {
                result[start] = current
            }
        }
        return result
    }

    /**
     * Whole-term stats for every (subject, type) pair.
     * Required attendance is [pct]% over [total] sessions, so the absence budget is
     * `allowed = floor(total * (100-pct) / 100)`, and `margin = allowed - confirmed absences`.
     * Unanswered sessions never count against the budget, only confirmed ABSENT ones do.
     */
    fun compute(
        sessions: List<Session>,
        att: Map<String, Status>,
        prefs: Map<String, Int>,
        examMappings: Map<String, List<String>>,
        availableExams: List<String>,
        now: Long,
        pct: Int,
        typeNames: Map<Int, String> = emptyMap(),
        typeLabel: (Int) -> String,
        mergeSubjectsByExam: Boolean = false,
        manualMerges: Map<String, String> = emptyMap(),
        allSessionsForCutoffs: List<Session> = sessions,
    ): List<SubjectStats> {
        val normalizedMappings = examMappings
        val manuallyMappedTitles = normalizedMappings.values.flatten()
            .filter { it != ExamMatch.AUTO_MAPPING && it != ExamMatch.NONE_MAPPING }
            .map { ExamMatch.parseExamKey(it).first }
            .toSet()
        val allExamStarts = examAllStartTimes(allSessionsForCutoffs, typeNames, manuallyMappedTitles)
        val resolvedManualMerges = resolveTransitiveMerges(manualMerges)
        val mergeMap = if (mergeSubjectsByExam) {
            resolveSubjectMerges(sessions, normalizedMappings, availableExams, typeNames) + resolvedManualMerges
        } else {
            resolvedManualMerges
        }

        val (_, rawCourseSessions) = sessions.partition { ExamMatch.isExamSession(it, typeNames) }
        val courseSessions = if (mergeMap.isEmpty()) rawCourseSessions else rawCourseSessions.map { s ->
            val merged = mergeMap[s.subject] ?: s.subject
            if (merged != s.subject) s.copy(subject = merged, originalSubject = s.subject) else s
        }

        val courseBySubject = courseSessions.groupBy { it.subject }

        return courseBySubject.flatMap { (subject, subjectSessions) ->
            val originalSubjs = (mergeMap.filterValues { it == subject }.keys + subject).toSet()
            val resolvedPairs = originalSubjs.map { ExamMatch.resolveExams(it, normalizedMappings, availableExams) }
            val resolvedExams = resolvedPairs.flatMap { it.first }.distinct()
            val isManual = resolvedPairs.any { it.second }

            val examList = resolvedExams.flatMap { entry ->
                val (title, explicitStart) = ExamMatch.parseExamKey(entry)
                if (explicitStart != null) {
                    listOf(title to explicitStart)
                } else {
                    val starts = allExamStarts[title]
                    if (starts.isNullOrEmpty()) listOf(title to Long.MAX_VALUE)
                    else starts.map { start -> title to start }
                }
            }.distinct().sortedWith(compareBy({ it.second }, { it.first.lowercase() }))

            val examInfos = examList.mapIndexed { idx, (title, start) ->
                val indexedLabel = if (examList.size > 1) "$subject ${idx + 1}" else title
                SubjectExamInfo(
                    originalTitle = title,
                    indexedLabel = indexedLabel,
                    start = start,
                    isNext = false,
                )
            }

            val targetIdx = examInfos.indexOfFirst { it.start > now && it.start != Long.MAX_VALUE }
                .takeIf { it >= 0 }
                ?: examInfos.indexOfFirst { it.start == Long.MAX_VALUE }.takeIf { it >= 0 }
            val allExamsPassed = examInfos.isNotEmpty() && examInfos.all { it.start <= now && it.start != Long.MAX_VALUE }
            val finalExamInfos = examInfos.mapIndexed { idx, info -> info.copy(isNext = idx == targetIdx) }
            val targetExam = targetIdx?.let { finalExamInfos[it] }
            val examFound = finalExamInfos.any { it.start != Long.MAX_VALUE } || isManual

            val cutoff = targetExam?.takeIf { it.start != Long.MAX_VALUE }?.start

            fun calcPeriod(typeSessions: List<Session>, examCutoff: Long?): ExamPeriodStats {
                val filtered = if (examCutoff != null) typeSessions.filter { it.start < examCutoff } else typeSessions
                var p = 0
                var a = 0
                var pend = 0
                var up = 0
                for (s in filtered) {
                    if (s.end <= now) {
                        when (att[s.uid]) {
                            null -> pend++
                            Status.ABSENT -> a++
                            else -> p++
                        }
                    } else up++
                }
                val total = filtered.size
                val allowed = total * (100 - pct) / 100
                return ExamPeriodStats(
                    present = p,
                    absent = a,
                    pending = pend,
                    upcoming = up,
                    total = total,
                    allowed = allowed,
                    margin = allowed - a,
                )
            }

            subjectSessions.groupBy { it.typeId }.values.map { typeSessions ->
                val first = typeSessions.first()
                val fullTerm = calcPeriod(typeSessions, null)
                val active = if (cutoff != null) calcPeriod(typeSessions, cutoff) else fullTerm
                val perExam = finalExamInfos.associate { ex ->
                    ex.indexedLabel to calcPeriod(typeSessions, ex.start.takeIf { it != Long.MAX_VALUE })
                }
                SubjectStats(
                    subject = subject,
                    typeId = first.typeId,
                    typeLabel = typeLabel(first.typeId),
                    present = active.present,
                    absent = active.absent,
                    pending = active.pending,
                    upcoming = active.upcoming,
                    total = active.total,
                    allowed = active.allowed,
                    margin = active.margin,
                    examFound = examFound,
                    pref = prefs[subject] ?: originalSubjs.mapNotNull { prefs[it] }.firstOrNull() ?: 1,
                    mappedExamTitle = targetExam?.indexedLabel ?: finalExamInfos.firstOrNull()?.indexedLabel,
                    isExamManual = isManual,
                    associatedExams = finalExamInfos,
                    targetExam = targetExam,
                    allExamsPassed = allExamsPassed,
                    originalSubjects = originalSubjs.toList().sorted(),
                    examPeriodStats = perExam,
                    endOfTermStats = fullTerm,
                )
            }
        }.sortedWith(compareBy({ it.subject.lowercase() }, { it.typeId }))
    }

    /** Aggregates all activity types for a subject into overall stats. */
    fun totalStats(rows: List<SubjectStats>, pct: Int): TotalSubjectStats {
        val first = rows.first()
        val total = rows.sumOf { it.total }
        val present = rows.sumOf { it.present }
        val absent = rows.sumOf { it.absent }
        val pending = rows.sumOf { it.pending }
        val upcoming = rows.sumOf { it.upcoming }
        val allowed = total * (100 - pct) / 100
        return TotalSubjectStats(
            subject = first.subject,
            present = present,
            absent = absent,
            pending = pending,
            upcoming = upcoming,
            total = total,
            allowed = allowed,
            margin = allowed - absent,
            examFound = first.examFound,
            associatedExams = first.associatedExams,
            targetExam = first.targetExam,
            allExamsPassed = first.allExamsPassed,
        )
    }

    /** Pairs where the user already skipped at least once and is close to (or over) the budget. */
    fun alerts(stats: List<SubjectStats>, alertAt: Int): List<SubjectStats> =
        stats.filter { it.absent >= 1 && it.margin <= alertAt }.sortedBy { it.margin }

    /**
     * Today's plan.
     *
     * A pending session today is a skip *candidate* only if:
     *  - its subject is not marked "important", and
     *  - the pair still has at least 1 session of margin, and
     *  - EITHER the pair is in "high margin" (margin covers at least [AppSettings.exceptionThresholdPct]%
     *    of its remaining sessions until the exam), which lifts every other restriction,
     *  - OR the session starts before [AppSettings.morningCutoffHour] AND is the only session of the day
     *    starting before that hour (a lone morning class, not chained to another one).
     * Afternoon/evening sessions, and morning sessions followed by another one, are therefore only
     * suggested through the high-margin exception. Candidates are then ranked by preference and margin,
     * and the best [AppSettings.maxPerDay] get the "suggested" badge.
     */
    fun today(
        sessions: List<Session>,
        att: Map<String, Status>,
        stats: Map<String, SubjectStats>,
        s: AppSettings,
        now: Long,
        day: LocalDate = LocalDate.now(ZoneId.systemDefault()),
    ): List<TodayItem> {
        val zone = ZoneId.systemDefault()
        val todays = sessions
            .filter { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == day }
            .sortedBy { it.start }
        return forDay(todays, att, stats, s, now)
    }

    /** Same as [today], but [todays] is already the day's sessions sorted by start (no full scan). */
    fun forDay(
        todays: List<Session>,
        att: Map<String, Status>,
        stats: Map<String, SubjectStats>,
        s: AppSettings,
        now: Long,
    ): List<TodayItem> {
        val zone = ZoneId.systemDefault()

        fun isMorning(sess: Session) = Instant.ofEpochMilli(sess.start).atZone(zone).hour < s.morningCutoffHour
        val morningCount = todays.count { isMorning(it) }

        fun effectiveStats(c: Session): SubjectStats? {
            val base = stats[key(c)] ?: return null
            val curTarget = base.targetExam
            if (curTarget != null && curTarget.start != Long.MAX_VALUE && c.start >= curTarget.start) {
                val nextExam = base.associatedExams.firstOrNull {
                    it.start > c.start && it.start != Long.MAX_VALUE
                } ?: base.associatedExams.firstOrNull { it.start == Long.MAX_VALUE }
                return base.forExam(nextExam)
            }
            return base
        }

        fun highMargin(st: SubjectStats?): Boolean {
            if (st == null || st.upcoming <= 0) return false
            val needed = ceil(st.upcoming * s.exceptionThresholdPct / 100.0).toInt()
            return st.margin >= needed
        }

        fun eligible(c: Session): Boolean {
            val st = effectiveStats(c)
            if (st == null || st.pref == 0 || st.margin < 1) return false
            if (highMargin(st)) return true
            return isMorning(c) && morningCount == 1
        }

        val candidates = todays.filter { it.end > now && att[it.uid] == null }
        val ranked = candidates.filter(::eligible).sortedWith(
            compareByDescending<Session> { effectiveStats(it)?.pref ?: 1 }
                .thenByDescending { effectiveStats(it)?.margin ?: 0 }
                .thenBy { it.start }
        )

        val remaining = HashMap<String, Int>()
        val badges = HashMap<String, Badge>()
        var picked = 0
        for (c in ranked) {
            val eff = effectiveStats(c)
            val k = "${key(c)}@${eff?.targetExam?.indexedLabel.orEmpty()}"
            val left = remaining.getOrPut(k) { (eff?.margin ?: 0) - s.reserve }
            if (left >= 1) {
                remaining[k] = left - 1
                badges[c.uid] = if (picked < s.maxPerDay) { picked++; Badge.SKIP_SUGGESTED } else Badge.CAN_SKIP
            }
        }
        // Everything else: flag only the important ones and the ones with no margin left at all.
        for (c in candidates) {
            if (badges.containsKey(c.uid)) continue
            val st = effectiveStats(c) ?: continue // Skip excluded subjects
            when {
                st.margin <= 0 -> badges[c.uid] = Badge.MUST_ATTEND
                st.pref == 0 -> badges[c.uid] = Badge.IMPORTANT
            }
        }
        return todays.map { TodayItem(it, att[it.uid], badges[it.uid]) }
    }
}
