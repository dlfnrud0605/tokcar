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
 * 시연용: **실제 내비에 연결**하면서 남은 시간 · 진행률은 **움직이는 데모 이동 값**을 쓴다.
 *
 * 에뮬레이터의 Pleos 내비는 위치가 고정이라 안내를 시작해도 남은 시간이 줄지 않는다. 그렇다고 데모 GPS 모드로 가면 내비 화면에 경로가 뜨지 않는다.
 * 그래서 경로 요청 · 경유지 · 취소 · 내비 화면 · 정체 · 내 위치(출발 주소)는 [real] 에 그대로 맡기고,
 * [real] 이 알려 주는 진행값(Progress)과 도착(Arrived)은 버리고 [DemoNaviHelper] 가 재생하는 이동 값으로 대신 보낸다.
 * 재생이 끝나면(도착) 앱에 도착을 알리고 내비 안내도 끝낸다.
 *
 * 이동 값은 42dot → 집 경로 하나라서, 목적지를 어디로 골라도 같은 시간이 줄어든다(시연 Take A 용). 경유지를 넣어도 재생은 이어진다.
 * 실제 차에서는 쓰지 않는다(설정의 개발용 스위치로만 켠다).
 */
class HybridNaviGateway(
    private val context: Context,
    private val scope: CoroutineScope,
    private val real: NaviGateway,
    private val speedFactor: Float = DemoNaviGateway.DEFAULT_SPEED,
) : NaviGateway {

    override val label: String = "실제 내비 + 데모 이동값 (${speedFactor.toInt()}배속)"
    override val maxWaypoints: Int get() = real.maxWaypoints

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private var helper: DemoNaviHelper? = null
    private var sim: Job? = null
    private var simOn = false
    private val relay: Job = scope.launch { real.events.collect(::onReal) }

    private suspend fun onReal(e: NaviEvent) {
        when (e) {
            is NaviEvent.Progress, NaviEvent.Arrived -> Unit    // 에뮬레이터 내비는 움직이지 않으니 진행 · 도착은 이동 값으로 대신한다
            NaviEvent.Preview -> { stopSim(); _events.emit(e) }    // 새 경로: 이동 값을 처음부터 다시
            NaviEvent.Guiding -> {
                _events.emit(e)
                if (!simOn) startSim()    // 경유지를 넣어 안내가 다시 시작돼도 재생은 이어간다
            }
            // 앱을 켜기 전부터 내비가 안내 중이던 경로를 이어받았거나 내비에서 직접 시작한 안내: Guiding 이 오지 않으니 여기서 이동 값을 시작한다
            is NaviEvent.ExternalRoute -> { _events.emit(e); if (!simOn) startSim() }
            NaviEvent.Cancelled -> { stopSim(); _events.emit(e) }
            else -> _events.emit(e)
        }
    }

    private fun startSim() {
        simOn = true
        val demo = DemoNaviHelper(context, speedFactor, DemoNaviGateway.timelineAsset(context))
        helper = demo
        var total = 0
        var lastLoc: CurrentLocationInfo? = null
        demo.addListener(object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                lastLoc = currentLocationInfo
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
                if (!simOn || helper !== demo) return@initialize    // 그 사이 멈췄거나 새 재생으로 바뀜
                sim = scope.launch {
                    while (true) {
                        demo.getCurrentLocationInfo()
                        demo.getDestinationInfo()
                        if (demo.isFinished()) {
                            delay(RESPONSE_WAIT_MS)    // 마지막 응답이 도착할 시간
                            _events.emit(NaviEvent.Arrived)
                            stopSim()
                            real.cancelRoute()    // 도착했으니 내비 안내도 끝낸다(실제 내비는 차가 안 움직여서 스스로 끝나지 않음)
                            break
                        }
                        delay(POLL_MS)
                    }
                }
            },
            onFailed = { _events.tryEmit(NaviEvent.Error("데모 이동 값을 읽지 못했어요")) },
        )
    }

    private fun stopSim() {
        simOn = false
        sim?.cancel()
        sim = null
        helper?.release()
        helper = null
    }

    override fun start() = real.start()

    override fun requestRoute(dest: Place, vias: List<Place>) {
        stopSim()
        real.requestRoute(dest, vias)
    }

    override fun addWaypoint(via: Place, position: Int) = real.addWaypoint(via, position)
    override fun removeWaypoint(position: Int) = real.removeWaypoint(position)

    override fun cancelRoute() {
        stopSim()
        real.cancelRoute()
    }

    override fun openSearchScreen(keyword: String) = real.openSearchScreen(keyword)

    override fun release() {
        stopSim()
        relay.cancel()
        real.release()
    }

    private companion object {
        const val POLL_MS = 1000L
        const val RESPONSE_WAIT_MS = 300L
    }
}
