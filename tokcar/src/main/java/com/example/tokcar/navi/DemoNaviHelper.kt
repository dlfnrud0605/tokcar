package com.example.tokcar.navi

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject

/**
 * 시연용 가짜 NaviHelper.
 *
 * 에뮬레이터의 내비 앱은 GPS를 받지 못해서 남은 시간·거리가 고정이다.
 * 그래서 assets의 값 목록(eta_timeline_commute.json)을 시계에 맞춰 재생하고,
 * 앱이 get...()을 부르면 **SDK와 같은 타입**(DestinationInfo, CurrentLocationInfo)으로
 * **같은 콜백**(NaviHelperEventListener)에 응답한다. (진짜 NaviHelper와 같은 요청-응답 방식)
 *
 * @param speedFactor 재생 배속. 1이면 실시간, 20이면 20배 빠르게(18분 경로를 약 1분에).
 *
 * 진짜 NaviHelper와 같은 이름의 메서드(initialize, addListener, get...Info, release)를 가진다.
 * 앱은 "데모면 이 클래스, 아니면 진짜 NaviHelper"를 한 곳에서 골라 쓴다.
 */
class DemoNaviHelper(
    private val context: Context,
    private val speedFactor: Float = 1f,
    private val assetPath: String = COMMUTE_TIMELINE,
) {

    // 값 목록의 한 줄. SDK 타입 그대로 들고 있다가 요청이 오면 돌려준다.
    private class Sample(val dest: DestinationInfo, val loc: CurrentLocationInfo)

    private var samples: List<Sample> = emptyList()
    private val listeners = mutableListOf<NaviHelperEventListener>()
    private var startedAtMs = 0L
    private val handler = Handler(Looper.getMainLooper())

    fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) {
        // 실제 SDK도 연결 결과가 비동기로 오므로 handler.post 로 한 박자 늦춰서 부른다.
        try {
            samples = loadSamples()
            startedAtMs = SystemClock.elapsedRealtime()
            handler.post { onConnected() }
        } catch (e: Exception) {
            handler.post { onFailed(e) }
        }
    }

    fun addListener(listener: NaviHelperEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: NaviHelperEventListener) {
        listeners.remove(listener)
    }

    fun getDestinationInfo() {
        val sample = currentSample() ?: return
        // 요청한 순간의 값을 약간 뒤에 돌려준다. (실제 SDK 응답이 IPC로 오는 지연을 흉내 냄)
        handler.postDelayed({ listeners.toList().forEach { it.onDestinationInfo(sample.dest) } }, RESPONSE_DELAY_MS)
    }

    fun getCurrentLocationInfo() {
        val sample = currentSample() ?: return
        handler.postDelayed({ listeners.toList().forEach { it.onCurrentLocationInfo(sample.loc) } }, RESPONSE_DELAY_MS)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        listeners.clear()
    }

    /** 처음으로 되돌려 다시 재생한다. */
    fun restart() {
        startedAtMs = SystemClock.elapsedRealtime()
    }

    /** 지금까지 흐른 시간(초). 배속이 적용된다. */
    fun elapsedSec(): Int =
        ((SystemClock.elapsedRealtime() - startedAtMs) / 1000f * speedFactor).toInt()

    /** 재생이 끝났는가(도착했는가). */
    fun isFinished(): Boolean = samples.isNotEmpty() && elapsedSec() >= samples.lastIndex

    // 지금 시각에 해당하는 줄. 끝을 넘으면 마지막 줄(도착 상태)을 계속 돌려준다.
    private fun currentSample(): Sample? {
        if (samples.isEmpty()) return null
        return samples[elapsedSec().coerceIn(0, samples.lastIndex)]
    }

    private fun loadSamples(): List<Sample> {
        // [Kotlin 메모] `.use { }` 는 Java의 try-with-resources 와 같다. 다 읽으면 파일을 자동으로 닫는다.
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val array = JSONObject(text).getJSONArray("samples")
        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            val d = item.getJSONObject("destinationInfo")
            val l = item.getJSONObject("currentLocationInfo")
            Sample(
                dest = DestinationInfo(
                    latitude = d.getDouble("latitude"),
                    longitude = d.getDouble("longitude"),
                    name = d.getString("name"),
                    address = d.getString("address"),
                    remainDistance = d.getInt("remainDistance"),
                    remainTime = d.getInt("remainTime"),
                    tollFare = d.getInt("tollFare"),
                    poiId = "demo-poi",        // 데모라서 의미 없는 자리값
                    poiSubId = "",
                ),
                loc = CurrentLocationInfo(
                    latitude = l.getDouble("latitude"),
                    longitude = l.getDouble("longitude"),
                    heading = l.getDouble("heading"),
                    address = l.getString("address"),
                    routeId = l.getString("routeId"),
                    currentRouteIndex = l.getInt("currentRouteIndex"),
                ),
            )
        }
    }

    companion object {
        /** 예전 판교 → 수서 직선 경로. */
        const val COMMUTE_TIMELINE = "sim/eta_timeline_commute.json"
        private const val RESPONSE_DELAY_MS = 50L
    }
}
