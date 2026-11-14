package com.example.tokcar

import com.example.tokcar.navi.Congestion
import com.example.tokcar.navi.RawCongestion
import com.example.tokcar.navi.RawRatio
import com.example.tokcar.navi.Traffic
import com.example.tokcar.navi.TrafficSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficTest {
    private fun seg(a: Float, b: Float, l: Congestion) = TrafficSegment(a, b, l)

    @Test
    fun metersBecomeRatiosAndGapsAreSmooth() {
        val segs = Traffic.segments(
            listOf(
                RawCongestion(1000.0, 500.0, Congestion.SLOW),      // 5km 중 1.0~1.5km
                RawCongestion(3000.0, 1000.0, Congestion.HEAVY),    // 3.0~4.0km
            ),
            5000.0,
        )
        assertEquals(
            listOf(
                seg(0f, 0.2f, Congestion.FAST), seg(0.2f, 0.3f, Congestion.SLOW), seg(0.3f, 0.6f, Congestion.FAST),
                seg(0.6f, 0.8f, Congestion.HEAVY), seg(0.8f, 1f, Congestion.FAST),
            ),
            segs,
        )
    }

    @Test
    fun noCongestionMeansWholeRouteIsSmooth() {
        assertEquals(listOf(seg(0f, 1f, Congestion.FAST)), Traffic.segments(emptyList(), 1000.0))
    }

    @Test
    fun skipsUnusedLevelsAndTinyOrEmptySpans() {
        val segs = Traffic.segments(
            listOf(
                RawCongestion(0.0, 400.0, null),                       // 알 수 없음은 안 씀(그 자리는 원활로 채워짐)
                RawCongestion(500.0, 0.0, Congestion.HEAVY),           // 길이 0
                RawCongestion(600.0, 3.0, Congestion.SLOW),            // 3m/10km = 0.03%: 막대에서 안 보임
                RawCongestion(2000.0, 1000.0, Congestion.MODERATE),
            ),
            10_000.0,
        )
        assertEquals(listOf(seg(0f, 0.2f, Congestion.FAST), seg(0.2f, 0.3f, Congestion.MODERATE), seg(0.3f, 1f, Congestion.FAST)), segs)
    }

    @Test
    fun sortsClipsToTheRouteAndTrimsOverlap() {
        val segs = Traffic.segments(
            listOf(
                RawCongestion(900.0, 400.0, Congestion.HEAVY),     // 뒤에 오지만 먼저 정렬됨: 0.9~1.0 로 잘림(경로 끝 넘김)
                RawCongestion(300.0, 400.0, Congestion.SLOW),      // 0.3~0.7
                RawCongestion(600.0, 200.0, Congestion.MODERATE),  // 0.6~0.8 중 앞 구간(~0.7)과 겹침 → 0.7~0.8
            ),
            1000.0,
        )
        assertEquals(
            listOf(
                seg(0f, 0.3f, Congestion.FAST), seg(0.3f, 0.7f, Congestion.SLOW), seg(0.7f, 0.8f, Congestion.MODERATE),
                seg(0.8f, 0.9f, Congestion.FAST), seg(0.9f, 1f, Congestion.HEAVY),
            ),
            segs,
        )
    }

    @Test
    fun unknownRouteLengthGivesNothing() {
        assertTrue(Traffic.segments(listOf(RawCongestion(0.0, 100.0, Congestion.HEAVY)), 0.0).isEmpty())
    }

    @Test
    fun tripRatiosInPercentBecomeSegmentsAndUnknownStaysEmpty() {
        val segs = Traffic.fromRatios(
            listOf(
                RawRatio(0, 20, Congestion.FAST),
                RawRatio(20, 45, Congestion.HEAVY),
                RawRatio(45, 60, null),                         // 알 수 없음: 그 자리는 비워 둠(막대 바탕 회색)
                RawRatio(60, 100, Congestion.SLOW),
            ),
        )
        assertEquals(listOf(seg(0f, 0.2f, Congestion.FAST), seg(0.2f, 0.45f, Congestion.HEAVY), seg(0.6f, 1f, Congestion.SLOW)), segs)
    }

    @Test
    fun tripRatiosInPermilleAreDetectedByTheLargestEnd() {
        val segs = Traffic.fromRatios(listOf(RawRatio(0, 400, Congestion.HEAVY), RawRatio(400, 1000, Congestion.FAST)))
        assertEquals(listOf(seg(0f, 0.4f, Congestion.HEAVY), seg(0.4f, 1f, Congestion.FAST)), segs)
    }

    @Test
    fun tripRatiosEmptyOrZeroGiveNothing() {
        assertTrue(Traffic.fromRatios(emptyList()).isEmpty())
        assertTrue(Traffic.fromRatios(listOf(RawRatio(0, 0, Congestion.HEAVY))).isEmpty())
    }

    @Test
    fun adjacentSameLevelSegmentsAreMerged() {
        // 실제 여정 정보는 4%씩 25칸으로 옴: 같은 단계가 이어지면 한 덩어리로
        val segs = Traffic.fromRatios(
            listOf(
                RawRatio(0, 4, Congestion.SLOW), RawRatio(4, 8, Congestion.SLOW), RawRatio(8, 12, Congestion.HEAVY),
                RawRatio(88, 92, Congestion.HEAVY), RawRatio(92, 96, Congestion.HEAVY), RawRatio(96, 100, Congestion.HEAVY),
            ),
        )
        assertEquals(listOf(seg(0f, 0.08f, Congestion.SLOW), seg(0.08f, 0.12f, Congestion.HEAVY), seg(0.88f, 1f, Congestion.HEAVY)), segs)
    }
}
