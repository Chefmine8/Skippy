package com.skippy.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** PRESENT and JUSTIFIED both count as attended; ABSENT does not. */
enum class Status(val code: String) {
    PRESENT("P"), ABSENT("A"), JUSTIFIED("J");

    companion object {
        fun from(code: String?): Status? = entries.firstOrNull { it.code == code }
    }
}

/** One Zeus reservation (a class). [uid] is the Zeus idReservation. */
data class Session(
    val uid: String,
    val subject: String,
    val typeId: Int,
    val start: Long,
    val end: Long,
    val location: String,
    val online: Boolean,
    val teachers: String,
    val groups: String = "",
)

/** Result of GET api/reservation/{id}/details. */
data class ReservationDetails(
    val id: Long,
    val name: String,
    val typeId: Int,
    val start: Long,
    val end: Long,
    val online: Boolean,
    val url: String,
    val comment: String,
    val code: String,
    val durationMin: Int,
    val rooms: List<String>,
    val teachers: List<String>,
    val groups: List<String>,
)

data class ApiGroup(
    val id: Int,
    val name: String,
    val path: String? = null
)

data class AppSettings(
    val groupIds: Set<Int> = setOf(634),
    val rentree: String = "",
    val requiredPct: Int = 60,
    val reserve: Int = 0,
    val alertAt: Int = 1,
    val maxPerDay: Int = 2,
    val notifications: Boolean = true,
    val excluded: String = "férié, vacances",
    val lastSync: Long = 0L,
    val lastFull: Long = 0L,
    /** "" = signed out, "msal" = Microsoft sign-in, "manual" = pasted token. */
    val authMode: String = "",
    /** Sessions starting before this hour are "morning" for the smart-skip heuristic. */
    val morningCutoffHour: Int = 13,
    /** % of a subject's remaining sessions (before its exam) that must still be spare margin
     *  to override the morning/afternoon rules and suggest skipping almost anything. */
    val exceptionThresholdPct: Int = 80,
)

/** Zeus idType -> default label, from the school's own table. Editable per user in Settings. */
val DEFAULT_TYPE_NAMES: Map<Int, String> = mapOf(
    0 to "Non spécifié", 1 to "Suivi", 2 to "Examen", 3 to "CM", 4 to "TP",
    5 to "Conférence", 6 to "Réunion", 7 to "Soutenance", 8 to "Atelier",
    9 to "Rush", 10 to "TD", 11 to "Evénement associatif", 12 to "Travaux-Logistique",
    13 to "Remédiation", 15 to "Checkpoint",
)

/** idType of a Zeus "exam" event: marks the end of a subject's teaching period, not a class to attend. */
const val EXAM_TYPE_ID = 2

/** Zeus only exposes a numeric idType: pre-filled with the school's table, editable in Settings. */
fun typeName(names: Map<Int, String>, id: Int): String =
    names[id]?.takeIf { it.isNotBlank() } ?: DEFAULT_TYPE_NAMES[id] ?: "Type $id"

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dateTimeFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

fun fmtTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt)

fun fmtDate(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFmt)

fun fmtDateTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFmt)
