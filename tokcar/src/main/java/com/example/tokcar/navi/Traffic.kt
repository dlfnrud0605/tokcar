package com.example.tokcar.navi

/**
 * 경로 위 정체 단계(내비 SDK 의 TrafficCongestionType 과 같음): 원활 · 보통 · 서행 · 정체.
 * 알 수 없음(UNKNOWN)은 구간으로 넘기지 않아서 막대의 바탕(회색)이 그대로 보인다.
 */
enum class Congestion { FAST, MODERATE, SLOW, HEAVY }

/** 경로 위 정체 구간. [from]·[to] 는 경로 전체를 0~1로 본 위치(출발 0, 도착 1). */
data class TrafficSegment(val from: Float, val to: Float, val level: Congestion)

/** 내비가 준 정체 구간 한 건: 경로 시작에서 [offsetM] 미터 지점부터 [distanceM] 미터. [level] 이 null 이면 쓰지 않는다. */
data class RawCongestion(val offsetM: Double, val distanceM: Double, val level: Congestion?)

/** 여정 정보의 교통 상황 한 건: 경로 전체 대비 [start]~[end] 위치(비율)의 정체 단계. [level] 이 null(원활·알 수 없음)이면 쓰지 않는다. */
data class RawRatio(val start: Int, val end: Int, val level: Congestion?)

object Traffic {
    /**
     * 여정 정보(TripInfo.summary.trafficConditions)의 비율 구간을 막대용 구간으로 바꾼다.
     * 비율의 단위는 에뮬레이터 로그로 백분율(0~100, 4%씩 25칸으로 옴)임을 확인했다. 혹시 천분율로 오면 대비해서
     * 가장 큰 끝값이 100 이하면 백분율, 그보다 크면 천분율로 본다. 모두 0 이하이면 빈 목록.
     * 바로 이어지는 같은 단계 칸은 하나로 합친다.
     */
    fun fromRatios(items: List<RawRatio>): List<TrafficSegment> {
        val maxEnd = items.maxOfOrNull { it.end } ?: return emptyList()
        if (maxEnd <= 0) return emptyList()
        val base = if (maxEnd <= 100) 100f else 1000f
        val out = mutableListOf<TrafficSegment>()
        var last = 0f
        for (it in items.filter { r -> r.level != null }.sortedBy { r -> r.start }) {
            val from = (it.start / base).coerceIn(0f, 1f).coerceAtLeast(last)
            val to = (it.end / base).coerceIn(0f, 1f)
            if (to - from < MIN_SPAN) continue
            out += TrafficSegment(from, to, it.level!!)
            last = to
        }
        return merge(out)
    }

    /** 이보다 짧은 구간은 막대에서 보이지 않아 버린다(경로 전체의 0.5%). */
    private const val MIN_SPAN = 0.005f

    /** 바로 이어지는 같은 단계 구간을 하나로 합친다(칸마다 따로 그리면 경계에 가는 틈이 보임). */
    private fun merge(segs: List<TrafficSegment>): List<TrafficSegment> {
        val out = mutableListOf<TrafficSegment>()
        for (s in segs) {
            val prev = out.lastOrNull()
            if (prev != null && prev.level == s.level && s.from - prev.to < 1e-4f) out[out.lastIndex] = TrafficSegment(prev.from, s.to, s.level) else out += s
        }
        return out
    }

    /** 구간 목록의 빈 곳(정체 구간이 아닌 곳)을 원활(FAST)로 채운다. 구간은 위치 순이고 서로 겹치지 않아야 한다. */
    private fun fillSmooth(segs: List<TrafficSegment>): List<TrafficSegment> {
        val out = mutableListOf<TrafficSegment>()
        var at = 0f
        for (s in segs) {
            if (s.from - at >= MIN_SPAN) out += TrafficSegment(at, s.from, Congestion.FAST)
            out += s
            at = s.to
        }
        if (1f - at >= MIN_SPAN) out += TrafficSegment(at, 1f, Congestion.FAST)
        return out
    }

    /**
     * 내비가 준 정체 구간(미터)을 막대용 비율 구간으로 바꾼다. 경로 전체 길이 [totalM] 을 모르면(0 이하) 빈 목록.
     * 위치 순으로 정렬하고, 앞 구간과 겹치는 부분은 앞 구간 끝에서부터로 줄인다. 구간 목록에 없는 곳은 원활(FAST)로 채운다.
     */
    fun segments(items: List<RawCongestion>, totalM: Double): List<TrafficSegment> {
        if (totalM <= 0.0) return emptyList()
        val out = mutableListOf<TrafficSegment>()
        var last = 0f
        for (it in items.filter { r -> r.level != null && r.distanceM > 0.0 }.sortedBy { r -> r.offsetM }) {
            val from = (it.offsetM / totalM).toFloat().coerceIn(0f, 1f).coerceAtLeast(last)
            val to = ((it.offsetM + it.distanceM) / totalM).toFloat().coerceIn(0f, 1f)
            if (to - from < MIN_SPAN) continue
            out += TrafficSegment(from, to, it.level!!)
            last = to
        }
        return merge(fillSmooth(out))
    }
}
