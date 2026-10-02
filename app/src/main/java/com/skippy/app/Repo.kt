package com.skippy.app

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/** Local storage (SharedPreferences) + synchronisation with Zeus. */
@SuppressLint("StaticFieldLeak")
class Repo private constructor(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val sp = appCtx.getSharedPreferences("skippy", Context.MODE_PRIVATE)

    companion object {
        private val DIACRITICS_REGEX = Regex("\\p{M}+")
        @Volatile private var inst: Repo? = null

        fun get(ctx: Context): Repo =
            inst ?: synchronized(this) { inst ?: Repo(ctx).also { inst = it } }

        /** Lowercase, no accents: "Férié" -> "ferie". */
        fun plain(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS_REGEX, "").lowercase().trim()

        /** 1 September of the current school year. */
        fun defaultRentree(): String {
            val t = LocalDate.now()
            return LocalDate.of(if (t.monthValue >= 8) t.year else t.year - 1, 9, 1).toString()
        }
    }

    // ---- groups ---------------------------------------------------------------------------

    fun cachedGroups(): List<ApiGroup> {
        val arr = JSONArray(sp.getString("apiGroups", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ApiGroup(
                id = o.getInt("id"),
                name = o.getString("name"),
                path = o.optString("path", null)
            )
        }
    }

    fun saveGroups(groups: List<ApiGroup>) {
        val arr = JSONArray()
        groups.forEach { g ->
            val o = JSONObject().put("id", g.id).put("name", g.name)
            if (g.path != null) o.put("path", g.path)
            arr.put(o)
        }
        sp.edit().putString("apiGroups", arr.toString()).apply()
    }

    // ---- settings -------------------------------------------------------------------------

    fun settings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            groupIds = sp.getStringSet("groupIds", null)?.mapNotNull { it.toIntOrNull() }?.toSet()?.takeIf { it.isNotEmpty() } 
                ?: setOf(sp.getInt("group", 0)).filter { it > 0 }.toSet().ifEmpty { d.groupIds },
            rentree = sp.getString("rentree", null) ?: defaultRentree(),
            requiredPct = sp.getInt("pct", d.requiredPct),
            reserve = sp.getInt("reserve", d.reserve),
            alertAt = sp.getInt("alertAt", d.alertAt),
            maxPerDay = sp.getInt("maxDay", d.maxPerDay),
            notifications = sp.getBoolean("notif", d.notifications),
            excluded = sp.getString("excluded", d.excluded) ?: d.excluded,
            lastSync = sp.getLong("lastSync", 0L),
            lastFull = sp.getLong("lastFull", 0L),
            authMode = sp.getString("auth", "") ?: "",
            morningCutoffHour = sp.getInt("morningCutoff", d.morningCutoffHour),
            exceptionThresholdPct = sp.getInt("exceptionPct", d.exceptionThresholdPct),
        )
    }

    /** Saves user-editable settings (not lastSync / authMode). */
    fun saveSettings(s: AppSettings) {
        val e = sp.edit()
            .putStringSet("groupIds", s.groupIds.map { it.toString() }.toSet())
            .putInt("group", s.groupIds.firstOrNull() ?: 0) // fallback for older clients if needed
            .putInt("pct", s.requiredPct)
            .putInt("reserve", s.reserve)
            .putInt("alertAt", s.alertAt)
            .putInt("maxDay", s.maxPerDay)
            .putBoolean("notif", s.notifications)
            .putString("excluded", s.excluded)
            .putInt("morningCutoff", s.morningCutoffHour)
            .putInt("exceptionPct", s.exceptionThresholdPct)
        if (runCatching { LocalDate.parse(s.rentree.trim()) }.isSuccess) e.putString("rentree", s.rentree.trim())
        e.apply()
    }

    fun setAuthMode(mode: String) {
        sp.edit().putString("auth", mode).apply()
    }

    // ---- activity type names --------------------------------------------------------------

    private var cachedTypeNames: Map<Int, String>? = null
    fun typeNames(): Map<Int, String> {
        cachedTypeNames?.let { return it }
        val o = JSONObject(sp.getString("types", "{}"))
        val map = HashMap<Int, String>()
        for (k in o.keys()) k.toIntOrNull()?.let { map[it] = o.getString(k) }
        return map.also { cachedTypeNames = it }
    }

    fun typeName(id: Int): String = typeName(typeNames(), id)

    // ---- exam boundaries --------------------------------------------------------------------

    private var cachedExamMappings: Map<String, String>? = null
    fun examMappings(): Map<String, String> {
        cachedExamMappings?.let { return it }
        val o = JSONObject(sp.getString("examMappings", "{}"))
        val map = HashMap<String, String>()
        for (k in o.keys()) map[k] = o.getString(k)
        return map.also { cachedExamMappings = it }
    }

    fun setExamMapping(subject: String, examSubject: String) {
        val o = JSONObject(sp.getString("examMappings", "{}"))
        if (examSubject == ExamMatch.AUTO_MAPPING) {
            o.remove(subject)
        } else {
            o.put(subject, examSubject)
        }
        sp.edit().putString("examMappings", o.toString()).apply()
        cachedExamMappings = null
    }

    fun availableExams(): List<String> {
        val types = typeNames()
        return rawSessions()
            .filter { ExamMatch.isExamSession(it, types) }
            .map { it.subject }
            .distinct()
            .sortedWith(compareBy { it.lowercase() })
    }

    /** Per subject (plain-normalized), the start of its last known Zeus "Examen" event. */
    fun examBySubject(): Map<String, Long> {
        val types = typeNames()
        return rawSessions()
            .filter { ExamMatch.isExamSession(it, types) }
            .groupBy { plain(it.subject) }
            .mapValues { (_, v) -> v.maxOf { it.start } }
    }

    fun setTypeName(id: Int, label: String) {
        val o = JSONObject(sp.getString("types", "{}"))
        o.put(id.toString(), label.trim())
        sp.edit().putString("types", o.toString()).apply()
        cachedTypeNames = null
    }

    // ---- excluded subjects (attendance tracking turned off) --------------------------------

    private var cachedExcludedSubjects: Set<String>? = null
    fun excludedSubjects(): Set<String> {
        cachedExcludedSubjects?.let { return it }
        val arr = JSONArray(sp.getString("excludedSubjects", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }.toSet().also { cachedExcludedSubjects = it }
    }

    fun setSubjectExcluded(subject: String, excluded: Boolean) {
        val current = excludedSubjects().toMutableSet()
        if (excluded) current.add(subject) else current.remove(subject)
        val arr = JSONArray()
        current.forEach { arr.put(it) }
        sp.edit().putString("excludedSubjects", arr.toString()).apply()
        cachedExcludedSubjects = null
    }

    // ---- hidden subjects (completely removed from timetable) --------------------------------

    private var cachedHiddenSubjects: Set<String>? = null
    fun hiddenSubjects(): Set<String> {
        cachedHiddenSubjects?.let { return it }
        val arr = JSONArray(sp.getString("hiddenSubjects", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }.toSet().also { cachedHiddenSubjects = it }
    }

    fun setSubjectHidden(subject: String, hidden: Boolean) {
        val current = hiddenSubjects().toMutableSet()
        if (hidden) current.add(subject) else current.remove(subject)
        val arr = JSONArray()
        current.forEach { arr.put(it) }
        sp.edit().putString("hiddenSubjects", arr.toString()).apply()
        cachedHiddenSubjects = null
    }

    // ---- sessions -------------------------------------------------------------------------

    private var cachedRawSessions: List<Session>? = null

    private fun rawSessions(): List<Session> {
        cachedRawSessions?.let { return it }
        val arr = JSONArray(sp.getString("sessions2", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Session(
                uid = o.getString("u"), subject = o.getString("s"), typeId = o.optInt("t", 0),
                start = o.getLong("a"), end = o.getLong("e"), location = o.optString("l", ""),
                online = o.optBoolean("o", false), teachers = o.optString("h", ""),
                groups = o.optString("g", ""),
            )
        }.also { cachedRawSessions = it }
    }

    fun allRawSessions(): List<Session> = rawSessions().filter { it.subject !in hiddenSubjects() }

    private fun saveRaw(list: List<Session>) {
        cachedRawSessions = list
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("u", it.uid).put("s", it.subject).put("t", it.typeId)
                    .put("a", it.start).put("e", it.end).put("l", it.location)
                    .put("o", it.online).put("h", it.teachers).put("g", it.groups)
            )
        }
        sp.edit().putString("sessions2", arr.toString()).apply()
    }

    /** Sessions after removing ignored events (Férié, vacances, EXAM… by default). */
    /** All subjects that would be tracked if nothing were excluded (used to manage the exclusion list). */
    fun allSubjects(): List<String> {
        val keywords = settings().excluded.split(",").map { plain(it) }.filter { it.isNotEmpty() }
        val types = typeNames()
        return rawSessions()
            .filter { !ExamMatch.isExamSession(it, types) }
            .filter { s -> val n = plain(s.subject); keywords.none { n.startsWith(it) } }
            .map { it.subject }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    fun sessions(): List<Session> {
        val keywords = settings().excluded.split(",").map { plain(it) }.filter { it.isNotEmpty() }
        val excludedSubjects = excludedSubjects()
        val hiddenSubjects = hiddenSubjects()
        return rawSessions()
            .filter { it.subject !in hiddenSubjects }
            .filter { s -> val n = plain(s.subject); keywords.none { n.startsWith(it) } }
            .filter { it.subject !in excludedSubjects }
            .sortedBy { it.start }
    }

    private fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Fetches the timetable one week at a time.
     *  - full = true : from the school-year start to ~30 weeks ahead (first sync, or on demand);
     *  - full = false: last week to ~9 weeks ahead (periodic refresh).
     * Each fetched week replaces what was stored for that week (cancelled / moved classes disappear);
     * weeks outside the window are left untouched. Nothing is written if any request fails.
     */
    suspend fun sync(full: Boolean, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val s = settings()
                require(s.groupIds.isNotEmpty()) { "No group" }
                val token = Auth.accessToken(appCtx) ?: throw ZeusAuthException("No token")

                val today = LocalDate.now(ZoneOffset.UTC)
                val from = if (full) {
                    monday(runCatching { LocalDate.parse(s.rentree) }.getOrElse { today.minusWeeks(8) })
                } else monday(today).minusWeeks(1)
                // 42 weeks covers a full year including September retakes, so the exam cutoff used by
                // Stats.examCutoffs() is far more likely to already be in the data.
                val to = monday(today).plusWeeks(if (full) 42L else 9L)
                val weeks = generateSequence(from) { it.plusWeeks(1) }.takeWhile { it < to }.toList()

                val fetched = ArrayList<Triple<Long, Long, List<Session>>>()
                for ((i, w) in weeks.withIndex()) {
                    val a = w.atStartOfDay(ZoneOffset.UTC).toInstant()
                    val b = w.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    fetched.add(Triple(a.toEpochMilli(), b.toEpochMilli(), ZeusApi.reservations(token, a, b, s.groupIds)))
                    onProgress(i + 1, weeks.size)
                }

                var all = rawSessions()
                for ((a, b, list) in fetched) {
                    all = all.filter { it.start !in a until b } + list
                }
                saveRaw(all.distinctBy { it.uid })
                val now = System.currentTimeMillis()
                val editor = sp.edit().putLong("lastSync", now)
                if (full) editor.putLong("lastFull", now)
                editor.apply()
                sessions().size
            }
        }

    // ---- attendance -----------------------------------------------------------------------

    private var cachedAttendance: Map<String, Status>? = null
    fun attendance(): Map<String, Status> {
        cachedAttendance?.let { return it }
        val o = JSONObject(sp.getString("att", "{}"))
        val map = HashMap<String, Status>()
        for (k in o.keys()) {
            map[k] = Status.from(o.getString(k)) ?: continue
        }
        return map.also { cachedAttendance = it }
    }

    fun setStatus(uid: String, status: Status?) {
        val o = JSONObject(sp.getString("att", "{}"))
        if (status == null) o.remove(uid) else o.put(uid, status.code)
        sp.edit().putString("att", o.toString()).apply()
        cachedAttendance = null
    }

    // ---- per-subject preference: 0 = important, 1 = normal, 2 = skip first ----------------

    private var cachedPrefs: Map<String, Int>? = null
    fun prefs(): Map<String, Int> {
        cachedPrefs?.let { return it }
        val o = JSONObject(sp.getString("prefs", "{}"))
        val map = HashMap<String, Int>()
        for (k in o.keys()) map[k] = o.getInt(k)
        return map.also { cachedPrefs = it }
    }

    fun setPref(subject: String, pref: Int) {
        val o = JSONObject(sp.getString("prefs", "{}"))
        o.put(subject, pref)
        sp.edit().putString("prefs", o.toString()).apply()
        cachedPrefs = null
    }

    // ---- notification bookkeeping ---------------------------------------------------------

    fun notified(): Set<String> {
        val arr = JSONArray(sp.getString("notified", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun markNotified(uid: String) {
        val arr = JSONArray(sp.getString("notified", "[]"))
        arr.put(uid)
        sp.edit().putString("notified", arr.toString()).apply()
    }

    fun notifiedUpcoming(): Set<String> {
        val arr = JSONArray(sp.getString("notifiedUpcoming", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun markNotifiedUpcoming(uid: String) {
        val arr = JSONArray(sp.getString("notifiedUpcoming", "[]"))
        arr.put(uid)
        sp.edit().putString("notifiedUpcoming", arr.toString()).apply()
    }
}
