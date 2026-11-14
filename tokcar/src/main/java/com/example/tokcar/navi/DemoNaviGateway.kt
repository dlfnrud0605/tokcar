package com.example.tokcar.navi

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import com.example.tokcar.data.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 더미 GPS로 내비를 흉내 내는 구현.
 *
 * 에뮬레이터의 내비는 GPS가 움직이지 않아서 남은 시간·거리가 그대로다.
 * 그래서 기존 점검 앱처럼 assets/sim/eta_timeline_commute.json (판교 회사 -> 수서 집, 1초 간격)을
 * [DemoNaviHelper]로 재생하고, 거기서 나오는 남은 시간·거리·현재 위치로 진행값을 만든다.
 * 어느 목적지를 골라도 같은 이동 값을 재생한다(더미이므로 목적지마다 따로 만들지 않았다).
 */
class DemoNaviGateway(
    private val context: Context,
    private val scope: CoroutineScope,
    private val speedFactor: Float = DEFAULT_SPEED,
) : NaviGateway {

    override val label: String = "데모 GPS (더미 이동 ${speedFactor.toInt()}배속)"
    override val maxWaypoints: Int = 5

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private var helper: DemoNaviHelper? = null
    private var job: Job? = null

    override fun start() = Unit

    override fun requestRoute(dest: Place, vias: List<Place>) = play()

    private fun play() {
        stopPlayback()
        val demo = DemoNaviHelper(context, speedFactor, timelineAsset(context))
        helper = demo

        var total = 0
        var lastLoc: CurrentLocationInfo? = null
        var announcedHere = false
        demo.addListener(object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                lastLoc = currentLocationInfo
                // 출발 자리는 한 번만 알린다(경로 공유 카드의 「출발」 주소를 구하는 데 씀). 움직이는 동안 계속 알리면 주소를 계속 다시 찾는다.
                // 주소는 비워 보낸다(데모 파일의 「데모 경로 위」는 주소가 아니다): 앱이 좌표로 찾는다.
                if (!announcedHere) {
                    announcedHere = true
                    _events.tryEmit(NaviEvent.Here(currentLocationInfo.latitude, currentLocationInfo.longitude, ""))
                }
            }

            override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                if (total == 0) total = destinationInfo.remainTime
                val loc = lastLoc
                _events.tryEmit(
                    NaviEvent.Progress(
                        remainSec = destinationInfo.remainTime,
                        totalSec = total,
                        remainMeters = destinationInfo.remainDistance,
                        lat = loc?.latitude ?: destinationInfo.latitude,
                        lon = loc?.longitude ?: destinationInfo.longitude,
                        heading = loc?.heading ?: 0.0,
                    ),
                )
            }
        })

        demo.initialize(
            onConnected = {
                _events.tryEmit(NaviEvent.Preview)
                job = scope.launch {
                    delay(PREVIEW_MS)    // 실제 내비처럼 미리보기를 잠깐 보여 준 뒤 안내를 시작한다
                    _events.emit(NaviEvent.Guiding)
                    _events.emit(NaviEvent.Traffic(DEMO_TRAFFIC))    // 더미 이동에는 정체 정보가 없어서 화면 확인용 가짜 구간을 보낸다
                    while (true) {
                        demo.getCurrentLocationInfo()
                        demo.getDestinationInfo()
                        if (demo.isFinished()) {
                            delay(RESPONSE_WAIT_MS)    // 마지막 응답이 도착할 시간
                            _events.emit(NaviEvent.Arrived)
                            break
                        }
                        delay(POLL_MS)
                    }
                }
            },
            onFailed = { _events.tryEmit(NaviEvent.Error("데모 값 목록을 읽지 못했어요")) },
        )
    }

    /**
     * 재생할 이동 값 파일. tools/make_demo_route.py 로 만든 42dot → 집 도로 경로(eta_timeline_home.json)가 있으면 그것을, 없으면 예전 판교 → 수서 직선 경로를 쓴다.
     */

    // 데모는 경유지를 넣어도 이동 값이 같다. (진짜 내비에서만 경로가 바뀐다)
    override fun addWaypoint(via: Place, position: Int) = Unit
    override fun removeWaypoint(position: Int) = Unit

    override fun cancelRoute() = stopPlayback()

    override fun openSearchScreen(keyword: String) = Unit    // 데모는 앱 안의 가짜 검색 목록을 쓴다

    override fun release() = stopPlayback()

    private fun stopPlayback() {
        job?.cancel()
        job = null
        helper?.release()
        helper = null
    }

    companion object {
        /**
         * 시연 영상용: 42dot → 집 경로(실제 약 23분)가 약 45초에 끝나는 배속. 한 장면을 1분 안에 담으려고 올렸다(예전 15배속은 91초).
         * 이동은 안내 시작부터 흐르고 공유 대기 15초는 그와 동시에 흐르므로, 폰에서 움직임이 보이는 구간은 이 값에서 약 18초를 뺀 만큼이다(45배속이면 12초뿐이라 30배속으로 낮췄다).
         */
        const val DEFAULT_SPEED = 30f
        private const val HOME_TIMELINE = "sim/eta_timeline_home.json"

        /** 재생할 이동 값 파일: 42dot → 집 도로 경로(tools/make_demo_route.py)가 있으면 그것, 없으면 예전 판교 → 수서 직선 경로. */
        fun timelineAsset(context: Context): String =
            runCatching { context.assets.open(HOME_TIMELINE).close(); HOME_TIMELINE }.getOrDefault(DemoNaviHelper.COMMUTE_TIMELINE)
        private const val PREVIEW_MS = 3000L
        private const val POLL_MS = 1000L
        private const val RESPONSE_WAIT_MS = 300L

        /** 데모용 가짜 정체 구간(경로 전체를 0~1로 본 위치): 원활 사이사이에 보통 · 서행 · 정체 한 곳씩. */
        private val DEMO_TRAFFIC = listOf(
            TrafficSegment(0f, 0.22f, Congestion.FAST),
            TrafficSegment(0.22f, 0.34f, Congestion.MODERATE),
            TrafficSegment(0.34f, 0.46f, Congestion.FAST),
            TrafficSegment(0.46f, 0.58f, Congestion.SLOW),
            TrafficSegment(0.58f, 0.72f, Congestion.FAST),
            TrafficSegment(0.72f, 0.84f, Congestion.HEAVY),
            TrafficSegment(0.84f, 1f, Congestion.FAST),
        )
    }
}
