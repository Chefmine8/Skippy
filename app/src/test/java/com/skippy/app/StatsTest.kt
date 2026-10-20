package com.skippy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    @Test
    fun testTotalSubjectStatsAggregation() {
        // Subject with CM and TD sessions
        val sessions = listOf(
            // CM sessions (typeId = 3)
            Session("cm1", "Maths", 3, 100L, 200L, "", false, ""),
            Session("cm2", "Maths", 3, 200L, 300L, "", false, ""),
            Session("cm3", "Maths", 3, 300L, 400L, "", false, ""),
            Session("cm4", "Maths", 3, 400L, 500L, "", false, ""),
            Session("cm5", "Maths", 3, 500L, 600L, "", false, ""),
            // TD sessions (typeId = 10)
            Session("td1", "Maths", 10, 150L, 250L, "", false, ""),
            Session("td2", "Maths", 10, 250L, 350L, "", false, ""),
            Session("td3", "Maths", 10, 350L, 450L, "", false, ""),
            Session("td4", "Maths", 10, 450L, 550L, "", false, ""),
            Session("td5", "Maths", 10, 550L, 650L, "", false, ""),
            // Exam session
            Session("e1", "EXAM MATHS", EXAM_TYPE_ID, 800L, 900L, "", false, ""),
        )

        val att = mapOf(
            "cm1" to Status.PRESENT,
            "cm2" to Status.ABSENT,
            "td1" to Status.PRESENT,
            "td2" to Status.PRESENT,
        )

        val stats = Stats.compute(
            sessions = sessions,
            att = att,
            prefs = emptyMap(),
            examMappings = emptyMap<String, List<String>>(),
            availableExams = listOf("EXAM MATHS"),
            now = 350L,
            pct = 60,
            typeLabel = { if (it == 3) "CM" else "TD" },
        )

        assertEquals(2, stats.size)
        val cmRow = stats.first { it.typeId == 3 }
        val tdRow = stats.first { it.typeId == 10 }

        // CM: 5 total, allowed = 5 * 40 / 100 = 2, 1 absent -> margin = 1
        assertEquals(5, cmRow.total)
        assertEquals(2, cmRow.allowed)
        assertEquals(1, cmRow.absent)
        assertEquals(1, cmRow.margin)

        // TD: 5 total, allowed = 2, 0 absent -> margin = 2
        assertEquals(5, tdRow.total)
        assertEquals(2, tdRow.allowed)
        assertEquals(0, tdRow.absent)
        assertEquals(2, tdRow.margin)

        // Compute total stats across the whole subject (CM + TD)
        val totalSt = Stats.totalStats(stats, 60)
        assertEquals("Maths", totalSt.subject)
        assertEquals(10, totalSt.total)
        // allowed = 10 * 40 / 100 = 4
        assertEquals(4, totalSt.allowed)
        // present = cm1 (1) + td1, td2 (2) = 3
        assertEquals(3, totalSt.present)
        // absent = cm2 (1) = 1
        assertEquals(1, totalSt.absent)
        // margin = 4 - 1 = 3
        assertEquals(3, totalSt.margin)
        // rate = 3 / (3 + 1) = 75%
        assertEquals(75, totalSt.rate)
    }

    @Test
    fun testMergeSubjectsLinkedToSameExam() {
        val sessions = listOf(
            Session("a1", "Algorithmique", 3, 100L, 200L, "", false, ""),
            Session("a2", "Algorithmique", 3, 200L, 300L, "", false, ""),
            Session("s1", "Structures de données", 3, 150L, 250L, "", false, ""),
            Session("s2", "Structures de données", 3, 250L, 350L, "", false, ""),
            Session("e1", "EXAM INFO", EXAM_TYPE_ID, 600L, 700L, "", false, ""),
        )

        // Map both subjects to the same exam "EXAM INFO"
        val mappings = mapOf(
            "Algorithmique" to listOf("EXAM INFO"),
            "Structures de données" to listOf("EXAM INFO"),
        )

        // Case 1: Default behavior (mergeSubjectsByExam = false) -> subjects remain separate!
        val statsUnmerged = Stats.compute(
            sessions = sessions,
            att = emptyMap(),
            prefs = emptyMap(),
            examMappings = mappings,
            availableExams = listOf("EXAM INFO"),
            now = 50L,
            pct = 60,
            mergeSubjectsByExam = false,
            typeLabel = { "CM" },
        )
        assertEquals(2, statsUnmerged.size)
        assertEquals("Algorithmique", statsUnmerged[0].subject)
        assertEquals("Structures de données", statsUnmerged[1].subject)

        // Case 2: Opt-in exam merge (mergeSubjectsByExam = true) -> merged into one single entry
        val statsMerged = Stats.compute(
            sessions = sessions,
            att = emptyMap(),
            prefs = emptyMap(),
            examMappings = mappings,
            availableExams = listOf("EXAM INFO"),
            now = 50L,
            pct = 60,
            mergeSubjectsByExam = true,
            typeLabel = { "CM" },
        )
        assertEquals(1, statsMerged.size)
        val merged = statsMerged.first()
        assertEquals("INFO", merged.subject)
        assertEquals(4, merged.total) // 2 + 2 sessions merged
        assertEquals(1, merged.allowed) // floor(4 * 0.4) = 1

        // Case 3: Manual subject merge mapping
        val statsManual = Stats.compute(
            sessions = sessions,
            att = emptyMap(),
            prefs = emptyMap(),
            examMappings = emptyMap(),
            availableExams = listOf("EXAM INFO"),
            now = 50L,
            pct = 60,
            mergeSubjectsByExam = false,
            manualMerges = mapOf("Structures de données" to "Algorithmique"),
            typeLabel = { "CM" },
        )
        assertEquals(1, statsManual.size)
        assertEquals("Algorithmique", statsManual.first().subject)
        assertEquals(4, statsManual.first().total)
        assertEquals(listOf("Algorithmique", "Structures de données"), statsManual.first().originalSubjects)

        // Case 4: Transitive manual merges: A -> B and B -> C
        val transitiveSessions = listOf(
            Session("a1", "A", 3, 100L, 200L, "", false, ""),
            Session("b1", "B", 3, 150L, 250L, "", false, ""),
            Session("c1", "C", 3, 200L, 300L, "", false, ""),
        )
        val statsTransitive = Stats.compute(
            sessions = transitiveSessions,
            att = emptyMap(),
            prefs = emptyMap(),
            examMappings = emptyMap(),
            availableExams = emptyList(),
            now = 50L,
            pct = 60,
            manualMerges = mapOf("A" to "B", "B" to "C"),
            typeLabel = { "CM" },
        )
        assertEquals(1, statsTransitive.size)
        assertEquals("C", statsTransitive.first().subject)
        assertEquals(3, statsTransitive.first().total)
        assertEquals(listOf("A", "B", "C"), statsTransitive.first().originalSubjects)
    }

    @Test
    fun testMultiExamIndexingAndTargetingNextExamWithoutReset() {
        val sessions = listOf(
            // Period 1 sessions before Exam 1 (t = 500)
            Session("m1", "Maths", 3, 100L, 200L, "", false, ""),
            Session("m2", "Maths", 3, 200L, 300L, "", false, ""),
            Session("m3", "Maths", 3, 300L, 400L, "", false, ""),
            Session("m4", "Maths", 3, 400L, 500L, "", false, ""),
            Session("e1", "EXAM MATHS 1", EXAM_TYPE_ID, 500L, 600L, "", false, ""),

            // Period 2 sessions between Exam 1 and Exam 2 (t = 1000)
            Session("m5", "Maths", 3, 600L, 700L, "", false, ""),
            Session("m6", "Maths", 3, 700L, 800L, "", false, ""),
            Session("m7", "Maths", 3, 800L, 900L, "", false, ""),
            Session("m8", "Maths", 3, 900L, 1000L, "", false, ""),
            Session("e2", "EXAM MATHS 2", EXAM_TYPE_ID, 1000L, 1100L, "", false, ""),

            // Post Exam 2 sessions
            Session("m9", "Maths", 3, 1200L, 1300L, "", false, ""),
        )

        val att = mapOf(
            "m1" to Status.ABSENT,   // Absent in period 1
            "m5" to Status.ABSENT,   // Absent in period 2
        )

        val exams = listOf("EXAM MATHS 1", "EXAM MATHS 2")

        // CASE 1: Before Exam 1 (now = 250L)
        val statsBeforeE1 = Stats.compute(
            sessions = sessions,
            att = att,
            prefs = emptyMap(),
            examMappings = emptyMap(), // auto-matches both exams
            availableExams = exams,
            now = 250L,
            pct = 60,
            typeLabel = { "CM" },
        )

        assertEquals(1, statsBeforeE1.size)
        val s1 = statsBeforeE1.first()

        // 2 exams associated and indexed as "Maths 1" and "Maths 2"
        assertEquals(2, s1.associatedExams.size)
        assertEquals("Maths 1", s1.associatedExams[0].indexedLabel)
        assertEquals("Maths 2", s1.associatedExams[1].indexedLabel)

        // Target exam is Maths 1
        assertNotNull(s1.targetExam)
        assertEquals("Maths 1", s1.targetExam?.indexedLabel)
        assertTrue(s1.associatedExams[0].isNext)
        assertFalse(s1.associatedExams[1].isNext)
        assertFalse(s1.allExamsPassed)

        // Total sessions up to Exam 1 = 4 sessions (m1..m4)
        assertEquals(4, s1.total)
        // Allowed absences = floor(4 * 0.4) = 1
        assertEquals(1, s1.allowed)
        // 1 absent so far (m1) -> margin = 0
        assertEquals(1, s1.absent)
        assertEquals(0, s1.margin)

        // CASE 2: After Exam 1 has passed, but before Exam 2 (now = 750L)
        val statsBeforeE2 = Stats.compute(
            sessions = sessions,
            att = att,
            prefs = emptyMap(),
            examMappings = emptyMap(),
            availableExams = exams,
            now = 750L,
            pct = 60,
            typeLabel = { "CM" },
        )

        val s2 = statsBeforeE2.first()
        // Target exam is now Maths 2
        assertNotNull(s2.targetExam)
        assertEquals("Maths 2", s2.targetExam?.indexedLabel)
        assertFalse(s2.associatedExams[0].isNext)
        assertTrue(s2.associatedExams[1].isNext)
        assertFalse(s2.allExamsPassed)

        // Total sessions up to Exam 2 = 8 sessions (m1..m4 + m5..m8)
        assertEquals(8, s2.total)
        // Allowed absences up to Exam 2 = floor(8 * 0.4) = 3
        assertEquals(3, s2.allowed)
        // Counter does NOT reset: both m1 (period 1) and m5 (period 2) are counted!
        assertEquals(2, s2.absent)
        // Margin = 3 - 2 = 1
        assertEquals(1, s2.margin)

        // CASE 3: All exams have passed (now = 1400L)
        val statsAfterAllExams = Stats.compute(
            sessions = sessions,
            att = att,
            prefs = emptyMap(),
            examMappings = emptyMap(),
            availableExams = exams,
            now = 1400L,
            pct = 60,
            typeLabel = { "CM" },
        )

        val s3 = statsAfterAllExams.first()
        assertNull(s3.targetExam)
        assertTrue(s3.allExamsPassed)
        // All sessions of the semester included (9 sessions)
        assertEquals(9, s3.total)
        assertEquals(3, s3.allowed) // floor(9 * 0.4) = 3
    }

    @Test
    fun testMultiExamSameTitleAndPerExamPeriodStatsAndForDay() {
        val sessions = listOf(
            // Period 1 sessions before Exam 1 (t = 500)
            Session("m1", "Maths", 3, 100L, 200L, "", false, ""),
            Session("m2", "Maths", 3, 200L, 300L, "", false, ""),
            Session("m3", "Maths", 3, 300L, 400L, "", false, ""),
            Session("m4", "Maths", 3, 400L, 500L, "", false, ""),
            // Exam 1 has two contiguous slots with the exact same title "Maths"
            Session("e1a", "Maths", EXAM_TYPE_ID, 500L, 550L, "", false, ""),
            Session("e1b", "Maths", EXAM_TYPE_ID, 550L, 600L, "", false, ""),

            // Period 2 sessions between Exam 1 (500) and Exam 2 (1000)
            Session("m5", "Maths", 3, 600L, 700L, "", false, ""),
            Session("m6", "Maths", 3, 700L, 800L, "", false, ""),
            Session("m7", "Maths", 3, 800L, 900L, "", false, ""),
            Session("m8", "Maths", 3, 900L, 1000L, "", false, ""),
            // Exam 2 has the exact same title "Maths" at a later date (t = 1000)
            Session("e2", "Maths", EXAM_TYPE_ID, 1000L, 1100L, "", false, ""),
        )

        val att = mapOf(
            "m1" to Status.ABSENT, // 1 absence in Period 1
        )

        val encodedExams = listOf("Maths@500", "Maths@1000")

        // Before Exam 1 (now = 250L, after m1 ends at 200L and before m2 ends at 300L)
        val statsBeforeE1 = Stats.compute(
            sessions = sessions,
            att = att,
            prefs = emptyMap(),
            examMappings = emptyMap(),
            availableExams = encodedExams,
            now = 250L,
            pct = 60,
            typeLabel = { "CM" },
        )

        assertEquals(1, statsBeforeE1.size)
        val s1 = statsBeforeE1.first()

        // Both exams with the same title are detected and indexed
        assertEquals(2, s1.associatedExams.size)
        val exam1 = s1.associatedExams[0]
        val exam2 = s1.associatedExams[1]
        assertEquals("Maths 1", exam1.indexedLabel)
        assertEquals(500L, exam1.start)
        assertEquals("Maths 2", exam2.indexedLabel)
        assertEquals(1000L, exam2.start)

        // targetExam equals exam1 (including isNext == true)
        assertEquals(exam1, s1.targetExam)

        // Per-exam period stats are precomputed for both Exam 1 and Exam 2
        val s1ForExam1 = s1.forExam(exam1)
        assertEquals(4, s1ForExam1.total)
        assertEquals(1, s1ForExam1.allowed)
        assertEquals(1, s1ForExam1.absent)
        assertEquals(0, s1ForExam1.margin)

        val s1ForExam2 = s1.forExam(exam2)
        assertEquals(8, s1ForExam2.total)
        assertEquals(3, s1ForExam2.allowed)
        assertEquals(1, s1ForExam2.absent)
        assertEquals(2, s1ForExam2.margin)

        val totalForExam2 = Stats.totalStats(statsBeforeE1.map { it.forExam(exam2) }, 60)
        assertEquals(8, totalForExam2.total)
        assertEquals(3, totalForExam2.allowed)
        assertEquals(2, totalForExam2.margin)

        // Check Stats.forDay:
        val statsMap = statsBeforeE1.associateBy { it.key }
        val settings = AppSettings(reserve = 0, morningCutoffHour = 24)
        // In Period 1 (day with m2 at t=200..300L), margin for Exam 1 is 0 -> MUST_ATTEND
        val day1Plan = Stats.forDay(
            todays = listOf(sessions[1]), // m2 at 200..300L
            att = att,
            stats = statsMap,
            s = settings,
            now = 250L,
        )
        assertEquals(Badge.MUST_ATTEND, day1Plan.first().badge)

        // In Period 2 (day with m6 at t=700..800L, after Exam 1 at 500L), Stats.forDay uses Exam 2's budget (margin = 2 -> SKIP_SUGGESTED!)
        val day2Plan = Stats.forDay(
            todays = listOf(sessions[7]), // m6 at 700..800L
            att = att,
            stats = statsMap,
            s = settings,
            now = 250L,
        )
        assertEquals(Badge.SKIP_SUGGESTED, day2Plan.first().badge)

        // After Exam 1 passes (now = 650L), targetExam transitions to Maths 2 automatically
        val statsAfterE1 = Stats.compute(
            sessions = sessions,
            att = att,
            prefs = emptyMap(),
            examMappings = emptyMap(),
            availableExams = encodedExams,
            now = 650L,
            pct = 60,
            typeLabel = { "CM" },
        )
        val sAfterE1 = statsAfterE1.first()
        assertEquals("Maths 2", sAfterE1.targetExam?.indexedLabel)
        assertEquals(sAfterE1.associatedExams[1], sAfterE1.targetExam)
        assertEquals(8, sAfterE1.total)
        assertEquals(3, sAfterE1.allowed)
        assertEquals(1, sAfterE1.absent)
        assertEquals(2, sAfterE1.margin)
    }
}
