package com.example.tokcar.remote

import com.example.tokcar.core.TokStore
import com.example.tokcar.core.clockText
import com.example.tokcar.data.LocKind
import com.example.tokcar.data.LocMsg
import com.example.tokcar.data.Msg
import com.example.tokcar.data.MyLocMsg
import com.example.tokcar.data.MyMsg
import com.example.tokcar.data.MyRouteMsg
import com.example.tokcar.data.Place
import com.example.tokcar.data.ShareEnd
import com.example.tokcar.data.SysMsg
import com.example.tokcar.data.TextMsg

/**
 * 서버 메시지 → 앱 화면의 메시지. 앱은 사람을 이름으로, 장소를 places 의 id 로 다루므로 그 둘을 이어 준다.
 * [meId] = 나, [names] = 사용자 id → 이름, [others] = 그 방의 나를 뺀 멤버 id, [readUpTo] = 사람마다 읽은 곳.
 */
class RemoteMapper(
    private val meId: Long,
    private val names: Map<Long, String>,
    private val others: List<Long>,
    private val readUpTo: Map<Long, Long>,
) {
    /** 바꾼 메시지와, 그 메시지가 가리키는 장소(앱의 places 에 넣어야 함). */
    data class Out(val msg: Msg, val places: List<Place>)

    fun map(m: SMessage): Out {
        val mine = m.senderId == meId
        val who = m.senderId?.let { names[it] } ?: "가족"
        val time = clockText(m.createdAtMs)
        val total = others.size
        val readBy = if (mine) others.filter { (readUpTo[it] ?: 0L) >= m.id }.mapNotNull { names[it] }.toSet() else emptySet()
        // 남이 보낸 것: 보낸 사람을 뺀 방 사람(나 포함) 중 아직 안 읽은 수
        val unread = if (mine) 0 else (others + meId).count { it != m.senderId && (readUpTo[it] ?: 0L) < m.id }
        return when (m.kind) {
            "TEXT" -> Out(if (mine) MyMsg(m.id, m.text.orEmpty(), total, readBy) else TextMsg(m.id, who, m.text.orEmpty(), unread), emptyList())
            "LOCATION" -> {
                val loc = m.location
                when {
                    mine -> Out(MyLocMsg(m.id, loc?.addr?.ifBlank { loc.name }.orEmpty(), time, total, readBy), emptyList())
                    // 보낸 지 1년이 지나 좌표가 지워진 위치
                    loc == null -> Out(TextMsg(m.id, who, "위치를 보냈어요", unread), emptyList())
                    else -> placeOf(loc).let { p -> Out(LocMsg(m.id, who, LocKind.HERE, p.id, time = time, unread = unread), listOf(p)) }
                }
            }
            "ROUTE" -> {
                val r = m.route ?: return Out(SysMsg(m.id, "경로 공유"), emptyList())
                val dest = placeOf(r.dest)
                val live = r.live
                val end = when (r.state) {
                    "ARRIVED" -> ShareEnd.ARRIVED
                    "STOPPED" -> ShareEnd.STOPPED
                    else -> null
                }
                val min = live?.let { (it.remainSec + 59) / 60 } ?: 0
                val pct = if (end == ShareEnd.ARRIVED) 100 else live?.pct ?: 0
                val eta = live?.let { clockText(it.etaAtMs) }.orEmpty()
                val msg = if (mine) {
                    MyRouteMsg(m.id, dest.title, r.from.addr, dest.fullAddr, min, pct, eta, state = end, fill = end == null && live == null, total = total, readBy = readBy)
                } else {
                    LocMsg(m.id, who, LocKind.DEST, dest.id, r.from.addr, min, pct, eta, time = time, end = end, unread = unread)
                }
                Out(msg, listOf(dest))
            }
            else -> Out(SysMsg(m.id, m.text.orEmpty()), emptyList())
        }
    }

    companion object {
        /** 서버 장소 → 앱 장소. id 는 좌표로 만들어 같은 곳이면 같은 id(내비 목적지와 같은 규칙). */
        fun placeOf(p: SPlace): Place =
            Place(TokStore.externalPlaceId(p.lat, p.lon), p.name.ifBlank { p.addr }, p.addr, p.lat, p.lon)

        /**
         * 서버 「이 방에서 간 곳」 목록(최신순) → 앱 장소. id 는 좌표(소수 넷째 자리)로 만들어서, 같은 칸에 두 곳이 있으면
         * (「집」과 내비가 같은 자리에 주소 글자 이름으로 알린 곳) 맨 앞(최신)만 남긴다. 그냥 합치면 뒤쪽(오래된 쪽)이 앞의 이름을 덮어써서
         * 「집」이 주소 글자 이름으로 바뀐다.
         */
        fun placesOf(list: List<SPlace>): List<Place> = list.map(::placeOf).distinctBy(Place::id)

        /** 사용자 · 방 id 로 정하는 얼굴 색(사진이 없을 때). 같은 사람은 늘 같은 색. */
        fun colorOf(id: Long): Long = TokStore.ROOM_COLORS[(id % TokStore.ROOM_COLORS.size).toInt()]
    }
}
