package com.example.tokcar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 좌표 → 사람이 읽는 주소. 못 찾거나 실패하면 null. */
fun interface AddressLookup {
    suspend fun addressOf(lat: Double, lon: Double): String?
}

/**
 * 좌표 → 주소. 네이버 Reverse Geocoding 을 **가족 서버를 거쳐** 부른다(서버가 네이버 키를 붙임: 앱에는 비밀 키가 없다).
 * 서버 주소 [base] 와 로그인 토큰 [token]이 있을 때만 동작한다(토큰이 없으면 null). 응답은 네이버 응답 그대로라 해석은 폰 앱 lib/data/reverse_geocoder.dart 와 같다.
 *
 * 기본은 **지번 주소**("경기도 성남시 수정구 시흥동 335")만 쓴다([preferRoad] = false). 지번은 필지마다 있어서 어느 좌표든 거의 항상 나오고,
 * 호출도 한 번이면 된다. 카드의 「출발」은 앱이 도 이름을 뺀다. 도로명이 필요하면 [preferRoad] = true:
 *
 * 좌표가 건물에 도로명 주소가 붙은 점이 아니면 응답에 도로명(roadaddr)이 아예 없고 지번(addr)만 온다
 * (2026-11-14 로그: 42dot 자리 37.4127,127.0947 → 시흥동 335). 그때는 그 지번 주소를 Geocoding 으로 다시 검색해
 * 그 번지의 도로명 주소(roadAddress)를 받고, 그 번지에도 도로명이 없으면(그 로그에서 roadAddress 가 "") 둘레 30m · 60m 의
 * 8방향 점을 차례로 주소 변환해 가장 가까운 고리에서 가장 많이 나온 도로명 주소를 쓴다. 그것도 없으면 지번 주소를 그대로 쓴다.
 */
class ServerReverseGeocoder(
    private val base: String,
    private val token: () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build(),
    private val log: (String) -> Unit = {},
    /** true 면 도로명 주소를 찾아 쓴다(없으면 지번). 기본 false = 지번 주소만. */
    private val preferRoad: Boolean = false,
) : AddressLookup {
    /** 서버 중계 요청. 로그인 전이면 null. */
    private fun request(pathAndQuery: String): Request? {
        val t = token() ?: return null
        return Request.Builder().url(base.trimEnd('/') + pathAndQuery).header("Authorization", "Bearer $t").build()
    }

    override suspend fun addressOf(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val orders = if (preferRoad) "roadaddr,addr" else "addr"
        val req = request("/api/naver/reverse-geocode?coords=$lon,$lat&orders=$orders&output=json") ?: return@withContext null
        runCatching {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                // 도로명이 왜 안 나오는지 볼 수 있게 원문을 남긴다(응답에 키는 없다)
                log("주소 변환 ${res.code} $lat,$lon → ${body.take(900)}")
                if (!res.isSuccessful) return@use null
                val json = JSONObject(body)
                if (!preferRoad) return@use parseParcelAddress(json)
                val address = parseNaverAddress(json)
                if (address == null || hasRoadAddress(json)) address else roadOfParcel(address, lat, lon) ?: nearbyRoad(lat, lon) ?: address
            }
        }.onFailure { log("주소 변환 실패: $it") }.getOrNull()
    }

    /** 좌표 하나의 도로명 주소(roadaddr 가 있을 때만). 실패하거나 없으면 null. */
    private fun roadAt(lat: Double, lon: Double): String? = runCatching {
        val req = request("/api/naver/reverse-geocode?coords=$lon,$lat&orders=roadaddr&output=json") ?: return null
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) null else JSONObject(res.body?.string().orEmpty()).takeIf(::hasRoadAddress)?.let(::parseNaverAddress)
        }
    }.getOrNull()

    /** 둘레 점들을 가까운 고리부터 주소 변환해, 도로명이 나온 첫 고리에서 가장 많이 나온 주소를 돌려준다. 없으면 null. */
    private fun nearbyRoad(lat: Double, lon: Double): String? {
        for (m in NEARBY_RINGS_M) {
            val found = ringPoints(lat, lon, m).mapNotNull { (la, lo) -> roadAt(la, lo) }
            val best = mostCommon(found)
            log("둘레 ${m}m 도로명 ${found.size}건 → $best")
            if (best != null) return best
        }
        return null
    }

    /** 지번 주소를 Geocoding 으로 검색해 그 번지의 도로명 주소를 받는다. 못 받으면 null. */
    private fun roadOfParcel(parcel: String, lat: Double, lon: Double): String? = runCatching {
        val q = URLEncoder.encode(parcel, "UTF-8")
        val req = request("/api/naver/geocode?query=$q&coordinate=$lon,$lat&count=1") ?: return null
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            log("지번 → 도로명 ${res.code} $parcel → ${body.take(600)}")
            if (!res.isSuccessful) null else parseGeocodeRoad(JSONObject(body))
        }
    }.onFailure { log("지번 → 도로명 실패: $it") }.getOrNull()
}

private val NEARBY_RINGS_M = listOf(30.0, 60.0)

/** 중심에서 [meters] 떨어진 8방향(동서남북 + 대각선) 점 (위도, 경도). */
fun ringPoints(lat: Double, lon: Double, meters: Double): List<Pair<Double, Double>> {
    val dLat = meters / 111_320.0
    val dLon = meters / (111_320.0 * Math.cos(Math.toRadians(lat)))
    return (0 until 8).map { i ->
        val a = Math.toRadians(i * 45.0)
        (lat + dLat * Math.cos(a)) to (lon + dLon * Math.sin(a))
    }
}

/** 가장 많이 나온 값(같으면 먼저 나온 것). 비었으면 null. */
fun mostCommon(items: List<String>): String? = items.groupingBy { it }.eachCount().entries.maxWithOrNull(compareBy({ it.value }, { -items.indexOf(it.key) }))?.key

/** 응답에서 지번 주소("경기도 성남시 수정구 시흥동 335")만 만든다. 지번 항목이 없으면 null. */
fun parseParcelAddress(json: JSONObject): String? {
    val results = json.optJSONArray("results") ?: return null
    val r = (0 until results.length()).map { results.getJSONObject(it) }.firstOrNull { it.optString("name") == "addr" } ?: return null
    fun area(k: String) = r.optJSONObject("region")?.optJSONObject(k)?.optString("name").orEmpty().trim()
    fun land(k: String) = r.optJSONObject("land")?.optString(k).orEmpty().trim()
    val n1 = land("number1")
    if (n1.isEmpty()) return null
    val number = land("number2").let { n2 -> if (n2.isEmpty()) n1 else "$n1-$n2" }
    return listOf(area("area1"), area("area2"), area("area3"), area("area4"), number).filter { it.isNotEmpty() }.joinToString(" ")
}

/** 응답에 도로명 주소(roadaddr, 도로 이름 있음)가 있는가. */
fun hasRoadAddress(json: JSONObject): Boolean {
    val results = json.optJSONArray("results") ?: return false
    return (0 until results.length()).map { results.getJSONObject(it) }.any {
        it.optString("name") == "roadaddr" && it.optJSONObject("land")?.optString("name").orEmpty().isNotBlank()
    }
}

/** Geocoding 응답의 첫 결과에서 도로명 주소(roadAddress)를 꺼낸다. 비었으면 null. */
fun parseGeocodeRoad(json: JSONObject): String? {
    val list = json.optJSONArray("addresses") ?: return null
    if (list.length() == 0) return null
    return list.getJSONObject(0).optString("roadAddress").trim().takeIf { it.isNotEmpty() }
}

/**
 * 응답에서 주소 글을 만든다. 도로명 주소가 있으면 "경기도 성남시 수정구 창업로40번길 20".
 * 도로 위의 점처럼 도로명만 있고 번지가 비면 번지 없이 "경기도 성남시 수정구 창업로40번길"까지만 쓴다(지번으로 넘어가지 않음).
 * 도로명이 아예 없을 때만 지번 주소. 차량 카드의 「출발」은 한 줄이라 폰 앱과 달리 괄호 안 동 이름은 붙이지 않는다. 아무 결과도 없으면 null.
 */
fun parseNaverAddress(json: JSONObject): String? {
    val results = json.optJSONArray("results") ?: return null
    fun pick(name: String): JSONObject? = (0 until results.length()).map { results.getJSONObject(it) }.firstOrNull { it.optString("name") == name }
    fun area(r: JSONObject, k: String) = r.optJSONObject("region")?.optJSONObject(k)?.optString("name").orEmpty().trim()
    fun land(r: JSONObject, k: String) = r.optJSONObject("land")?.optString(k).orEmpty().trim()
    fun number(r: JSONObject) = land(r, "number1").let { n1 -> land(r, "number2").let { n2 -> if (n2.isEmpty()) n1 else "$n1-$n2" } }
    fun join(vararg parts: String) = parts.filter { it.isNotEmpty() }.joinToString(" ")

    val road = pick("roadaddr")
    if (road != null && land(road, "name").isNotEmpty()) {
        return join(area(road, "area1"), area(road, "area2"), land(road, "name"), number(road))    // 번지가 비면 number() 도 빈 글
    }
    val parcel = pick("addr")
    if (parcel != null && land(parcel, "number1").isNotEmpty()) {
        return join(area(parcel, "area1"), area(parcel, "area2"), area(parcel, "area3"), area(parcel, "area4"), number(parcel))
    }
    return null
}
