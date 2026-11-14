package com.example.tokcar.navi

import com.example.tokcar.data.Place
import kotlinx.coroutines.flow.SharedFlow

/** 내비(진짜 또는 데모)가 앱에 알려 주는 일. */
sealed interface NaviEvent {
    /** 내비가 알려 준 차의 지금 자리와 그 주소(경로 공유 카드의 「출발」, 내 위치 보내기에 씀). 주소를 모르면 빈 글. */
    data class Here(val lat: Double, val lon: Double, val address: String) : NaviEvent

    /** 경로를 잡았다(미리보기). 곧 안내가 시작된다. */
    data object Preview : NaviEvent

    /** 안내가 시작됐다. */
    data object Guiding : NaviEvent

    /** 주행 진행값. lat/lon 은 내 현재 위치(더미 GPS 또는 내비가 준 값). */
    data class Progress(
        val remainSec: Int,
        val totalSec: Int,
        val remainMeters: Int,
        val lat: Double,
        val lon: Double,
        val heading: Double,
    ) : NaviEvent

    /** 목적지에 도착했다. */
    data object Arrived : NaviEvent

    /** 지금 경로의 정체 구간(경로 전체를 0~1로 본 위치). 정체 정보를 못 받았으면 null, 받았는데 정체가 없으면 빈 목록. */
    data class Traffic(val segments: List<TrafficSegment>?) : NaviEvent

    /** 앱이 시키지 않았는데 내비에서 이미 안내가 진행 중이다(내비에서 직접 시작, 또는 앱을 켜기 전부터 안내 중). */
    data class ExternalRoute(val name: String, val addr: String, val lat: Double, val lon: Double) : NaviEvent

    /** 앱 밖(내비에서 직접)에서 안내가 끝났다/취소됐다. */
    data object Cancelled : NaviEvent

    /** 내비 서비스에 연결하지 못했다(실제 내비). 앱은 데모 GPS로 바꿔 계속 쓸 수 있게 한다. */
    data object ConnectFailed : NaviEvent

    data class Error(val message: String) : NaviEvent
}

/**
 * 앱이 내비와 주고받는 부분. 에뮬레이터는 GPS가 움직이지 않으므로
 * [DemoNaviGateway](더미 GPS 재생)와 [PleosNaviGateway](진짜 NaviHelper)를 바꿔 끼울 수 있게 했다.
 */
interface NaviGateway {
    /** 설정 화면에 보이는 이름. */
    val label: String

    /** 한 번에 넣을 수 있는 경유지 수. (진짜 내비는 개별 지정이 FIRST/SECOND 두 곳이라 2) */
    val maxWaypoints: Int

    val events: SharedFlow<NaviEvent>

    fun start()
    fun release()

    /** 목적지(와 경유지)로 안내를 시작한다. */
    fun requestRoute(dest: Place, vias: List<Place>)

    /** 안내 중에 경유지를 하나 더 넣는다. [position] 은 0부터 시작하는 순서. */
    fun addWaypoint(via: Place, position: Int)

    /** 안내 중에 경유지를 뺀다. */
    fun removeWaypoint(position: Int)

    /** 안내를 끝낸다. */
    fun cancelRoute()

    /** 내비에서 [keyword] 로 장소를 검색한 화면을 띄운다. (빈 검색어는 내비가 거절해서, 빈 값이면 내비를 앞으로만 띄운다) */
    fun openSearchScreen(keyword: String)
}
