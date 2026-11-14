package com.example.tokcar

import com.example.tokcar.data.hasRoadAddress
import com.example.tokcar.core.distanceM
import com.example.tokcar.data.mostCommon
import com.example.tokcar.data.ringPoints
import com.example.tokcar.data.parseGeocodeRoad
import com.example.tokcar.data.parseParcelAddress
import com.example.tokcar.data.parseNaverAddress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 네이버 Reverse Geocoding 응답 고르기(도로명 우선, 없으면 지번). 42dot 자리 예. */
class AddressLookupTest {
    private fun region(vararg names: String) = JSONObject().apply { names.forEachIndexed { i, n -> put("area$i", JSONObject().put("name", n)) } }

    @Test
    fun roadAddressWinsWithoutDongInParens() {
        val json = JSONObject().put("results", org.json.JSONArray()
            .put(JSONObject().put("name", "addr").put("region", region("kr", "경기도", "성남시 수정구", "시흥동")).put("land", JSONObject().put("number1", "293")))
            .put(JSONObject().put("name", "roadaddr").put("region", region("kr", "경기도", "성남시 수정구", "시흥동"))
                .put("land", JSONObject().put("name", "창업로40번길").put("number1", "20").put("number2", ""))))
        assertEquals("경기도 성남시 수정구 창업로40번길 20", parseNaverAddress(json))
    }

    @Test
    fun roadNameWithoutNumberStillBeatsParcel() {
        val json = JSONObject().put("results", org.json.JSONArray()
            .put(JSONObject().put("name", "addr").put("region", region("kr", "경기도", "성남시 수정구", "시흥동")).put("land", JSONObject().put("number1", "335")))
            .put(JSONObject().put("name", "roadaddr").put("region", region("kr", "경기도", "성남시 수정구", "시흥동"))
                .put("land", JSONObject().put("name", "창업로40번길").put("number1", "").put("number2", ""))))
        assertEquals("경기도 성남시 수정구 창업로40번길", parseNaverAddress(json))
    }

    /** 2026-11-14 실제 응답(42dot 자리): roadaddr 없이 addr 만 옴 → 도로명 없음으로 판단해 Geocoding 으로 넘어간다. */
    /** 기본(지번만): 도로명 항목이 같이 와도 지번을 고른다. */
    @Test
    fun parcelAddressIsPickedEvenWhenARoadIsPresent() {
        val json = JSONObject().put("results", org.json.JSONArray()
            .put(JSONObject().put("name", "roadaddr").put("region", region("kr", "경기도", "성남시 수정구", "시흥동"))
                .put("land", JSONObject().put("name", "창업로40번길").put("number1", "20").put("number2", "")))
            .put(JSONObject().put("name", "addr").put("region", region("kr", "경기도", "성남시 수정구", "시흥동")).put("land", JSONObject().put("number1", "335").put("number2", ""))))
        assertEquals("경기도 성남시 수정구 시흥동 335", parseParcelAddress(json))
        assertNull(parseParcelAddress(JSONObject().put("results", org.json.JSONArray())))
    }

    @Test
    fun parcelOnlyResponseHasNoRoadAddress() {
        val json = JSONObject().put("results", org.json.JSONArray()
            .put(JSONObject().put("name", "addr").put("region", region("kr", "경기도", "성남시 수정구", "시흥동")).put("land", JSONObject().put("number1", "335").put("number2", ""))))
        assertEquals("경기도 성남시 수정구 시흥동 335", parseNaverAddress(json))
        assertEquals(false, hasRoadAddress(json))
    }

    @Test
    fun geocodeRoadAddressIsReadFromTheFirstResult() {
        val ok = JSONObject().put("addresses", org.json.JSONArray().put(JSONObject().put("roadAddress", " 경기도 성남시 수정구 창업로40번길 20 ").put("jibunAddress", "경기도 성남시 수정구 시흥동 335")))
        assertEquals("경기도 성남시 수정구 창업로40번길 20", parseGeocodeRoad(ok))
        assertNull(parseGeocodeRoad(JSONObject().put("addresses", org.json.JSONArray())))
        assertNull(parseGeocodeRoad(JSONObject().put("addresses", org.json.JSONArray().put(JSONObject().put("roadAddress", "")))))
    }

    @Test
    fun ringPointsAreEightPointsAtTheGivenDistance() {
        val pts = ringPoints(37.4127212, 127.094681, 30.0)
        assertEquals(8, pts.size)
        pts.forEach { (la, lo) -> assertEquals(30.0, distanceM(37.4127212, 127.094681, la, lo), 0.6) }
        assertEquals(8, pts.toSet().size)
    }

    @Test
    fun mostCommonPicksTheMajorityAndTheEarlierOnTies() {
        assertEquals("b", mostCommon(listOf("a", "b", "b", "c")))
        assertEquals("a", mostCommon(listOf("a", "b")))
        assertNull(mostCommon(emptyList()))
    }

    @Test
    fun parcelAddressWhenNoRoadAndNullWhenNothing() {
        val json = JSONObject().put("results", org.json.JSONArray()
            .put(JSONObject().put("name", "addr").put("region", region("kr", "경기도", "성남시 수정구", "시흥동")).put("land", JSONObject().put("number1", "293").put("number2", "1"))))
        assertEquals("경기도 성남시 수정구 시흥동 293-1", parseNaverAddress(json))
        assertNull(parseNaverAddress(JSONObject().put("results", org.json.JSONArray())))
    }
}
