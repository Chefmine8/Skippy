package com.skippy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class CachedWeekData(
    val stats: List<SubjectStats>,
    val schedules: Map<LocalDate, List<TodayItem>>
)

object WeeklyCache {
    fun save(ctx: Context, stats: List<SubjectStats>, schedules: Map<LocalDate, List<TodayItem>>) {
        try {
            val obj = JSONObject()
            
            // Serialize stats
            val statsArr = JSONArray()
            for (st in stats) {
                val so = JSONObject()
                so.put("subject", st.subject)
                so.put("typeId", st.typeId)
                so.put("typeLabel", st.typeLabel)
                so.put("present", st.present)
                so.put("absent", st.absent)
                so.put("pending", st.pending)
                so.put("upcoming", st.upcoming)
                so.put("total", st.total)
                so.put("allowed", st.allowed)
                so.put("margin", st.margin)
                so.put("examFound", st.examFound)
                so.put("pref", st.pref)
                st.mappedExamTitle?.let { so.put("mappedExamTitle", it) }
                so.put("isExamManual", st.isExamManual)
                statsArr.put(so)
            }
            obj.put("stats", statsArr)

            // Serialize schedules
            val schedObj = JSONObject()
            for ((date, items) in schedules) {
                val itemsArr = JSONArray()
                for (item in items) {
                    val io = JSONObject()
                    val s = item.session
                    val so = JSONObject()
                    so.put("uid", s.uid).put("subject", s.subject).put("typeId", s.typeId)
                      .put("start", s.start).put("end", s.end).put("location", s.location)
                      .put("online", s.online).put("teachers", s.teachers).put("groups", s.groups)
                    io.put("session", so)
                    item.status?.let { io.put("status", it.code) }
                    item.badge?.let { io.put("badge", it.name) }
                    itemsArr.put(io)
                }
                schedObj.put(date.toString(), itemsArr)
            }
            obj.put("schedules", schedObj)

            File(ctx.filesDir, "weekly_cache.json").writeText(obj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load(ctx: Context): CachedWeekData? {
        try {
            val file = File(ctx.filesDir, "weekly_cache.json")
            if (!file.exists()) return null
            val obj = JSONObject(file.readText())

            val stats = mutableListOf<SubjectStats>()
            val statsArr = obj.getJSONArray("stats")
            for (i in 0 until statsArr.length()) {
                val so = statsArr.getJSONObject(i)
                stats.add(SubjectStats(
                    subject = so.getString("subject"),
                    typeId = so.getInt("typeId"),
                    typeLabel = so.getString("typeLabel"),
                    present = so.getInt("present"),
                    absent = so.getInt("absent"),
                    pending = so.getInt("pending"),
                    upcoming = so.getInt("upcoming"),
                    total = so.getInt("total"),
                    allowed = so.getInt("allowed"),
                    margin = so.getInt("margin"),
                    examFound = so.getBoolean("examFound"),
                    pref = so.getInt("pref"),
                    mappedExamTitle = if (so.has("mappedExamTitle")) so.getString("mappedExamTitle") else null,
                    isExamManual = so.getBoolean("isExamManual")
                ))
            }

            val schedules = mutableMapOf<LocalDate, List<TodayItem>>()
            val schedObj = obj.getJSONObject("schedules")
            for (key in schedObj.keys()) {
                val date = LocalDate.parse(key)
                val itemsArr = schedObj.getJSONArray(key)
                val items = mutableListOf<TodayItem>()
                for (i in 0 until itemsArr.length()) {
                    val io = itemsArr.getJSONObject(i)
                    val so = io.getJSONObject("session")
                    val session = Session(
                        uid = so.optString("uid"), subject = so.optString("subject"), typeId = so.optInt("typeId"),
                        start = so.optLong("start"), end = so.optLong("end"), location = so.optString("location", ""),
                        online = so.optBoolean("online"), teachers = so.optString("teachers", ""), groups = so.optString("groups", "")
                    )
                    val status = if (io.has("status")) Status.from(io.getString("status")) else null
                    val badge = if (io.has("badge")) Badge.valueOf(io.getString("badge")) else null
                    items.add(TodayItem(session, status, badge))
                }
                schedules[date] = items
            }
            return CachedWeekData(stats, schedules)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
