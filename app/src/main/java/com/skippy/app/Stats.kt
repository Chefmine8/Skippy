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
) {
    val key: String get() = Stats.key(subject, typeId)
    val title: String get() = "$subject · $typeLabel"
    val answered: Int get() = present + absent
    val rate: Int? get() = if (answered == 0) null else (present * 100f / answered).roundToInt()
}

enum class Badge { SKIP_SUGGESTED, CAN_SKIP, ATTEND, IMPORTANT }

data class TodayItem(val session: Session, val status: Status?, val badge: Badge?)

object Stats {
    fun key(subject: String, typeId: Int) = "$subject#$typeId"
    fun key(s: Session) = key(s.subject, s.typeId)

    /** Earliest "Examen" session start time per exam title. */
    fun examCutoffs(sessions: List<Session>, typeNames: Map<Int, String> = emptyMap()): Map<String, Long> =
        sessions.filter { ExamMatch.isExamSession(it, typeNames) }
            .groupBy { it.subject }
            .mapValues { (_, list) -> list.minOf { it.start } }

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
        examMappings: Map<String, String>,
        availableExams: List<String>,
        now: Long,
        pct: Int,
        typeNames: Map<Int, String> = emptyMap(),
        typeLabel: (Int) -> String,
    ): List<SubjectStats> {
        val examStartTimes = examCutoffs(sessions, typeNames)
        return sessions.groupBy { key(it) }.values.map { list ->
            val first = list.first()
            val (resolvedExamTitle, isManual) = ExamMatch.resolveExam(first.subject, examMappings, availableExams)
            val examCutoff = resolvedExamTitle?.let { examStartTimes[it] }
            val examFound = examCutoff != null || (isManual && resolvedExamTitle == null)
            val withinTerm = if (examCutoff != null) list.filter { it.start < examCutoff } else list

            var p = 0
            var a = 0
            var pend = 0
            var up = 0
            for (s in withinTerm) {
                if (s.end <= now) {
                    when (att[s.uid]) {
                        null -> pend++
                        Status.ABSENT -> a++
                        else -> p++
                    }
                } else up++
            }
            val total = withinTerm.size
            val allowed = total * (100 - pct) / 100
            SubjectStats(
                first.subject, first.typeId, typeLabel(first.typeId),
                p, a, pend, up, total, allowed, allowed - a, examFound, prefs[first.subject] ?: 1,
                mappedExamTitle = resolvedExamTitle,
                isExamManual = isManual,
            )
        }.sortedWith(compareBy({ it.subject.lowercase() }, { it.typeId }))
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

        fun highMargin(st: SubjectStats?): Boolean {
            if (st == null || st.upcoming <= 0) return false
            val needed = ceil(st.upcoming * s.exceptionThresholdPct / 100.0).toInt()
            return st.margin >= needed
        }

        fun eligible(c: Session): Boolean {
            val st = stats[key(c)]
            if (st == null || st.pref == 0 || st.margin < 1) return false
            if (highMargin(st)) return true
            return isMorning(c) && morningCount == 1
        }

        val candidates = todays.filter { it.end > now && att[it.uid] == null }
        val ranked = candidates.filter(::eligible).sortedWith(
            compareByDescending<Session> { stats[key(it)]?.pref ?: 1 }
                .thenByDescending { stats[key(it)]?.margin ?: 0 }
                .thenBy { it.start }
        )

        val remaining = HashMap<String, Int>()
        val badges = HashMap<String, Badge>()
        var picked = 0
        for (c in ranked) {
            val k = key(c)
            val left = remaining.getOrPut(k) { (stats[k]?.margin ?: 0) - s.reserve }
            if (left >= 1) {
                remaining[k] = left - 1
                badges[c.uid] = if (picked < s.maxPerDay) { picked++; Badge.SKIP_SUGGESTED } else Badge.CAN_SKIP
            }
        }
        // Everything else: flag only the important ones and the ones with no margin left at all.
        for (c in candidates) {
            if (badges.containsKey(c.uid)) continue
            val st = stats[key(c)] ?: continue // Skip excluded subjects
            when {
                st.pref == 0 -> badges[c.uid] = Badge.IMPORTANT
                st.margin <= 0 -> badges[c.uid] = Badge.ATTEND
            }
        }
        return todays.map { TodayItem(it, att[it.uid], badges[it.uid]) }
    }
}
