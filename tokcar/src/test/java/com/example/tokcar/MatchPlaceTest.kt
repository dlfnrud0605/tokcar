package com.example.tokcar

import com.example.tokcar.core.TokStore
import com.example.tokcar.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 내비가 알려 준 목적지 좌표를 앱의 어느 장소로 볼지(내비가 만든 별도 장소보다 방 목적지를 먼저). */
class MatchPlaceTest {
    // 집(앱이 정한 좌표)과, 예전에 내비가 알려 준 값으로 만든 별도 장소(좌표는 내비 값 그대로 · 이름은 주소 글자)
    private val home = Place("home", "집", "성남시 수정구 산성대로 501", 37.456205, 127.162272)
    private val echo = Place("sp:31", "경기도 성남시 수정구 산성대로 501 (양지동)", "수정구 양지동", 37.456212017, 127.162282375)
    private val far = Place("stn", "서현역", "성남시 분당구 서현로 170", 37.385, 127.123)
    private val places = listOf(home, echo, far).associateBy { it.id }

    @Test
    fun roomDestinationWinsOverACloserNavigationMadePlace() {
        // 내비 좌표는 echo 와 똑같아서 echo 가 더 가깝지만, 이 방의 목적지는 집이다
        val p = TokStore.matchPlace(places, listOf("home"), echo.lat, echo.lon)
        assertEquals("home", p?.id)
    }

    @Test
    fun withoutARoomDestinationTheNearestPlaceIsUsed() {
        assertEquals("sp:31", TokStore.matchPlace(places, emptyList(), echo.lat, echo.lon)?.id)
    }

    @Test
    fun aRoomDestinationFarAwayDoesNotHijackTheMatch() {
        // 방 목적지가 서현역이면 집 근처 좌표는 방 목적지에 끌려가지 않고 가장 가까운 장소로
        assertEquals("sp:31", TokStore.matchPlace(places, listOf("stn"), echo.lat, echo.lon)?.id)
    }

    @Test
    fun unknownSpotGivesNull() {
        assertNull(TokStore.matchPlace(places, listOf("home"), 37.5, 127.0))
    }
}
