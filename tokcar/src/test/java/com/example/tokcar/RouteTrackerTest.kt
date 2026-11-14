package com.example.tokcar

import com.example.tokcar.navi.RouteTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 에뮬레이터 로그(2026-11-09)에서 확인한 신호 순서를 그대로 재현한다. */
class RouteTrackerTest {
    private var t = 0L
    private val tracker = RouteTracker { t }

    /** 미리보기(안내 시작 전 카운트) 중에 X를 누르면 요청 2.4초 뒤 취소 신호가 온다: 끝난 것으로 알려야 한다. */
    @Test
    fun cancelDuringPreviewIsReported() {
        tracker.requested()
        t += 2_444
        assertTrue("onRouteCancelled", tracker.cancelSignal())
        assertFalse("2ms 뒤 FREE_DRIVE는 같은 끝남이라 한 번만", tracker.cancelSignal())
        assertFalse(tracker.active)
        assertFalse(tracker.awaitingStart)
    }

    /** 안내 중 새 경로를 요청하면 20ms 뒤 이전 경로 취소 신호가 먼저 온다: 끝난 것이 아니다. 몇 초 뒤 새 경로가 시작된다. */
    @Test
    fun cancelOfThePreviousRouteRightAfterANewRequestIsIgnored() {
        tracker.requested(); tracker.started()    // 집으로 안내 중
        t += 13_000
        tracker.requested()    // 엄마 위치로 새 요청
        t += 19
        assertFalse("onRouteCancelled(이전 경로 교체)", tracker.cancelSignal())
        t += 3
        assertFalse("FREE_DRIVE", tracker.cancelSignal())
        assertTrue("아직 새 경로를 기다리는 중", tracker.awaitingStart)
        t += 5_768
        tracker.started()    // 새 경로 onRouteStarted
        assertTrue(tracker.active)
        assertFalse(tracker.awaitingStart)
    }

    /** 안내가 시작된 뒤 [안내 종료]를 누르면 취소 신호가 와서 끝남으로 알린다. */
    @Test
    fun cancelWhileGuidingIsReportedOnce() {
        tracker.requested(); t += 5_800; tracker.started()
        t += 7_800
        assertTrue(tracker.cancelSignal())
        assertFalse(tracker.cancelSignal())
    }

    /** 앱이 직접 끝내면(취소) 이어서 오는 내비의 취소 신호는 다시 알리지 않는다. */
    @Test
    fun cancelSignalsAfterTheAppEndedTheRouteAreIgnored() {
        tracker.requested(); tracker.started()
        tracker.appEnded()
        t += 50
        assertFalse(tracker.cancelSignal())
    }

    @Test
    fun signalsWithoutAnyRouteAreIgnored() {
        assertFalse(tracker.cancelSignal())
        assertFalse(tracker.errorSignal())
    }

    /** 경로 요청이 거절되면(예: 목적지가 너무 가까움) 미리보기는 시작되지 못한 것이다. */
    @Test
    fun errorWhilePreviewingEndsThePreview() {
        tracker.requested()
        t += 300
        assertTrue(tracker.errorSignal())
        assertFalse(tracker.awaitingStart)
        t += 10
        assertFalse(tracker.errorSignal())
    }

    @Test
    fun errorWhileGuidingDoesNotEndTheRoute() {
        tracker.requested(); tracker.started()
        assertFalse(tracker.errorSignal())
        assertTrue(tracker.active)
    }

    /** 앱을 켜기 전부터 하던 안내는 처음 한 번만 이어받는다. */
    @Test
    fun existingRouteIsAdoptedOnlyOnce() {
        assertTrue(tracker.adopted())
        assertFalse(tracker.adopted())
        t += 8_000
        assertTrue("이어받은 안내도 취소되면 끝남", tracker.cancelSignal())
    }

    /** 내비 앱에서 목적지를 바꾸면 FREE_DRIVE 약 2.3~2.5초 뒤 새 경로가 시작된다(2026-11-13 로그): 끝이 아니라 목적지 변경이다. */
    @Test
    fun destinationChangedInTheNaviAppIsNotAnEnd() {
        tracker.requested(); tracker.started()
        t += 30_000
        assertTrue("FREE_DRIVE: 새 경로를 기다림", tracker.beginEnding())
        assertFalse("2ms 뒤 onRouteCancelled는 같은 끝남", tracker.beginEnding())
        assertTrue(tracker.ending)
        t += 2_300
        tracker.started()    // 새 경로 onRouteStarted
        assertFalse(tracker.ending)
        assertFalse("기다림이 끝나는 시각에 확인해도 끝이 아님", tracker.confirmEnd())
        assertTrue(tracker.active)
    }

    /** 2026-11-14 로그: 안내 중 앱에서 경유지를 넣으면 FREE_DRIVE 뒤 8.5초에 ROUTE_DRIVE. 길게 기다리는 구간이어야 하고, 새 안내가 오면 끝이 아니다. */
    @Test
    fun addingAWaypointWhileGuidingIsNotAnEnd() {
        tracker.requested(); tracker.started()
        t += 30_000
        tracker.rerouting()
        t += 50    // 바로 FREE_DRIVE
        assertTrue("경유지를 바꾼 직후", tracker.recentlyRerouted)
        assertTrue(tracker.beginEnding())
        t += 8_500
        assertTrue("6초 기본 기다림보다 긴 8.5초", 8_500 > RouteTracker.ENDING_WAIT_MS && 8_500 < RouteTracker.REROUTE_WAIT_MS)
        tracker.started()    // ROUTE_DRIVE
        assertFalse(tracker.confirmEnd())
        assertTrue(tracker.active)
        assertFalse("새 안내가 시작되면 경유지 표시는 지움", tracker.recentlyRerouted)
        t += RouteTracker.REROUTE_WINDOW_MS
        tracker.rerouting(); t += RouteTracker.REROUTE_WINDOW_MS
        assertFalse("경유지를 바꾸고 한참 뒤의 취소는 보통 규칙(6초)", tracker.recentlyRerouted)
    }

    /** 내비에서 안내를 끝냈다면 기다려도 새 경로가 없다: 기다림이 끝나면 끝으로 확정한다(한 번만). */
    @Test
    fun cancelInTheNaviAppIsConfirmedWhenNoNewRouteComes() {
        tracker.requested(); tracker.started()
        t += 30_000
        assertTrue(tracker.beginEnding())
        t += RouteTracker.ENDING_WAIT_MS
        assertTrue(tracker.confirmEnd())
        assertFalse(tracker.active)
        assertFalse(tracker.confirmEnd())
    }

    /** 앱이 경로를 요청하는 중이거나 안내가 없으면 기다림은 시작되지 않는다(앱이 시킨 경로 교체는 유예 규칙이 맡음). */
    @Test
    fun endingWaitOnlyAppliesToAGuidingRoute() {
        assertFalse(tracker.beginEnding())
        tracker.requested()
        assertFalse(tracker.beginEnding())
        tracker.started()
        tracker.requested()    // 앱이 새 경로를 요청
        assertFalse(tracker.beginEnding())
    }

    /** 기다리는 중에 앱이 직접 끝내거나 새 경로를 요청하면 기다림은 취소된다. */
    @Test
    fun appActionsCancelTheEndingWait() {
        tracker.requested(); tracker.started()
        assertTrue(tracker.beginEnding())
        tracker.appEnded()
        assertFalse(tracker.confirmEnd())
        tracker.requested(); tracker.started()
        assertTrue(tracker.beginEnding())
        tracker.requested()
        assertFalse(tracker.confirmEnd())
    }
}
