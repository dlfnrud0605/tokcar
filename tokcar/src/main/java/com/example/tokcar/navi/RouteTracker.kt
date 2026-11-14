package com.example.tokcar.navi

/**
 * 내비의 취소 신호(onRouteCancelled·FREE_DRIVE)가 "사용자가 안내를 끝냈다"는 뜻인지 가린다.
 * 에뮬레이터 로그(2026-11-09)로 확인한 사실:
 * - 안내 시작 전 미리보기(카운트) 중에 X를 누르면 요청 약 2.4초 뒤 onRouteCancelled + FREE_DRIVE(2ms 간격)가 오고 안내 시작 알림은 없다.
 * - 안내 중에 새 경로를 요청하면 이전 경로가 취소되며 요청 약 20ms 뒤 onRouteCancelled + FREE_DRIVE가 먼저 오고, 몇 초 뒤 새 경로의 시작 알림이 온다.
 * 그래서 앱이 경로를 요청한 직후 [REPLACE_GRACE_MS] 안의 취소 신호는 "이전 경로가 교체된 것"으로 보고 무시하고, 그 뒤의 신호는 끝난 것으로 본다.
 *
 * 에뮬레이터 로그(2026-11-13)로 확인한 사실(내비 앱에서 직접 목적지를 바꿀 때):
 * - 안내 중에 내비에서 목적지를 바꾸면 FREE_DRIVE가 먼저 오고 약 2.3~2.5초 뒤에 새 경로의 onRouteStarted·ROUTE_DRIVE가 온다. 앱이 시킨 것이 아니라 위 유예가 적용되지 않는다.
 * 그래서 시작된 안내가 앱 모르게 취소되면 곧바로 끝으로 보지 않고 [ENDING_WAIT_MS] 동안 새 경로를 기다린다([beginEnding]·[confirmEnd]).
 * 그 안에 새 경로가 시작되면 끝이 아니라 목적지 변경이다.
 *
 * 에뮬레이터 로그(2026-11-14)로 확인한 사실(앱에서 안내 중에 경유지를 넣을 때):
 * - FREE_DRIVE가 오고 약 8.5초 뒤에 ROUTE_DRIVE가 다시 온다(onRouteCancelled·onRouteStarted는 없음). [ENDING_WAIT_MS](6초)보다 길어서 안내 종료로 잘못 보았다.
 * 그래서 앱이 경유지를 바꾼 직후([REROUTE_WINDOW_MS] 안)의 취소 신호는 [REROUTE_WAIT_MS]까지 새 안내를 기다린다([rerouting]·[recentlyRerouted]).
 */
class RouteTracker(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    /** 안내가 진행 중이라고 앱이 아는 상태. */
    var active = false
        private set

    /** 앱이 경로를 요청했고 아직 안내 시작 알림이 오지 않은 상태(미리보기 중). */
    var awaitingStart = false
        private set

    /** 시작된 안내에 취소 신호가 왔고, 새 경로가 이어질지 기다리는 중. */
    var ending = false
        private set

    private var requestedAt = 0L
    private var reroutedAt = 0L

    /** 앱이 안내 중에 경유지를 넣거나 뺐다. 내비가 경로를 다시 잡는 동안 오는 FREE_DRIVE를 종료로 보지 않으려고 기록한다. */
    fun rerouting() {
        reroutedAt = now()
    }

    /** 앱이 경유지를 바꾼 직후인가: 이때 온 취소 신호는 [REROUTE_WAIT_MS]까지 새 안내를 기다린다. */
    val recentlyRerouted: Boolean get() = reroutedAt > 0 && now() - reroutedAt < REROUTE_WINDOW_MS

    /** 앱이 경로를 요청했다. 이전 안내가 있었다면 [active]는 새 경로가 시작될 때까지 그대로 둔다. */
    fun requested() {
        ending = false
        awaitingStart = true
        requestedAt = now()
    }

    /** 안내 시작 알림(onRouteStarted·ROUTE_DRIVE). */
    fun started() {
        ending = false
        reroutedAt = 0L
        awaitingStart = false
        active = true
    }

    /** 앱을 켜기 전부터 하던 안내를 이어받는다. 처음 이어받을 때만 true. */
    fun adopted(): Boolean {
        if (active) return false
        active = true
        return true
    }

    /** 앱이 안내를 끝냈거나(취소) 도착했다. 이어서 오는 취소 신호는 무시한다. */
    fun appEnded() {
        ending = false
        active = false
        awaitingStart = false
    }

    /** 취소 신호가 왔다. true = 안내(또는 미리보기)가 정말 끝났다. 같은 끝남에 신호가 두 번(2ms 간격) 와도 한 번만 true. */
    fun cancelSignal(): Boolean {
        if (!active && !awaitingStart) return false
        if (awaitingStart && now() - requestedAt < REPLACE_GRACE_MS) return false    // 새 경로 요청이 이전 경로를 취소한 신호
        appEnded()
        return true
    }

    /**
     * 시작된 안내(앱이 요청 중이 아닌)에 앱 모르게 취소 신호가 왔다. 새 경로가 이어질지 기다리는 상태로 바꾸고 true.
     * 같은 끝남의 두 번째 신호(onRouteCancelled 와 FREE_DRIVE는 2ms 간격)이거나 해당 상태가 아니면 false.
     */
    fun beginEnding(): Boolean {
        if (!active || awaitingStart || ending) return false
        ending = true
        return true
    }

    /** 기다리는 동안 새 경로가 시작되지 않았다: 정말 끝났다. true = 끝남 확정(한 번만). */
    fun confirmEnd(): Boolean {
        if (!ending) return false
        appEnded()
        return true
    }

    /** 내비가 오류를 알렸다(예: 목적지가 너무 가까움). 미리보기 중이었다면 시작되지 못한 것이므로 끝난 것으로 본다. */
    fun errorSignal(): Boolean {
        if (!awaitingStart) return false
        appEnded()
        return true
    }

    companion object {
        /** 요청 직후 이 시간(ms) 안의 취소 신호는 경로 교체로 본다. 교체는 약 20ms, 사용자 취소는 2초 이상 뒤였다. */
        const val REPLACE_GRACE_MS = 1000L

        /** 내비에서 직접 목적지를 바꾸면 새 경로 시작이 약 2.3~2.5초 뒤였다(5초 넘게 걸린 경우도 있어 여유를 둠). */
        const val ENDING_WAIT_MS = 6000L

        /** 경유지를 넣은 뒤 새 안내 시작은 약 8.5초 뒤였다. 넉넉히 기다린다. */
        const val REROUTE_WAIT_MS = 20_000L

        /** 경유지를 바꾸고 이 시간(ms) 안에 온 취소 신호만 「경로 다시 잡는 중」으로 본다. */
        const val REROUTE_WINDOW_MS = 5_000L
    }
}
