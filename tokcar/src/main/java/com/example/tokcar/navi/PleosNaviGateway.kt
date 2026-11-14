package com.example.tokcar.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TrafficStatus
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.example.tokcar.core.distanceM
import com.example.tokcar.data.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 진짜 Pleos NaviHelper를 쓰는 구현.
 *
 * - 경로: requestRoute / requestRouteWithWaypoints, 안내 종료: cancelRoute
 * - 진행값: 안내 중에 getDestinationInfo·getCurrentLocationInfo 를 [POLL_MS] 간격으로 조회
 * - 상태: onRouteStarted(미리보기), onNavigationStatus(ROUTE_DRIVE=안내 시작, FREE_DRIVE=안내 끝), onDestinationArrived(도착)
 *
 * 에뮬레이터 내비는 GPS가 움직이지 않아 진행값이 변하지 않는다. 그때는 [DemoNaviGateway]를 쓴다.
 * (이 클래스의 이동·도착 흐름은 실차에서 확인하지 못했다. 경로 요청과 상태 알림은 점검 앱으로 에뮬레이터에서 확인함.)
 */
class PleosNaviGateway(
    private val context: Context,
    private val scope: CoroutineScope,
) : NaviGateway {

    override val label: String = "실제 내비 (NaviHelper)"
    override val maxWaypoints: Int = 2    // 개별 경유지 지정이 FIRST/SECOND 두 곳뿐이다

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private var navi: NaviHelper? = null
    private var connected = false
    private val afterConnect = mutableListOf<() -> Unit>()
    private var pollJob: Job? = null
    private var total = 0
    private var routeTotalM = 0    // 경로 전체 길이(m): onRouteStarted 에서 받음. 정체 구간(미터)을 비율로 바꿀 때 씀
    private var tripReq: TripRequest? = null    // 목적지 여정(정체 포함) 요청. 내비 오른쪽 아래 색 띠와 같은 정보를 받는다
    private var haveTrip = false    // 여정 정보로 정체를 알렸으면 구간 목록(RouteStateInfo)은 덮어쓰지 않는다
    private var remainM = 0    // 마지막으로 받은 남은 거리(m): 경로 전체 길이를 모를 때(앱을 켜기 전부터 안내 중) 대신 씀
    private var lastLoc: CurrentLocationInfo? = null
    private val route = RouteTracker()    // 취소 신호가 정말 끝남인지 가린다(경로 교체·미리보기 취소 구분)
    private var lastDest: Pair<Double, Double>? = null    // 앱이 마지막으로 알고 있는 목적지 좌표(앱이 시킨 것 또는 이미 알린 것)
    private var endJob: Job? = null    // 안내 중 취소 신호 뒤 새 경로를 기다리는 중(내비에서 목적지를 바꾸면 이전 경로 취소 → 새 경로 시작 순서로 온다)

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            // 앱을 켜기 전부터 내비가 안내 중이면 그 경로를 이어받는다.
            // isRouting 은 안내 중에도 false 로 오는 경우가 있어서(이전 에뮬레이터 시험) naviMode 도 함께 본다.
            if ((routeStateInfo.isRouting || routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING) && route.adopted()) {
                startPolling()
            }
            // 정체 구간: 안내 중일 때만 알린다(안내가 아니면 지난 값이 남지 않게 빈 목록)
            val guiding = routeStateInfo.isRouting || routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING
            val fromList = if (guiding) trafficOf(routeStateInfo.trafficStatus) else null    // 로그도 이 안에서 남김
            if (!guiding) haveTrip = false
            if (!haveTrip) _events.tryEmit(NaviEvent.Traffic(fromList))
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "onRouteStarted $routeStartInfo")
            total = 0
            routeTotalM = routeStartInfo.distance
            haveTrip = false
            tripReq = null
            _events.tryEmit(NaviEvent.Traffic(null))    // 새 경로: 이전 경로의 정체 표시를 지움
            route.started()
            _events.tryEmit(NaviEvent.Preview)
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            val conds = tripInfo.summary.trafficConditions
            val level = { t: TrafficCongestionType -> when (t) {
                TrafficCongestionType.FAST -> Congestion.FAST
                TrafficCongestionType.MODERATE -> Congestion.MODERATE
                TrafficCongestionType.SLOW -> Congestion.SLOW
                TrafficCongestionType.HEAVY -> Congestion.HEAVY
                else -> null    // 알 수 없음: 구간으로 안 씀(막대 바탕 회색)
            } }
            val segs = Traffic.fromRatios(conds.map { RawRatio(it.startRatio, it.endRatio, level(it.congestionType)) })
            Log.i(
                TAG,
                "trip 거리=${tripInfo.distance} eta=${tripInfo.eta} 배터리=${tripInfo.estimatedSocAtDest} 교통 ${conds.size}건 → 막대 ${segs.size}건: " +
                    conds.take(40).joinToString(" ") { "${it.startRatio}-${it.endRatio}:${it.congestionType.name.first()}" },
            )
            if (conds.isEmpty()) return    // 여정 정보에 교통이 없으면 구간 목록(RouteStateInfo) 쪽을 그대로 씀
            haveTrip = true
            _events.tryEmit(NaviEvent.Traffic(segs))
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i(TAG, "onNavigationStatus $status")
            when (status) {
                NavigationStatus.ROUTE_DRIVE -> {
                    route.started()
                    _events.tryEmit(NaviEvent.Guiding)
                    startPolling()
                }
                NavigationStatus.FREE_DRIVE -> onRouteGone()
                else -> Unit
            }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val before = lastLoc
            lastLoc = currentLocationInfo
            val addr = currentLocationInfo.address.orEmpty()
            if (before == null || before.address != currentLocationInfo.address) {
                Log.i(TAG, "현재 위치 ${currentLocationInfo.latitude},${currentLocationInfo.longitude} 주소=$addr")
            }
            _events.tryEmit(NaviEvent.Here(currentLocationInfo.latitude, currentLocationInfo.longitude, addr))
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val known = lastDest
            val hasDest = destinationInfo.name.isNotBlank() || destinationInfo.latitude != 0.0 || destinationInfo.longitude != 0.0    // 미리보기 중에는 빈 값이 옴
            if (!hasDest) return
            if (route.active && (known == null || distanceM(known.first, known.second, destinationInfo.latitude, destinationInfo.longitude) > SAME_DEST_M)) {
                Log.i(TAG, "외부 목적지로 알림 ${destinationInfo.name} / ${destinationInfo.address} (앱이 아는 목적지=${known ?: "없음"})")
                lastDest = destinationInfo.latitude to destinationInfo.longitude    // 앱이 모르는 목적지(내비에서 직접 시작·변경)를 앱에 알린다
                _events.tryEmit(
                    NaviEvent.ExternalRoute(destinationInfo.name, destinationInfo.address, destinationInfo.latitude, destinationInfo.longitude),
                )
            }
            if (tripReq == null && hasDest) {
                tripReq = TripRequest(destinationInfo.latitude, destinationInfo.longitude, destinationInfo.poiId.orEmpty(), destinationInfo.name, destinationInfo.poiSubId.orEmpty())
                navi?.requestTrip(tripReq!!)    // 처음 한 번 바로 요청(이후는 약 30초마다)
            }
            Log.i(TAG, "dest ${destinationInfo.name} 남은 ${destinationInfo.remainTime}초 ${destinationInfo.remainDistance}m (안내 중=${route.active})")
            if (total == 0) total = destinationInfo.remainTime
            // 앱을 안내 도중에 켜서 경로를 이어받은 경우 경로 전체 길이를 모른다(onRouteStarted 를 못 받음). 남은 거리를 처음 알게 되면
            // 정체 정보를 바로 다시 읽어 막대가 30초를 기다리지 않게 한다. (이어받은 경로는 남은 거리를 전체로 쓰므로 차가 이미 움직였다면 위치가 어긋날 수 있음)
            val firstRemain = remainM == 0 && destinationInfo.remainDistance > 0
            remainM = destinationInfo.remainDistance
            if (firstRemain && routeTotalM == 0) navi?.getRouteStateInfo()
            val loc = lastLoc
            _events.tryEmit(
                NaviEvent.Progress(
                    remainSec = destinationInfo.remainTime,
                    totalSec = total.coerceAtLeast(destinationInfo.remainTime),
                    remainMeters = destinationInfo.remainDistance,
                    lat = loc?.latitude ?: destinationInfo.latitude,
                    lon = loc?.longitude ?: destinationInfo.longitude,
                    heading = loc?.heading ?: 0.0,
                ),
            )
        }

        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
            Log.i(TAG, "북마크 ${bookmarkInfoList.size}개")
            bookmarkInfoList.forEach { Log.i(TAG, "북마크 ${it.category} 이름=${it.name} 주소=${it.address} 좌표=${it.latitude},${it.longitude}") }
        }

        override fun onDestinationChanged() {
            navi?.getDestinationInfo()
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.i(TAG, "onDestinationArrived $destinationArrivedInfo")
            route.appEnded()
            lastDest = null
            stopPolling()
            _events.tryEmit(NaviEvent.Arrived)
        }

        override fun onRouteCancelled() = onRouteGone()

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "onError $errorCode")
            if (route.errorSignal()) {    // 경로 요청이 거절되면 미리보기는 시작되지 못함
                lastDest = null
                stopPolling()
                _events.tryEmit(NaviEvent.Cancelled)
            }
            _events.tryEmit(NaviEvent.Error("내비 오류: $errorCode"))
        }
    }

    /**
     * 내비가 취소 신호(onRouteCancelled·FREE_DRIVE)를 보냈다. 정말 끝난 것일 때만 앱에 알린다(경로 교체는 제외).
     * 시작된 안내가 앱 모르게 취소된 것은 내비에서 목적지를 바꾸는 중일 수 있어서(FREE_DRIVE 약 2.5초 뒤 새 경로 시작)
     * [RouteTracker.ENDING_WAIT_MS] 동안 새 경로가 시작되지 않을 때만 끝으로 알린다.
     */
    private fun onRouteGone() {
        if (route.active && !route.awaitingStart) {
            // 앱이 경유지를 바꾼 직후면 내비가 경로를 다시 잡는 중(약 8.5초): 더 오래 기다린다
            val wait = if (route.recentlyRerouted) RouteTracker.REROUTE_WAIT_MS else RouteTracker.ENDING_WAIT_MS
            if (!route.beginEnding()) return
            endJob?.cancel()
            endJob = scope.launch {
                delay(wait)
                if (route.confirmEnd()) routeEnded()
            }
            return
        }
        if (route.cancelSignal()) routeEnded()
    }

    private fun routeEnded() {
        lastDest = null
        tripReq = null
        haveTrip = false
        stopPolling()
        _events.tryEmit(NaviEvent.Cancelled)
    }

    override fun start() {
        if (navi != null) return
        val helper = NaviHelper(context.applicationContext)
        navi = helper
        helper.initialize(
            {
                connected = true
                helper.addListener(listener)
                helper.getRouteStateInfo()    // 이미 안내 중인지 확인
                helper.getCurrentLocationInfo()    // 차의 지금 자리 · 주소(경로 공유 카드의 「출발」)
                helper.getBookmarkInfo()    // 내비에 저장된 즐겨찾기(집 · 회사 별명이 있는지 로그로 확인)
                afterConnect.toList().forEach { it() }
                afterConnect.clear()
            },
            { t ->
                Log.w(TAG, "연결 실패", t)
                navi = null    // 다음에 다시 연결을 시도할 수 있게 비움
                _events.tryEmit(NaviEvent.ConnectFailed)
            },
        )
    }

    private fun whenConnected(block: () -> Unit) {
        if (connected) block() else afterConnect += block
        start()
    }

    private fun routeInfo(p: Place) = RouteInfo(
        latitude = p.lat,
        longitude = p.lon,
        poiName = p.name,
        poiId = "",    // 좌표만으로 요청한다(내비의 장소 DB 식별자 없이)
        address = p.addr,
        poiSubId = "",
    )

    private fun waypointInfo(p: Place, position: Int) = RequestWaypointInfo(
        latitude = p.lat,
        longitude = p.lon,
        poiName = p.name,
        waypointIndex = if (position == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
        poiId = "",
        address = p.addr,
        poiSubId = "",
    )

    override fun requestRoute(dest: Place, vias: List<Place>) = whenConnected {
        lastDest = dest.lat to dest.lon    // 앱이 시킨 경로는 '외부 안내'가 아니다
        route.requested()
        runCatching {
            if (vias.isEmpty()) {
                navi?.requestRoute(routeInfo(dest))
            } else {
                navi?.requestRouteWithWaypoints(
                    RouteWithWaypointsRequest(
                        destination = routeInfo(dest),
                        waypoints = vias.take(maxWaypoints).mapIndexed { i, p -> waypointInfo(p, i) },
                    ),
                )
            }
        }.onFailure { _events.tryEmit(NaviEvent.Error("경로를 요청하지 못했어요")) }
    }

    override fun addWaypoint(via: Place, position: Int) = whenConnected {
        route.rerouting()
        runCatching { navi?.addWaypoint(waypointInfo(via, position)) }
            .onFailure { _events.tryEmit(NaviEvent.Error("경유지를 넣지 못했어요")) }
    }

    override fun removeWaypoint(position: Int) = whenConnected {
        route.rerouting()
        runCatching { navi?.removeWaypoint(if (position == 0) WaypointIndex.FIRST else WaypointIndex.SECOND) }
            .onFailure { _events.tryEmit(NaviEvent.Error("경유지를 빼지 못했어요")) }
    }

    override fun cancelRoute() = whenConnected {
        endJob?.cancel()
        route.appEnded()
        lastDest = null
        stopPolling()
        runCatching { navi?.cancelRoute() }
    }

    /**
     * 내비의 장소 검색(SearchPlace) 결과 화면을 띄운다. 목적지를 고르면 onRouteStarted 등이 오고 앱이 읽어 간다.
     * NaviHelper SDK에는 검색칸만 여는 요청이 없고(NavigationScreenRequest 소스로 확인), 검색어가 비어 있으면 내비가
     * "search place query is invalid" 로 거절하므로(에뮬레이터 로그로 확인) 앱이 검색어를 받아 넘긴다. 비어 있으면 내비를 앞으로만 띄운다.
     */
    override fun openSearchScreen(keyword: String) = whenConnected {
        val request = if (keyword.isBlank()) NavigationScreenRequest.Foreground else NavigationScreenRequest.SearchPlace(keyword.trim())
        runCatching { navi?.requestNavigationScreen(request) }
            .onFailure { _events.tryEmit(NaviEvent.Error("내비 검색 화면을 열지 못했어요")) }
    }

    /**
     * 내비의 정체 정보(TrafficStatus)를 막대용 구간으로 바꾼다. 구간의 offset 은 경로 시작에서의 거리(m)라고 보고 비율로 바꾼다.
     * (offset 이 「지금 위치부터」인지는 에뮬레이터·실차에서 확인하지 못했다. 아래 로그로 실제 값을 보고 맞춰야 한다.)
     */
    private fun trafficOf(status: TrafficStatus?): List<TrafficSegment>? {
        status ?: return null    // 정체 정보를 못 받음
        val items = status.congestionItemList.map {
            RawCongestion(
                it.offset, it.distance,
                when (it.trafficCongestionType) {
                    TrafficCongestionType.FAST -> Congestion.FAST
                    TrafficCongestionType.MODERATE -> Congestion.MODERATE
                    TrafficCongestionType.SLOW -> Congestion.SLOW
                    TrafficCongestionType.HEAVY -> Congestion.HEAVY
                    else -> null    // 알 수 없음은 구간으로 안 씀
                },
            )
        }
        val totalM = (if (routeTotalM > 0) routeTotalM else remainM).toDouble()
        if (totalM <= 0.0) return null    // 경로 전체 길이를 몰라 위치를 계산할 수 없음(정체가 없다는 뜻이 아님)
        val segs = Traffic.segments(items, totalM)
        Log.i(
            TAG,
            "traffic 요약=${status.summary} 정체구간 ${status.congestionItemList.size}건 → 막대 ${segs.size}건 (경로 전체 ${totalM.toInt()}m) " +
                "처음 3건=" + status.congestionItemList.take(3).joinToString { "[offset=${it.offset} dist=${it.distance} ${it.trafficCongestionType} ${it.roadName}]" } +
                " 돌발=${status.trafficEventList.size}건",
        )
        return segs
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            var n = 0
            while (true) {
                navi?.getCurrentLocationInfo()
                navi?.getDestinationInfo()
                if (n++ % TRAFFIC_EVERY == 0) {    // 정체 정보는 자주 안 바뀌어서 가끔만 읽는다
                    navi?.getRouteStateInfo()
                    tripReq?.let { navi?.requestTrip(it) }
                }
                delay(POLL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun release() {
        endJob?.cancel()
        stopPolling()
        navi?.let {
            runCatching { it.removeListener(listener) }
            runCatching { it.release() }
        }
        navi = null
        connected = false
        afterConnect.clear()
    }

    companion object {
        private const val TAG = "PleosNavi"
        private const val SAME_DEST_M = 300.0
        private const val POLL_MS = 5000L    // 실차 조회 간격(약 5초)
        private const val TRAFFIC_EVERY = 6    // 정체 정보는 6번에 한 번(약 30초)
    }
}
