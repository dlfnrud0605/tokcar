package com.example.tokcar.remote

import com.example.tokcar.navi.Congestion
import com.example.tokcar.navi.TrafficSegment
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/*
 * 가족 서버(server)와 주고받는 JSON 모양. 칸 이름은 서버 API 명세(docs/api.html)와 같다.
 * 시각은 서버가 ISO-8601 UTC 로 주고, 앱 안에서는 ms 로 다룬다.
 */

data class SUser(val id: Long, val name: String)

data class SFriend(val id: Long, val name: String, val status: String)

data class SPlace(val name: String, val addr: String, val lat: Double, val lon: Double)

/** 경로 공유의 실시간 값(서버 메모리). traffic 이 null 이면 정보 없음. */
data class SLive(
    val carLat: Double,
    val carLon: Double,
    val remainSec: Int,
    val pct: Int,
    val etaAtMs: Long,
    val traffic: List<TrafficSegment>?,
)

data class SRoute(val state: String, val from: SPlace, val dest: SPlace, val live: SLive?)

data class SMessage(
    val id: Long,
    val roomId: Long,
    val senderId: Long?,
    val kind: String,
    val createdAtMs: Long,
    val text: String?,
    val location: SPlace?,
    val route: SRoute?,
)

data class SRoom(
    val id: Long,
    val name: String,
    val members: List<SUser>,
    /** 사람마다 어디까지 읽었는지(사용자 id -> 메시지 id). */
    val readUpTo: Map<Long, Long>,
    val lastMessage: SMessage?,
    val unread: Int,
)

object Wire {
    fun user(o: JSONObject) = SUser(o.getLong("id"), o.getString("name"))

    fun friend(o: JSONObject) = SFriend(o.getLong("id"), o.getString("name"), o.optStr("statusMsg"))

    fun place(o: JSONObject) = SPlace(o.optStr("name"), o.optStr("addr"), o.getDouble("lat"), o.getDouble("lon"))

    fun live(o: JSONObject) = SLive(
        carLat = o.getDouble("carLat"),
        carLon = o.getDouble("carLon"),
        remainSec = o.getInt("remainSec"),
        pct = o.getInt("pct"),
        etaAtMs = Instant.parse(o.getString("etaAt")).toEpochMilli(),
        traffic = o.optJSONArray("traffic")?.let(::traffic),
    )

    /** [[from, to, "FAST"], …] → 막대 구간. 모르는 단계는 건너뜀. */
    fun traffic(a: JSONArray): List<TrafficSegment> = (0 until a.length()).mapNotNull { i ->
        val s = a.optJSONArray(i) ?: return@mapNotNull null
        val level = runCatching { Congestion.valueOf(s.getString(2)) }.getOrNull() ?: return@mapNotNull null
        TrafficSegment(s.getDouble(0).toFloat(), s.getDouble(1).toFloat(), level)
    }

    fun route(o: JSONObject) = SRoute(
        state = o.getString("state"),
        from = place(o.getJSONObject("from")),
        dest = place(o.getJSONObject("dest")),
        live = o.optJSONObject("live")?.let(::live),
    )

    fun message(o: JSONObject) = SMessage(
        id = o.getLong("id"),
        roomId = o.getLong("roomId"),
        senderId = if (o.isNull("senderId")) null else o.getLong("senderId"),
        kind = o.getString("kind"),
        createdAtMs = Instant.parse(o.getString("createdAt")).toEpochMilli(),
        text = if (o.isNull("text")) null else o.getString("text"),
        location = o.optJSONObject("location")?.let(::place),
        route = o.optJSONObject("route")?.let(::route),
    )

    fun room(o: JSONObject): SRoom {
        val reads = o.getJSONArray("readUpTo")
        return SRoom(
            id = o.getLong("id"),
            name = o.getString("name"),
            members = o.getJSONArray("members").objects().map(::user),
            readUpTo = (0 until reads.length()).associate { reads.getJSONObject(it).let { r -> r.getLong("userId") to r.getLong("msgId") } },
            lastMessage = o.optJSONObject("lastMessage")?.let(::message),
            unread = o.optInt("unread"),
        )
    }

    // ───── 앱 → 서버 ─────

    fun placeJson(p: SPlace): JSONObject = JSONObject().put("name", p.name).put("addr", p.addr).put("lat", p.lat).put("lon", p.lon)

    fun liveJson(l: SLive): JSONObject = JSONObject()
        .put("carLat", l.carLat).put("carLon", l.carLon).put("remainSec", l.remainSec).put("pct", l.pct)
        .put("etaAt", Instant.ofEpochMilli(l.etaAtMs).toString())
        .put("traffic", l.traffic?.let { segs -> JSONArray(segs.map { JSONArray().put(it.from.toDouble()).put(it.to.toDouble()).put(it.level.name) }) } ?: JSONObject.NULL)

    fun frame(type: String, roomId: Long, ref: String? = null): JSONObject =
        JSONObject().put("type", type).put("roomId", roomId).also { if (ref != null) it.put("ref", ref) }

    fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStr(key: String): String = if (isNull(key)) "" else optString(key, "")
}
