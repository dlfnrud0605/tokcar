package com.example.tokcar.core

import com.example.tokcar.data.Friend
import com.example.tokcar.data.LocKind
import com.example.tokcar.data.LocMsg
import com.example.tokcar.data.Msg
import com.example.tokcar.data.MyLocMsg
import com.example.tokcar.data.MyMsg
import com.example.tokcar.data.MyRouteMsg
import com.example.tokcar.data.PhoneLookup
import com.example.tokcar.data.Place
import com.example.tokcar.data.Room
import com.example.tokcar.data.ShareEnd
import com.example.tokcar.data.SysMsg
import com.example.tokcar.data.TextMsg
import com.example.tokcar.navi.TrafficSegment

/** 아래 탭바 순서: 친구 · 채팅 · 설정. */
enum class Tab { FRIENDS, CHAT, SETTINGS }

// ───────────────────────── 회원가입·로그인(시안의 loginHTML) ─────────────────────────

/**
 * 처음 화면 -> (회원가입) 번호 -> 인증번호 -> 아이디·비밀번호 -> 이름 -> 약관
 *          -> (로그인) 아이디·비밀번호
 *          -> (아이디 찾기) 번호 -> 인증번호 -> 찾은 아이디
 *          -> (비밀번호 찾기) 아이디 -> 인증번호 -> 새 비밀번호
 */
enum class LoginStep { WELCOME, IDPW, PHONE, RID, CODE, ACCOUNT, NEWPW, FDONE, PROFILE, TERMS }

enum class LoginMode { SIGNUP, LOGIN, FINDID, RESETPW }

/** 화면에 보이는 안내: 이미 가입된 번호 / 가입 안 된 번호 / 아이디·비밀번호가 틀림 / 없는 아이디. */
enum class LoginErr { KNOWN, UNKNOWN, BAD, NOID }

/** 아이디 중복 확인 결과. [value] 는 확인한 아이디(그 뒤에 고치면 다시 확인해야 함). */
data class IdCheck(val value: String, val ok: Boolean)

data class Agree(val tos: Boolean = false, val priv: Boolean = false, val loc: Boolean = false, val mkt: Boolean = false) {
    val all: Boolean get() = tos && priv && loc && mkt
    val required: Boolean get() = tos && priv && loc
}

data class LoginState(
    val mode: LoginMode? = null,
    val step: LoginStep = LoginStep.WELCOME,
    val country: String = "KR",
    /** 입력한 번호(하이픈 포함, 입력 모양 그대로). */
    val phone: String = "",
    val code: String = "",
    val name: String = "",
    val id: String = "",
    val pw: String = "",
    val pw2: String = "",
    val showPw: Boolean = false,
    val idCheck: IdCheck? = null,
    /** 비밀번호 찾기에서 입력한 아이디. */
    val rid: String = "",
    val found: List<String> = emptyList(),
    val agree: Agree = Agree(),
    val err: LoginErr? = null,
    /** 로그인에서 틀렸을 때 남은 시도 횟수. */
    val left: Int = 0,
    val busy: Boolean = false,
)

/** 아이디·비밀번호 규칙(시안과 같음). */
object Rules {
    val ID = Regex("^[a-z][a-z0-9]{3,15}$")

    data class Pw(val len: Boolean, val mix: Boolean, val notId: Boolean) {
        val ok: Boolean get() = len && mix && notId
    }

    fun pw(pw: String, id: String): Pw = Pw(
        len = pw.length in 8..20,
        mix = pw.any { it.isLetter() && it.code < 128 } && pw.any { it.isDigit() },
        notId = id.isEmpty() || !pw.lowercase().contains(id.lowercase()),
    )

    /** 찾은 아이디를 가려서 보인다: papa -> pa** */
    fun maskId(id: String): String = id.take(2) + "*".repeat((id.length - 2).coerceAtLeast(0))
}

// ───────────────────────── 팝업 ─────────────────────────

/** 안내 중에 다른 곳으로 바꾸는 요청이 어디서 왔는가: 가족이 보낸 카드의 [안내] / 방 카드·대화방 위쪽의 [안내]. */
enum class RerouteAct { GO, CARD }

sealed interface Modal {
    data class FriendProfile(val friendId: String) : Modal

    /** 친구 추가. [tel] = 입력 중인 번호(하이픈 포함), [res] = 번호 검색 결과. */
    data class AddFriend(val tel: String = "", val res: PhoneLookup? = null) : Modal

    data class NewRoom(val selected: List<String> = emptyList(), val name: String = "") : Modal

    /** 목적지. [results] = (데모) 내비 검색 결과를 보이는 중, [query] = 말한 검색어. */
    data class Dest(val roomId: String, val results: Boolean = false, val query: String = "") : Modal

    data class Reroute(val act: RerouteAct, val roomId: String, val placeId: String) : Modal

    /** 공유할 방 고르기. */
    data object Pick : Modal

    /** 국가 선택(회원가입·아이디 찾기). */
    data object Country : Modal

    /** 대화상대 초대(대화방 서랍의 [초대하기]). */
    data class Invite(val selected: List<String> = emptyList()) : Modal

    /** 채팅방 나가기 확인. */
    data object Leave : Modal
}

enum class NavState { IDLE, PREVIEW, GUIDING, ARRIVED }

data class NavUi(
    val state: NavState = NavState.IDLE,
    val placeId: String? = null,
    val progress: Float = 0f,
    val remainSec: Int = 0,
    val remainMeters: Int = 0,
)

data class Via(val placeId: String, val roomId: String)

/** 공유 시작을 기다리는 중(15초 카운트). */
data class Pending(val roomId: String, val remain: Int)

/** 아래 파란 카드(목록은 탭바 위, 대화방은 입력 바 위). */
sealed interface Dock {
    /** 대화방: 다른 방에 공유 중이라 이 방으로 옮길지 묻는다. 「○○로 안내 중 / △△에 공유 중 [이 방으로]」 */
    data class Move(val title: String, val fromName: String, val roomId: String) : Dock

    data class Counting(val title: String, val roomName: String, val remain: Int, val total: Int) : Dock
    data class Sharing(val title: String, val roomName: String) : Dock

    /** 대화방: 안내 중인데 이 방에 공유가 없을 때 "이 방에 공유할까요?". */
    data class AskRoom(val title: String, val roomId: String) : Dock

    /** 목록: 안내 중인데 어느 방에도 공유가 없을 때 "공유할 방을 고르세요". */
    data class AskList(val title: String) : Dock
}

/** 아직 글을 안 보낸 새 방(초안). 목록에 나오지 않고 첫 메시지를 보내야 저장된다. [sysText] = 저장될 때 맨 앞에 남는 안내 글. */
data class Draft(val room: Room, val sysText: String?)

data class UiState(
    val places: Map<String, Place>,
    val signedIn: Boolean = true,
    val login: LoginState = LoginState(),
    val myName: String,
    val myId: String = "papa",
    val myPhone: String,
    /** 설정에 보이는 가려진 번호(예: "+82 10-****-5678"). 비어 있으면 [myPhone]을 가려 보인다. */
    val phoneMask: String = "",
    val myAddr: String = "",
    val tab: Tab = Tab.CHAT,
    val inChat: Boolean = false,
    val roomId: String? = null,
    val modal: Modal? = null,
    /** 대화방 오른쪽 위 메뉴의 대화상대 서랍. */
    val drawer: Boolean = false,

    val rooms: List<Room>,
    val drafts: Map<String, Draft> = emptyMap(),
    val friends: List<Friend>,
    val msgs: Map<String, List<Msg>>,
    val lastText: Map<String, String>,
    val readRooms: Set<String> = emptySet(),
    val newIn: Map<String, Int> = emptyMap(),
    val at: Map<String, String> = emptyMap(),
    val destByRoom: Map<String, String> = emptyMap(),
    val hist: Map<String, List<String>>,
    val favs: List<String>,
    val recent: List<String>,
    val navResults: List<String>,
    val draft: Map<String, String> = emptyMap(),

    val act: Map<String, Int> = emptyMap(),
    val actN: Int = 100,
    val tmpTop: String? = null,

    val nav: NavUi = NavUi(),
    /** 지금 경로의 정체 구간(경로 전체를 0~1로 본 위치). 경로 공유 카드의 진행 막대에 색으로 보인다. null = 정체 정보 없음(막대는 회색), 빈 목록 = 정체 없음(전체 원활) */
    val traffic: List<TrafficSegment>? = null,
    val vias: List<Via> = emptyList(),
    val pending: Pending? = null,
    val sharing: String? = null,
    val declined: Map<String, String> = emptyMap(),
    /** 방 안에서 [공유 취소]한 방: 방을 나갔다 다시 들어오면 "이 방에 공유할까요?"를 다시 묻는다. */
    val askAgain: Set<String> = emptySet(),
    val listDismissed: String? = null,
    val plusOpen: Boolean = false,
    val listening: Boolean = false,
    /** 목적지 모달의 [말하기]로 내비 검색을 기다리는 중(실제 내비). */
    val navSearching: Boolean = false,
    val toast: String? = null,
    val gear: Gear = Gear.UNKNOWN,

    val naviReal: Boolean = false,
    val naviLabel: String = "",
    val maxWaypoints: Int = 5,
    val shareWait: Int = 15,
    /** 시연용: 실제 내비에 연결하되 남은 시간 · 진행률은 움직이는 데모 이동 값을 쓴다(설정의 개발용 스위치. 「실제 내비 쓰기」가 켜져 있을 때 적용). */
    val demoMove: Boolean = false,
) {
    val navActive: Boolean get() = nav.state == NavState.PREVIEW || nav.state == NavState.GUIDING

    /** 차가 D(앞으로 가는 기어)에 있을 때만 주행 중: 글 입력이 막히고 말하기만 남는다. */
    val driving: Boolean get() = gear == Gear.DRIVE

    /** 저장된 방과 초안 방 모두에서 찾는다. */
    fun room(id: String?): Room? = rooms.firstOrNull { it.id == id } ?: drafts[id]?.room
    fun isDraft(id: String?): Boolean = id != null && drafts.containsKey(id)
    fun friend(id: String): Friend? = friends.firstOrNull { it.id == id }
    fun place(id: String?): Place? = id?.let { places[it] }

    fun destId(r: Room): String? = destByRoom[r.id] ?: r.placeId
    fun destOf(r: Room): Place? = place(destId(r))
    fun histOf(r: Room): List<String> = hist[r.id] ?: listOfNotNull(r.placeId)

    fun unreadOf(r: Room): Int = (if (r.id in readRooms) 0 else r.unread) + (newIn[r.id] ?: 0)
    val totalUnread: Int get() = rooms.sumOf { unreadOf(it) }
    fun timeOf(r: Room): String = at[r.id] ?: r.time
    fun lastOf(r: Room): String = lastText[r.id]?.takeIf { it.isNotEmpty() } ?: "대화를 시작해 보세요"

    fun sameTrip(placeId: String): Boolean = navActive && nav.placeId == placeId

    fun lit(r: Room): Boolean = sharing == r.id || pending?.roomId == r.id || (navActive && nav.placeId == destId(r))

    fun memberColor(name: String): Long = friends.firstOrNull { it.name == name }?.color ?: 0xFF8B8F98

    val orderedRooms: List<Room>
        get() = rooms.withIndex().sortedWith(
            compareByDescending<IndexedValue<Room>> { if (it.value.id == tmpTop) 1 else 0 }
                .thenByDescending { act[it.value.id] ?: 0 }
                .thenBy { it.index },
        ).map { it.value }

    /** [안내 종료] 자리: 이 방 목적지로 안내 중이면서 이 방이 공유 중이거나 15초 카운트 중일 때만. */
    fun isEndSlot(r: Room): Boolean {
        val d = destId(r) ?: return false
        return navActive && nav.placeId == d && (sharing == r.id || pending?.roomId == r.id)
    }

    fun tripName(fallback: String): String = place(nav.placeId)?.title ?: fallback

    private fun guideTitle(name: String): String = (if (name.isNotEmpty()) Josa.ro(name) + " " else "") + "안내 중"

    /** 아래 파란 카드. 목록은 [room] = null, 대화방은 그 방. */
    fun dock(room: Room?): Dock? {
        if (!signedIn) return null
        val pend = pending != null
        val rid = pending?.roomId ?: sharing
        if (room != null && rid != null && !pend && rid != room.id) {
            val sr = this.room(rid)
            return Dock.Move(guideTitle(tripName(sr?.let { destOf(it)?.title }.orEmpty())), sr?.name.orEmpty(), room.id)
        }
        if (rid != null) {
            val r = this.room(rid)
            val title = guideTitle(tripName(r?.let { destOf(it)?.title }.orEmpty()))
            return if (pend) Dock.Counting(title, r?.name.orEmpty(), pending?.remain ?: 0, shareWait) else Dock.Sharing(title, r?.name.orEmpty())
        }
        if (!navActive) return null
        val pid = nav.placeId ?: return null
        val title = Josa.ro(place(pid)?.title.orEmpty()) + " 안내 중"
        if (room != null) return if (declined[room.id] == pid) null else Dock.AskRoom(title, room.id)
        return if (listDismissed == pid) null else Dock.AskList(title)
    }
}

/** 목록 카드에 보이는 마지막 대화 문구. 보낸 사람 이름 없이 내용만. */
fun describeMsg(m: Msg): String = when (m) {
    is TextMsg -> m.text
    is MyMsg -> m.text
    is SysMsg -> m.text
    is LocMsg ->
        if (m.kind == LocKind.HERE) Josa.ga(m.who) + " 위치를 공유했어요"
        else when (m.end) {
            ShareEnd.ARRIVED -> Josa.ga(m.who) + " 도착했어요"
            ShareEnd.STOPPED -> Josa.ga(m.who) + " 경로 공유를 멈췄어요"
            null -> Josa.ga(m.who) + " 경로를 공유했어요" + if (m.min > 0) " · ${leftText(m.min)} 남음" else ""
        }
    is MyLocMsg -> "위치를 공유했어요"
    is MyRouteMsg -> when (m.state) {
        ShareEnd.ARRIVED -> "도착했어요"
        ShareEnd.STOPPED -> "경로 공유를 멈췄어요"
        null -> "경로를 공유했어요" + if (m.min > 0) " · ${leftText(m.min)} 남음" else ""
    }
}
