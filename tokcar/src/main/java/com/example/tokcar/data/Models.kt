package com.example.tokcar.data

/** 장소. 좌표는 예시(더미)이고 실제 도로·건물 위치가 아니다. */
data class Place(
    val id: String,
    val name: String,
    val addr: String,
    val lat: Double,
    val lon: Double,
    /** 카드·경유 표시에 쓰는 짧은 이름(없으면 name). */
    val card: String? = null,
) {
    val short: String get() = card ?: name

    /**
     * 작은 글씨로 보이는 전체 주소(도로명·번호까지). 내비가 이름 칸에 전체 주소를 주고 주소 칸에는 "진주시 가좌동"처럼 짧게만 주는 경우가 있어서,
     * 이름이 주소처럼 생겼고 주소 칸보다 길면 이름을 전체 주소로 본다.
     */
    val fullAddr: String
        get() = short.let { base -> if (com.example.tokcar.core.Address.looksLikeAddress(base) && base.length > addr.length) base else addr.ifBlank { base } }
            .let { com.example.tokcar.core.Address.withoutProvince(it) }   // 도·특별시 이름은 뺌("경상남도 진주시 …" -> "진주시 …")

    /** 화면의 큰 글씨 이름. 이름이 주소처럼 생겼으면 "구(시) 동"까지만(전체 주소는 작은 글씨로 따로 보임). */
    val title: String
        get() = short.let { base -> if (base.isBlank() || com.example.tokcar.core.Address.looksLikeAddress(base)) com.example.tokcar.core.Address.area(fullAddr) else base }
}

data class Friend(
    val id: String,
    val tel: String,
    val name: String,
    val ini: String,
    /** 얼굴 색(ARGB). 연한 톤으로 바꿔 쓴다. */
    val color: Long,
    val status: String,
    val placeId: String?,
)

data class Room(
    val id: String,
    val name: String,
    val placeId: String?,
    val color: Long,
    val ini: String,
    val members: List<String>,
    /** 서버가 알려 준 안 읽은 수(방을 열면 0). */
    val unread: Int,
    val time: String,
) {
    /** 나를 포함한 인원수(채팅 목록과 같은 표기). */
    val headCount: Int get() = members.size + 1
}

enum class LocKind { HERE, DEST }

/** 대화방의 한 줄. */
sealed interface Msg {
    val id: Long
}

/** 다른 사람이 보낸 글. [unread] = 이 글을 아직 안 읽은 사람 수(보낸 사람 빼고, 카카오톡처럼 옆에 보임). */
data class TextMsg(override val id: Long, val who: String, val text: String, val unread: Int = 0) : Msg

/** 내가 보낸 것. 아직 안 읽은 사람 수를 옆에 보인다(카카오톡 방식). */
sealed interface MineMsg : Msg {
    val total: Int
    val readBy: Set<String>
    val unreadCount: Int get() = total - readBy.size
    fun withRead(who: String): MineMsg
}

/** 내가 보낸 글. */
data class MyMsg(override val id: Long, val text: String, override val total: Int, override val readBy: Set<String> = emptySet()) : MineMsg {
    override fun withRead(who: String) = copy(readBy = readBy + who)
}

/** 내가 보낸 내 위치(한 번 보낸 위치 공유). name = 구·동 단위 주소. */
data class MyLocMsg(
    override val id: Long,
    val name: String,
    val time: String,
    override val total: Int,
    override val readBy: Set<String> = emptySet(),
) : MineMsg {
    override fun withRead(who: String) = copy(readBy = readBy + who)
}

/** 경로 공유 카드가 끝난 이유. null 이면 진행 중. */
enum class ShareEnd { ARRIVED, STOPPED }

/**
 * 내가 보낸 경로 공유 카드(공유 시작·목적지 변경 때 방에 남는 카드). 새 메시지를 더 보내지 않고 이 카드가 바뀐다:
 * 목적지가 바뀌면 값이 새 목적지로, 도착하면 "도착", 공유를 멈추면 "공유 종료"로.
 * [fill] = 값(남은 시간 등)을 아직 모름: 내비가 첫 진행값을 알려 주면 채운다.
 */
data class MyRouteMsg(
    override val id: Long,
    val name: String,
    val from: String,
    val to: String,
    val min: Int,
    val pct: Int,
    val eta: String,
    val state: ShareEnd? = null,
    val fill: Boolean = false,
    override val total: Int,
    override val readBy: Set<String> = emptySet(),
) : MineMsg {
    override fun withRead(who: String) = copy(readBy = readBy + who)
}

/** 시스템 안내(방 만들기, 카드로 바꿀 수 없을 때의 도착·공유 멈춤). */
data class SysMsg(override val id: Long, val text: String) : Msg

/**
 * 가족이 보낸 위치 카드. kind = HERE 는 한 번 보낸 현재 위치, DEST 는 경로 공유(목적지로 가는 중).
 * 경로 공유는 출발([from], 구·동), 남은 분([min]), 진행률([pct], %), 도착 예정([eta])을 함께 받는다.
 * [end] = 경로 공유가 끝났으면 도착 · 공유 종료(서버가 알려 줌), 가는 중이면 null.
 */
data class LocMsg(
    override val id: Long,
    val who: String,
    val kind: LocKind,
    val placeId: String,
    val from: String = "",
    val min: Int = 0,
    val pct: Int = 0,
    val eta: String = "",
    val time: String = "",
    val end: ShareEnd? = null,
    /** 이 카드를 아직 안 읽은 사람 수(보낸 사람 빼고). */
    val unread: Int = 0,
) : Msg

/** 가족 폰에서 위치를 보내는 상황을 흉내 내는 데이터. */
data class IncomingSpec(
    val roomId: String,
    val who: String,
    val kind: LocKind,
    val placeId: String,
    val from: String = "",
    val min: Int = 0,
    val pct: Int = 0,
    val eta: String = "",
)

/** 가족이 보낸 글을 흉내 내는 데이터. */
data class IncomingText(val roomId: String, val who: String, val text: String)

/** 서버 대신 쓰는 더미 데이터(assets/dummy/seed.json). */
data class Seed(
    val myName: String,
    val myPhone: String,
    /** 내 현재 위치 주소(시안: 동 단위). 실제 내비는 getCurrentLocationInfo 가 주는 값을 쓴다. */
    val myAddr: String,
    val places: Map<String, Place>,
    val favs: List<String>,
    val navRecent: List<String>,
    val navResults: List<String>,
    val friends: List<Friend>,
    val phonebook: List<Friend>,
    val rooms: List<Room>,
    /** 방마다 처음 들어 있는 대화(보낸 사람, 내용). */
    val roomMsgs: Map<String, List<Pair<String, String>>>,
    val roomLast: Map<String, String>,
    val histories: Map<String, List<String>>,
    val incoming: Map<String, IncomingSpec>,
    /** 가족이 보내는 글(개발용 버튼으로 받아 본다). */
    val incomingText: Map<String, IncomingText>,
    /** 상대가 내 글을 읽는 시각(ms). null이면 계속 안 읽음. */
    val readDelaysMs: Map<String, Long?>,
)

/** 친구 추가: 전화번호 검색 결과. */
sealed interface PhoneLookup {
    data object Self : PhoneLookup
    data class Already(val friend: Friend) : PhoneLookup
    data class Found(val friend: Friend, val tel: String) : PhoneLookup
    data class NotRegistered(val tel: String) : PhoneLookup
}
