package com.skippy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamMatchTest {

    @Test
    fun testIsExamSession() {
        val typeNames = mapOf(15 to "Examen blanc", 3 to "CM")

        // 1. By typeId == EXAM_TYPE_ID (2)
        val s1 = Session("1", "Maths", EXAM_TYPE_ID, 0L, 0L, "", false, "")
        assertTrue(ExamMatch.isExamSession(s1, typeNames))

        // 2. By custom type name in settings ("Examen blanc")
        val s2 = Session("2", "Maths", 15, 0L, 0L, "", false, "")
        assertTrue(ExamMatch.isExamSession(s2, typeNames))

        // 3. By subject title parsing ("EXAM ...")
        val s3 = Session("3", "EXAM DRG1", 3, 0L, 0L, "", false, "")
        assertTrue(ExamMatch.isExamSession(s3, typeNames))

        // 4. Regular class
        val s4 = Session("4", "Maths", 3, 0L, 0L, "", false, "")
        assertFalse(ExamMatch.isExamSession(s4, typeNames))
    }

    @Test
    fun testAutoMatch() {
        val exams = listOf("EXAM DRG1", "EXAM IGAF", "EXAM PBS1")
        assertEquals("EXAM IGAF", ExamMatch.findAutoMatch("IGAF", exams))
        assertEquals("EXAM PBS1", ExamMatch.findAutoMatch("PBS", exams))
        // "Droit Général" won't match "EXAM DRG1" automatically because acronyms differ
        assertEquals(null, ExamMatch.findAutoMatch("Droit Général", exams))
    }

    @Test
    fun testResolveExamAutomaticAndManual() {
        val exams = listOf("EXAM DRG1", "EXAM IGAF")

        // 1. Unmapped: fallback to auto
        val autoRes = ExamMatch.resolveExam("IGAF", emptyMap(), exams)
        assertEquals("EXAM IGAF", autoRes.first)
        assertFalse(autoRes.second) // isManual = false

        // 2. Manually mapped: "Droit Général" -> "EXAM DRG1"
        val manualMap = mapOf("Droit Général" to "EXAM DRG1")
        val manualRes = ExamMatch.resolveExam("Droit Général", manualMap, exams)
        assertEquals("EXAM DRG1", manualRes.first)
        assertTrue(manualRes.second) // isManual = true

        // 3. Manually set to NONE
        val noneMap = mapOf("IGAF" to ExamMatch.NONE_MAPPING)
        val noneRes = ExamMatch.resolveExam("IGAF", noneMap, exams)
        assertEquals(null, noneRes.first)
        assertTrue(noneRes.second) // isManual = true
    }

    @Test
    fun testStatsCutoffWithManualMapping() {
        val now = 1000L
        val sessions = listOf(
            Session("1", "Droit Général", 3, 100L, 200L, "", false, ""),
            Session("2", "Droit Général", 3, 300L, 400L, "", false, ""),
            Session("3", "Droit Général", 3, 700L, 800L, "", false, ""),
            Session("e1", "EXAM DRG1", EXAM_TYPE_ID, 500L, 600L, "", false, ""),
        )

        // Without manual mapping: cutoffs cannot match "Droit Général" to "EXAM DRG1" automatically
        val statsUnmapped = Stats.compute(
            sessions = sessions,
            att = emptyMap(),
            prefs = emptyMap(),
            examMappings = emptyMap(),
            availableExams = listOf("EXAM DRG1"),
            now = now,
            pct = 60,
            typeLabel = { "CM" },
        )
        val dgUnmapped = statsUnmapped.first { it.subject == "Droit Général" }
        assertFalse(dgUnmapped.examFound)
        assertEquals(3, dgUnmapped.total) // all 3 sessions included

        // With manual mapping: "Droit Général" -> "EXAM DRG1"
        val statsMapped = Stats.compute(
            sessions = sessions,
            att = emptyMap(),
            prefs = emptyMap(),
            examMappings = mapOf("Droit Général" to "EXAM DRG1"),
            availableExams = listOf("EXAM DRG1"),
            now = now,
            pct = 60,
            typeLabel = { "CM" },
        )
        val dgMapped = statsMapped.first { it.subject == "Droit Général" }
        assertTrue(dgMapped.examFound)
        assertEquals(2, dgMapped.total) // only sessions before start=500L (i.e. sessions 1 and 2) included
        assertEquals("EXAM DRG1", dgMapped.mappedExamTitle)
        assertTrue(dgMapped.isExamManual)

        // With manual mapping set to NONE: end date is last class (all 3 sessions counted), examFound is true because manually declared no exam
        val statsNoneMapped = Stats.compute(
            sessions = sessions,
            att = emptyMap(),
            prefs = emptyMap(),
            examMappings = mapOf("Droit Général" to ExamMatch.NONE_MAPPING),
            availableExams = listOf("EXAM DRG1"),
            now = now,
            pct = 60,
            typeLabel = { "CM" },
        )
        val dgNone = statsNoneMapped.first { it.subject == "Droit Général" }
        assertTrue(dgNone.examFound)
        assertEquals(3, dgNone.total)
        assertEquals(null, dgNone.mappedExamTitle)
        assertTrue(dgNone.isExamManual)
    }
}
