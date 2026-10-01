package com.skippy.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 401 / 403: the Microsoft token is missing, expired or refused. */
class ZeusAuthException(message: String) : Exception(message)

class ZeusHttpException(val code: Int) : Exception("HTTP $code")

/**
 * Client for the internal Zeus API. Plain HttpURLConnection + org.json (no third-party network stack).
 * The bearer token is only ever put in the Authorization header: it is never logged or stored here.
 */
object ZeusApi {
    private const val BASE = "https://zeus.ionis-it.com/"
    private val requestDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    /** POST api/reservation/filter/displayable : reservations of one group between [from] and [to]. */
    suspend fun reservations(token: String, from: Instant, to: Instant, groupId: Int): List<Session> =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("startDate", requestDate.format(from))
                .put("endDate", requestDate.format(to))
                .put("groups", JSONArray().put(groupId))
                .put("rooms", JSONArray())
                .put("teachers", JSONArray())
            parseList(call("POST", "api/reservation/filter/displayable", token, body.toString()))
        }

    /** GET api/reservation/{id}/details. */
    suspend fun details(token: String, id: Long): ReservationDetails =
        withContext(Dispatchers.IO) {
            parseDetails(JSONObject(call("GET", "api/reservation/$id/details", token, null)))
        }

    // ---- HTTP ------------------------------------------------------------------------------

    private fun call(method: String, path: String, token: String, body: String?): String {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Skippy/2.0")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == 401 || code == 403) throw ZeusAuthException("HTTP $code")
            if (code !in 200..299) throw ZeusHttpException(code)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---- JSON -> models --------------------------------------------------------------------

    /** The list is expected as a top-level array; a wrapper object holding an array is tolerated. */
    internal fun parseList(text: String): List<Session> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        val arr: JSONArray = if (t.startsWith("[")) {
            JSONArray(t)
        } else {
            val o = JSONObject(t)
            o.keys().asSequence().map { o.opt(it) }.filterIsInstance<JSONArray>().firstOrNull() ?: JSONArray()
        }
        return (0 until arr.length()).mapNotNull { toSession(arr.optJSONObject(it)) }
    }

    private fun toSession(o: JSONObject?): Session? {
        if (o == null) return null
        val id = o.optLong("idReservation", -1L)
        if (id < 0) return null
        val start = parseInstant(o.optString("startDate")) ?: return null
        val end = parseInstant(o.optString("endDate")) ?: start
        return Session(
            uid = id.toString(),
            subject = o.optString("name").replace(Regex("\\s+"), " ").trim(),
            typeId = o.optInt("idType", 0),
            start = start,
            end = end,
            location = names(o.optJSONArray("rooms")).joinToString(", "),
            online = o.optBoolean("isOnline", false),
            teachers = names(o.optJSONArray("teachers")).joinToString(", "),
        )
    }

    private fun parseDetails(o: JSONObject): ReservationDetails {
        val start = parseInstant(o.optString("startDate")) ?: 0L
        val end = parseInstant(o.optString("endDate")) ?: start
        return ReservationDetails(
            id = o.optLong("idReservation", -1L),
            name = o.optString("name"),
            typeId = o.optInt("idType", 0),
            start = start,
            end = end,
            online = o.optBoolean("isOnline", false),
            url = o.optString("url"),
            comment = o.optString("comment"),
            code = o.optString("code"),
            durationMin = o.optInt("duration", 0),
            rooms = names(o.optJSONArray("rooms")),
            teachers = names(o.optJSONArray("teachers")),
            groups = names(o.optJSONArray("groups")),
        )
    }

    /** Names from [{"id":1,"name":"X"}] (list endpoint) or [{"room":{"id":1,"name":"X"}}] (details endpoint). */
    private fun names(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val e = a.optJSONObject(i) ?: return@mapNotNull null
            val n = e.optJSONObject("room") ?: e
            n.optString("name").takeIf { it.isNotBlank() }
        }
    }

    private fun parseInstant(s: String): Long? {
        if (s.isBlank()) return null
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }
}
