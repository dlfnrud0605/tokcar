package com.example.tokcar.core

import com.example.tokcar.data.Account
import com.example.tokcar.data.FamilyServer
import com.example.tokcar.data.Friend
import com.example.tokcar.data.IncomingSpec
import com.example.tokcar.data.LocKind
import com.example.tokcar.data.LocMsg
import com.example.tokcar.data.LoginResult
import com.example.tokcar.data.MineMsg
import com.example.tokcar.data.Msg
import com.example.tokcar.data.MyLocMsg
import com.example.tokcar.data.MyMsg
import com.example.tokcar.data.MyRouteMsg
import com.example.tokcar.data.PhoneLookup
import com.example.tokcar.data.Place
import com.example.tokcar.data.Room
import com.example.tokcar.data.ServerEvent
import com.example.tokcar.data.SharedLocation
import com.example.tokcar.data.ShareEnd
import com.example.tokcar.data.SysMsg
import com.example.tokcar.data.TextMsg
import com.example.tokcar.navi.NaviEvent
import com.example.tokcar.navi.NaviGateway
import com.example.tokcar.remote.RemoteApi
import com.example.tokcar.remote.RemoteEvent
import com.example.tokcar.remote.RemoteLogin
import com.example.tokcar.remote.RemoteMapper
import com.example.tokcar.remote.RemoteUnauthorized
import com.example.tokcar.remote.SLive
import com.example.tokcar.remote.SMessage
import com.example.tokcar.remote.SPlace
import com.example.tokcar.remote.SRoom
import com.example.tokcar.remote.SUser
import com.example.tokcar.remote.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** 로그인 상태 유지에 저장하는 값(앱을 다시 켜도 로그인된 채로). */
data class Session(val id: String, val name: String, val phone: String, val phoneMask: String)

/**
 * TokCar 의 모든 상태와 동작. 화면 시안의 createApp 과 같은 규칙을 따른다.
 * 화면(Compose)은 [ui]만 보고, 동작은 이 클래스의 함수를 부른다.
 * 서버는 [FamilyServer], 내비는 [NaviGateway] 뒤에 숨겨 두어 더미/실제를 바꿔 끼울 수 있다.
 * [remote] 가 있으면 로그인 · 친구 · 방 · 대화 · 위치 · 경로 공유 · 이 방에서 간 곳을 가족 서버(server)로 주고받는다.
 * 가입 · 방 만들기 · 친구 추가 같은 2단계 기능은 서버에 아직 없어서 안내만 띄운다.
 */
class TokStore(
    private val scope: CoroutineScope,
    private val server: FamilyServer,
    private val gatewayFactory: (real: Boolean) -> NaviGateway,
    private val voice: VoiceInput = FakeVoiceInput(),
    private val speaker: Speaker = NoSpeaker,
    private val gearSource: GearSource = NoGearSource,
    initialReal: Boolean = false,
    private val onNaviModeChanged: (Boolean) -> Unit = {},
    private val initialDemoMove: Boolean = false,
    private val onDemoMoveChanged: (Boolean) -> Unit = {},
    private val shareWaitSec: Int = SHARE_WAIT_SEC,
    session: Session? = null,
    private val onSessionChanged: (Session?) -> Unit = {},
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val remote: RemoteApi? = null,
    remoteToken: String? = null,
    private val onTokenChanged: (String?) -> Unit = {},
    /** 내비 좌표 → 도로명 주소. 없으면 내비가 준 동 주소를 그대로 씀. */
    private val addressLookup: com.example.tokcar.data.AddressLookup? = null,
) {
    private val seed = server.seed
    private val lastRe = Regex("^[^:]{1,12}: ")
    private var gateway: NaviGateway = gatewayFactory(initialReal)

    private val _ui = MutableStateFlow(initialState(initialReal, session))
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val state: UiState get() = _ui.value

    /** 앱 안에서만 쓰는 메시지 id. 서버 모드에서는 서버 id(1부터)와 겹치지 않게 음수에서 시작. */
    private var msgSeq = if (remote != null) -1_000_000_000L else 1000L
    private var roomSeq = 0
    private var shareJob: Job? = null
    private var toastJob: Job? = null
    private var micJob: Job? = null
    private var naviJob: Job? = null
    private var searchJob: Job? = null
    private val heldSpeech = mutableListOf<String>()
    private var searchRoomId: String? = null

    // ───── 가족 서버 상태(서버 모드) ─────
    private var meId = 0L
    private val names = mutableMapOf<Long, String>()
    /** 방(서버 id) → 나를 뺀 멤버 id. */
    private val roomOthers = mutableMapOf<Long, List<Long>>()
    /** 방 → 사람마다 읽은 곳. */
    private val readUpTo = mutableMapOf<Long, MutableMap<Long, Long>>()
    /** 방 → 받은 마지막 메시지 id(다시 연결했을 때 빠진 것 채우기). */
    private val lastSeen = mutableMapOf<Long, Long>()
    private val readSent = mutableMapOf<Long, Long>()
    private var refSeq = 0
    /** route.start 를 보내고 ack 를 기다리는 중: ref → (방, 앱 카드 id). */
    private val routeStarting = mutableMapOf<String, Pair<String, Long>>()
    /** 서버에 진행 중인 내 경로 공유: 방 → 서버 메시지 id. */
    private val serverRoute = mutableMapOf<String, Long>()
    /** 카드는 만들었는데 차 위치를 몰라 아직 서버에 시작을 못 보낸 방. 첫 위치가 오면 보낸다. */
    private val routeNeedsStart = mutableSetOf<String>()
    private var lastPos: Pair<Double, Double>? = null
    private var lastLiveMs = 0L
    private var loadJob: Job? = null

    init {
        if (remote != null) {
            remote.token = remoteToken
            scope.launch { remote.events.collect(::onRemote) }
            if (session != null) {
                if (remoteToken != null) loadJob = scope.launch { restore() } else signOutLocal()
            }
        }
        scope.launch { server.events.collect(::onServerEvent) }
        scope.launch { gearSource.gear.collect { g -> update { copy(gear = g, plusOpen = plusOpen && g != Gear.DRIVE) } } }
        bindGateway()
    }

    private fun initialState(real: Boolean, session: Session?): UiState {
        if (remote != null) return UiState(
            places = seed.places, signedIn = session != null, myName = session?.name ?: seed.myName, myId = session?.id ?: "",
            myPhone = session?.phone.orEmpty(), phoneMask = session?.phoneMask.orEmpty(), myAddr = Address.withoutProvince(seed.myAddr),
            rooms = emptyList(), friends = emptyList(), msgs = emptyMap(), lastText = emptyMap(), hist = emptyMap(),
            favs = seed.favs, recent = seed.navRecent, navResults = seed.navResults,
            naviReal = real, naviLabel = gateway.label, maxWaypoints = gateway.maxWaypoints, shareWait = shareWaitSec, demoMove = initialDemoMove,
        )
        var id = 0L
        val msgs = seed.rooms.associate { r ->
            r.id to (seed.roomMsgs[r.id].orEmpty()).map { (who, text) -> TextMsg(++id, who, text) as Msg }
        }
        return UiState(
            places = seed.places,
            signedIn = session != null,
            myName = session?.name ?: seed.myName,
            myId = session?.id ?: "papa",
            myPhone = session?.phone ?: seed.myPhone,
            phoneMask = session?.phoneMask.orEmpty(),
            myAddr = Address.withoutProvince(seed.myAddr),
            rooms = seed.rooms,
            friends = seed.friends,
            msgs = msgs,
            lastText = seed.roomLast.mapValues { it.value.replace(lastRe, "") },
            act = seed.rooms.withIndex().associate { (i, r) -> r.id to seed.rooms.size - i },
            hist = seed.histories,
            favs = seed.favs,
            recent = seed.navRecent,
            navResults = seed.navResults,
            naviReal = real,
            demoMove = initialDemoMove,
            naviLabel = gateway.label,
            maxWaypoints = gateway.maxWaypoints,
            shareWait = shareWaitSec,
        )
    }

    private inline fun update(f: UiState.() -> UiState) = _ui.update { it.f() }

    // ═══════════════ 내비 연동 ═══════════════
    private fun bindGateway() {
        naviJob?.cancel()
        naviJob = scope.launch { gateway.events.collect(::onNavi) }
        gateway.start()
        update { copy(naviLabel = gateway.label, maxWaypoints = gateway.maxWaypoints) }
    }

    /** 설정(개발용): 데모 GPS <-> 실제 내비 전환. 고른 방식은 기기에 저장된다. */
    fun setNaviReal(real: Boolean) {
        if (real == state.naviReal) return
        switchGateway(real)
        onNaviModeChanged(real)
        toast(if (real) "실제 내비로 바꿨어요" else "데모 GPS로 바꿨어요")
    }

    /** 설정(개발용, 시연용): 실제 내비에 연결한 채 남은 시간은 움직이는 데모 이동 값으로. 실제 내비를 쓰는 중이면 바로 다시 연결한다. 고른 값은 기기에 저장된다. */
    fun setDemoMove(on: Boolean) {
        if (on == state.demoMove) return
        update { copy(demoMove = on) }
        onDemoMoveChanged(on)    // 게이트웨이를 만드는 쪽이 저장된 값을 읽으므로 먼저 저장한다
        if (state.naviReal) switchGateway(real = true)
        toast(if (on) "내비는 연결하고 이동은 데모 값으로 해요" else "이동도 실제 내비 값으로 해요")
    }

    private fun switchGateway(real: Boolean) {
        if (state.nav.state != NavState.IDLE) endRoute()
        gateway.release()
        gateway = gatewayFactory(real)
        update { copy(naviReal = real) }
        bindGateway()
    }

    private fun onNavi(e: NaviEvent) {
        when (e) {
            NaviEvent.Preview -> update { if (nav.state == NavState.IDLE) this else copy(nav = nav.copy(state = NavState.PREVIEW)) }
            NaviEvent.Guiding -> update { if (nav.state == NavState.PREVIEW) copy(nav = nav.copy(state = NavState.GUIDING)) else this }
            is NaviEvent.Progress -> {
                if (!state.navActive) return
                val p = if (e.totalSec > 0) (1f - e.remainSec.toFloat() / e.totalSec).coerceIn(0f, 1f) else 0f
                update { copy(nav = nav.copy(state = NavState.GUIDING, progress = p, remainSec = e.remainSec, remainMeters = e.remainMeters)) }
                lastPos = e.lat to e.lon
                state.sharing?.let {
                    fillRouteCard(it)
                    if (remote == null) server.pushLocation(SharedLocation(it, e.lat, e.lon, e.remainSec)) else remoteProgress(it)
                }
            }
            is NaviEvent.ExternalRoute -> onExternalRoute(e)
            NaviEvent.Arrived -> if (state.navActive) arrive()
            is NaviEvent.Traffic -> update { copy(traffic = if (navActive) e.segments else null) }
            is NaviEvent.Here -> onHere(e)
            NaviEvent.Cancelled -> if (state.navActive) endRoute(fromNavi = true)
            is NaviEvent.Error -> toast(e.message)
            // 실제 내비에 못 붙으면 데모 GPS로 바꿔 앱은 계속 쓸 수 있게 한다(고른 방식은 저장하지 않아 다음에 다시 실제 내비를 시도함)
            NaviEvent.ConnectFailed -> if (state.naviReal) {
                switchGateway(real = false)
                toast("내비에 연결하지 못해서 데모 GPS로 바꿨어요")
            }
        }
    }

    private fun placeOf(e: NaviEvent.ExternalRoute): Place =
        matchPlace(state.places, state.destByRoom.values, e.lat, e.lon)
            ?: Place(externalPlaceId(e.lat, e.lon), e.name.ifBlank { "내비 목적지" }, e.addr, e.lat, e.lon)

    private fun onExternalRoute(e: NaviEvent.ExternalRoute) {
        val searchRoom = searchRoomId?.takeIf { state.room(it) != null }
        val place = placeOf(e)
        when {
            searchRoom != null -> adoptSearchedRoute(searchRoom, place)
            // 앱이 시킨 경로를 내비가 되돌려 알리는 것은 목적지가 바뀐 게 아니다. 내비는 좌표를 도로에 맞춰 몇 m 다르게, 이름은 주소 글자로 바꿔서 알려 주는데
            // 이걸 새 목적지로 받으면 「집」이 「수정구 양지동」 같은 별도 장소로 바뀌었다 돌아오기를 되풀이한다.
            state.navActive && state.places[state.nav.placeId]?.let { distanceM(it.lat, it.lon, e.lat, e.lon) <= SAME_ROUTE_DEST_M } == true -> Unit
            !state.navActive -> {
                cancelPending()
                update {
                    copy(
                        places = places + (place.id to place), nav = NavUi(NavState.GUIDING, place.id), vias = emptyList(), traffic = null,
                        declined = emptyMap(), listDismissed = null,
                    )
                }
                toast("내비에서 안내 중이에요")
            }
            state.nav.placeId != place.id -> {
                // 앱에서 목적지를 바꾼 것([setDest])과 같게: 공유 중·공유 카운트 중인 방의 목적지도 바꾸고 경로 공유 카드를 고친다
                val rooms = listOfNotNull(state.sharing, state.pending?.roomId).distinct().mapNotNull { state.room(it) }
                update { copy(places = places + (place.id to place), nav = NavUi(NavState.GUIDING, place.id), vias = emptyList(), traffic = null) }
                rooms.forEach { r -> update { withRoomDest(r, place.id) } }
                state.sharing?.let { updateShareCard(it, place.id) }
                toast("내비에서 목적지를 ${Josa.ro(place.title)} 바꿨어요")
            }
        }
    }

    private fun adoptSearchedRoute(roomId: String, place: Place) {
        searchRoomId = null
        searchJob?.cancel()
        val room = state.room(roomId) ?: return
        val wasActive = state.navActive
        update {
            copy(places = places + (place.id to place), navSearching = false).withRoomDest(room, place.id)
                .copy(modal = null, nav = NavUi(NavState.GUIDING, place.id), vias = emptyList(), traffic = null)
        }
        val sharing = state.sharing
        if (wasActive && sharing != null) updateShareCard(sharing, place.id)
        if (!wasActive && sharing != roomId) beginShare(roomId)
        if (!state.inChat) openRoom(roomId)    // 내비 검색에서 고른 목적지도 목록에서 시작했다면 그 방으로 들어간다
    }

    private fun startRoute(placeId: String, vias: List<Via> = emptyList()) {
        val dest = state.places.getValue(placeId)
        update { copy(vias = vias, nav = NavUi(NavState.PREVIEW, placeId), traffic = null) }
        gateway.requestRoute(dest, vias.mapNotNull { state.places[it.placeId] })
    }

    fun endRoute(fromNavi: Boolean = false) {
        cancelPending()
        if (!fromNavi) gateway.cancelRoute()
        state.sharing?.let { stopSharingRoom(it) }
        update { copy(nav = NavUi(), vias = emptyList(), declined = emptyMap(), listDismissed = null, traffic = null) }
    }

    /** [안내 종료]: 안내를 끝내고 공유도 멈춘다. */
    fun endGuide() {
        endRoute()
        toast("안내를 종료했어요")
    }

    private fun arrive() {
        cancelPending()
        val sharing = state.sharing
        update {
            copy(
                nav = nav.copy(state = NavState.ARRIVED, progress = 1f, remainSec = 0, remainMeters = 0),
                vias = emptyList(), declined = emptyMap(), listDismissed = null, traffic = null,
            )
        }
        if (sharing != null) {
            endShareCard(sharing, ShareEnd.ARRIVED, Josa.ga(state.myName) + " 도착했어요")
            update { copy(sharing = null) }
            if (remote == null) server.shareStopped(sharing)
        }
    }

    // ═══════════════ 공유(15초 카운트) ═══════════════
    private fun cancelPending() {
        shareJob?.cancel()
        shareJob = null
        update { copy(pending = null, tmpTop = null) }
    }

    private fun beginShare(roomId: String) {
        cancelPending()
        update { copy(pending = Pending(roomId, shareWaitSec), tmpTop = roomId) }
        shareJob = scope.launch {
            repeat(shareWaitSec) {
                delay(1000L)
                update { pending?.let { copy(pending = it.copy(remain = it.remain - 1)) } ?: this }
            }
            commitShare()
        }
    }

    private fun commitShare() {
        val id = state.pending?.roomId ?: return
        shareJob = null
        update { copy(pending = null, tmpTop = null) }
        val old = state.sharing
        if (old != null && old != id) {
            endShareCard(old, ShareEnd.STOPPED, stoppedText())
            if (remote == null) server.shareStopped(old)
        }
        update { copy(sharing = id) }
        val room = state.room(id)
        val pid = (if (state.navActive) state.nav.placeId else room?.let { state.destId(it) })
        if (pid != null) shareCard(id, pid, fresh = false)
        if (remote == null) server.shareStarted(id)
    }

    private fun stoppedText() = Josa.ga(state.myName) + " 위치 공유를 멈췄어요"

    private fun stopSharingRoom(roomId: String) {
        endShareCard(roomId, ShareEnd.STOPPED, stoppedText())
        update { copy(sharing = null) }
        if (remote == null) server.shareStopped(roomId)
    }

    /**
     * [공유 취소]·[공유 중지] 뒤: 방 밖(목록)에서 취소하면 그 방에 들어갔을 때 "이 방에 공유할까요?"를 다시 묻는다.
     * 그 방 안에서 취소하면 바로 되묻지 않고([askAgain]), 방을 나갔다 다시 들어오면 묻는다. [닫기]는 같은 안내에서 다시 묻지 않는다.
     */
    private fun cancelAsk(roomId: String) {
        val pid = state.nav.placeId?.takeIf { state.navActive } ?: return
        if (state.inChat && state.roomId == roomId) update { copy(declined = declined + (roomId to pid), askAgain = askAgain + roomId, listDismissed = null) }
        else update { copy(listDismissed = null) }
    }

    /** 아래 카드의 [공유 취소](15초 카운트 중). 카드가 바뀌는 것이 알림이라 토스트는 띄우지 않는다. */
    fun cancelShare() {
        val room = state.pending?.roomId
        cancelPending()
        if (room != null) cancelAsk(room)
    }

    /** 아래 카드의 [공유 취소](공유 중): 공유를 멈추고 경로 공유 카드는 "공유 종료"로. 토스트 없음. */
    fun stopShare() {
        val id = state.sharing ?: return
        stopSharingRoom(id)
        update { copy(modal = null) }
        cancelAsk(id)
    }

    /** 안내 중인 경로를 이 방에 공유([이 방으로]·[공유]·방 고르기): 방의 목적지가 안내 중인 목적지로 바뀌고 15초 카운트. */
    fun shareTripTo(roomId: String) {
        val room = state.room(roomId) ?: return
        val pid = state.nav.placeId?.takeIf { state.navActive } ?: return
        val fromList = !state.inChat    // 목록의 [방 고르기]로 고른 것이면 공유를 시작한 뒤 그 방으로 들어간다
        update { copy(modal = null) }
        if (state.destId(room) != pid) update { withRoomDest(room, pid) }
        update { copy(declined = declined - roomId) }
        beginShare(roomId)
        if (fromList) openRoom(roomId)
    }

    fun declineHere(roomId: String) {
        val pid = state.nav.placeId?.takeIf { state.navActive } ?: return
        update { copy(declined = declined + (roomId to pid)) }
    }

    fun closeAsk() {
        val pid = state.nav.placeId?.takeIf { state.navActive } ?: return
        update { copy(listDismissed = pid) }
    }

    fun openPick() = update { copy(modal = Modal.Pick) }

    // ───── 경로 공유 카드 ─────
    private class RouteVals(val min: Int, val pct: Int, val eta: String, val fill: Boolean)

    private fun routeVals(placeId: String, fresh: Boolean): RouteVals {
        val n = state.nav
        if (!fresh && state.navActive && n.placeId == placeId && n.remainSec > 0) {
            return RouteVals(
                min = (n.remainSec + 59) / 60, pct = (n.progress * 100).toInt().coerceIn(0, 100),
                eta = clockText(nowMs() + n.remainSec * 1000L), fill = false,
            )
        }
        return RouteVals(0, 0, "", fill = true)
    }

    private fun lastRouteCard(roomId: String): MyRouteMsg? = state.msgs[roomId].orEmpty().lastOrNull { it is MyRouteMsg } as MyRouteMsg?

    private fun shareCard(roomId: String, placeId: String, fresh: Boolean) {
        val p = state.places[placeId] ?: return
        val room = state.room(roomId) ?: return
        val v = routeVals(placeId, fresh)
        val m = MyRouteMsg(nextId(), p.title, state.myAddr, p.fullAddr, v.min, v.pct, v.eta, state = null, fill = v.fill, total = room.members.size)
        addMsg(roomId, m)
        if (remote == null) server.messageSent(roomId, m.id, room.members) else remoteStartRoute(roomId, m.id, p)
    }

    private fun updateShareCard(roomId: String, placeId: String) {
        val p = state.places[placeId] ?: return
        val card = lastRouteCard(roomId)
        if (card == null) { shareCard(roomId, placeId, fresh = true); return }
        val v = routeVals(placeId, fresh = true)
        replaceMsg(roomId, card.id) { card.copy(name = p.title, from = state.myAddr, to = p.fullAddr, min = v.min, pct = v.pct, eta = v.eta, fill = v.fill) }
        val rid = roomId.toLongOrNull()
        if (remote != null && rid != null && serverRoute[roomId] != null) remote.send(Wire.frame("route.dest", rid).put("dest", Wire.placeJson(sPlace(p))))
    }

    /** 공유 중인 내 경로 공유 카드를 지금 진행 값(남은 시간 · 진행률 · 도착 예정)으로 맞춘다. 내비가 진행 정보를 줄 때마다. 바뀐 게 없으면 그대로. */
    private fun fillRouteCard(roomId: String) {
        val card = lastRouteCard(roomId)?.takeIf { it.state == null } ?: return
        val n = state.nav
        if (n.remainSec <= 0) return
        val min = (n.remainSec + 59) / 60
        val pct = (n.progress * 100).toInt().coerceIn(0, 100)
        val eta = clockText(nowMs() + n.remainSec * 1000L)
        if (!card.fill && card.min == min && card.pct == pct && card.eta == eta) return
        replaceMsg(roomId, card.id) { card.copy(min = min, pct = pct, eta = eta, fill = false) }
    }

    private fun endShareCard(roomId: String, end: ShareEnd, fallbackText: String) {
        if (remote != null) remoteEndRoute(roomId, end)
        val card = lastRouteCard(roomId)
        if (card == null) { if (state.room(roomId) != null) addMsg(roomId, SysMsg(nextId(), fallbackText)); return }
        if (card.state == null) replaceMsg(roomId, card.id) { card.copy(state = end, fill = false) }
    }

    // ═══════════════ 길안내 시작·목적지 ═══════════════
    private fun chooseRoute(roomId: String, placeId: String, vias: List<Via> = emptyList()) {
        val newTrip = !state.navActive
        startRoute(placeId, vias)
        remoteGuidedTo(roomId, placeId)
        if (newTrip && state.sharing != roomId) beginShare(roomId)
    }

    /**
     * 카드의 [안내](목록·대화방 위쪽): 길안내를 시작하고 15초 공유 카운트를 시작하며, 그 방을 연다.
     * 목적지가 없으면 방을 열고 목적지 팝업, 안내 중에 다른 곳으로 바꾸는 것이면 확인창을 먼저 띄운다([force] 가 아니면).
     */
    fun directNav(roomId: String, force: Boolean = false) {
        val room = state.room(roomId) ?: return
        val pid = state.destId(room)
        if (pid == null) {    // 목적지가 없으면 방을 열고 목적지 팝업
            openRoom(roomId)
            update { copy(modal = Modal.Dest(roomId)) }
            return
        }
        if (!force && state.navActive && !state.sameTrip(pid)) {
            update { copy(modal = Modal.Reroute(RerouteAct.CARD, roomId, pid)) }
            return
        }
        if (!state.sameTrip(pid)) {
            startRoute(pid)
            remoteGuidedTo(roomId, pid)
        }
        if (state.sharing != roomId) beginShare(roomId)
        openRoom(roomId)    // 안내를 시작하면 그 방으로 들어간다
    }

    fun openRoom(roomId: String) {
        state.room(roomId) ?: return
        update {
            copy(
                roomId = roomId, inChat = true, readRooms = readRooms + roomId,
                newIn = newIn + (roomId to 0), modal = null, plusOpen = false, drawer = false,
            )
        }
        markRead(roomId)
    }

    /** 대화방 뒤로: 글을 안 보낸 새 방은 버린다(공유 카운트가 돌거나 공유 중이면 시작한 것으로 보고 저장). */
    fun back() {
        state.roomId?.let { leaveDraft(it) }
        update {
            val id = roomId
            val again = id != null && id in askAgain
            copy(
                inChat = false, modal = null, plusOpen = false, drawer = false,
                declined = if (again) declined - id!! else declined, askAgain = if (again) askAgain - id!! else askAgain,
            )
        }
    }

    fun openDest(roomId: String) = update { copy(roomId = roomId, modal = Modal.Dest(roomId)) }

    /** 목적지 모달의 최근 목적지 카드: 그 목적지로 안내 시작. */
    fun startDest() {
        val roomId = (state.modal as? Modal.Dest)?.roomId ?: state.roomId ?: return
        val room = state.room(roomId) ?: return
        val pid = state.destId(room) ?: return
        val changed = state.navActive
        val fromList = !state.inChat    // 목록에서 연 목적지 팝업이면 시작한 뒤 그 방으로 들어간다([안내]와 같게)
        update { copy(modal = null) }
        chooseRoute(roomId, pid)
        if (changed) toast("경로를 ${Josa.ro(state.places.getValue(pid).title)} 바꿨어요")
        if (fromList) openRoom(roomId)
    }

    fun setDest(roomId: String, placeId: String) {
        val room = state.room(roomId) ?: return
        val already = state.navActive && state.nav.placeId == placeId
        val fromList = !state.inChat    // 목록에서 연 목적지 팝업이면 고른 뒤 그 방으로 들어간다([안내]와 같게)
        update { withRoomDest(room, placeId).copy(modal = null) }
        if (!already) {
            state.sharing?.let { updateShareCard(it, placeId) }
            chooseRoute(roomId, placeId)
        }
        if (fromList) openRoom(roomId)
    }

    /**
     * 목적지 모달의 [말하기]: 말한 것을 검색어로 내비 검색을 연다.
     * 실제 내비: 내비의 검색 결과 화면을 띄우고, 거기서 고른 목적지를 앱이 읽어 와 이 방의 목적지로 정한다.
     * 데모: 모달 안에 가짜 검색 결과 목록을 보인다.
     */
    fun destMic() = listenOnce { text -> navSearch(text) }

    private fun navSearch(word: String) {
        val m = state.modal as? Modal.Dest ?: return
        if (!state.naviReal) {
            update { copy(modal = m.copy(results = true, query = word)) }
            return
        }
        searchRoomId = m.roomId
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_WAIT_MS)
            searchRoomId = null
            update { copy(navSearching = false) }
        }
        update { copy(modal = null, navSearching = true) }
        gateway.openSearchScreen(word)
        toast("내비에서 \"$word\" 검색 결과를 골라 주세요")
    }

    /** 가족이 보낸 위치·경로 카드의 [안내]. */
    fun goShared(roomId: String, placeId: String, force: Boolean = false) {
        val room = state.room(roomId) ?: return
        if (state.sameTrip(placeId)) {
            toast("이미 안내 중이에요")
            return
        }
        if (!force && state.navActive) {
            update { copy(modal = Modal.Reroute(RerouteAct.GO, roomId, placeId)) }
            return
        }
        val changed = state.navActive
        val name = state.places.getValue(placeId).title
        update { withRoomDest(room, placeId) }
        if (changed) state.sharing?.let { updateShareCard(it, placeId) }
        chooseRoute(roomId, placeId)
        if (changed && state.sharing == null && state.pending == null) beginShare(roomId)
        if (changed) toast("경로를 ${Josa.ro(name)} 바꿨어요")
    }

    fun rerouteNo() = update { copy(modal = null) }

    fun rerouteOk() {
        val m = state.modal as? Modal.Reroute ?: return
        update { copy(modal = null) }
        when (m.act) {
            RerouteAct.GO -> goShared(m.roomId, m.placeId, force = true)
            RerouteAct.CARD -> directNav(m.roomId, force = true)
        }
    }

    /** 가족이 보낸 현재 위치 카드의 [경유지]: 넣기/빼기. */
    fun toggleVia(roomId: String, placeId: String) {
        val place = state.places.getValue(placeId)
        val name = place.title
        val idx = state.vias.indexOfFirst { it.placeId == placeId }
        when {
            idx >= 0 -> {
                update { copy(vias = vias.filterNot { it.placeId == placeId }) }
                gateway.removeWaypoint(idx)
                toast("경유지를 뺐어요 (${state.vias.size}곳)")
            }
            state.navActive -> {
                if (state.vias.size >= state.maxWaypoints) {
                    toast("경유지는 ${state.maxWaypoints}곳까지 넣을 수 있어요")
                    return
                }
                val pos = state.vias.size
                update { copy(vias = vias + Via(placeId, roomId)) }
                gateway.addWaypoint(place, pos)
                toast("${Josa.eul(name)} 경유지로 넣었어요 (${state.vias.size}곳)")
            }
            state.room(roomId)?.let { state.destId(it) } == null -> toast("방의 목적지를 먼저 정해 주세요")
            else -> {
                val dest = state.destId(state.room(roomId)!!)!!
                chooseRoute(roomId, dest, listOf(Via(placeId, roomId)))
                toast("${Josa.eul(name)} 경유해서 안내를 시작해요")
            }
        }
    }

    // ═══════════════ 대화 ═══════════════
    private fun nextId() = ++msgSeq

    private fun UiState.bumped(roomId: String): UiState =
        copy(act = act + (roomId to actN + 1), actN = actN + 1, tmpTop = if (tmpTop == roomId) null else tmpTop)

    /** 초안 방 저장: 첫 메시지(글·위치·경로 공유)를 보내는 순간 목록에 들어가고, 만든 안내 글이 맨 앞에 남는다. */
    private fun commitDraft(roomId: String) {
        val d = state.drafts[roomId] ?: return
        update {
            val head = d.sysText?.let { listOf(SysMsg(nextId(), it)) }.orEmpty()
            copy(
                rooms = listOf(d.room) + rooms, drafts = drafts - roomId,
                msgs = msgs + (roomId to head + msgs[roomId].orEmpty()),
            )
        }
    }

    private fun leaveDraft(roomId: String) {
        if (!state.isDraft(roomId)) return
        if (state.pending?.roomId == roomId || state.sharing == roomId) commitDraft(roomId)
        else update { copy(drafts = drafts - roomId, msgs = msgs - roomId, hist = hist - roomId, draft = draft - roomId) }
    }

    private fun addMsg(roomId: String, m: Msg) {
        commitDraft(roomId)
        update {
            copy(
                msgs = msgs + (roomId to (msgs[roomId].orEmpty() + m)),
                lastText = lastText + (roomId to describeMsg(m)),
                at = at + (roomId to "방금"),
            ).bumped(roomId)
        }
    }

    private fun replaceMsg(roomId: String, msgId: Long, f: (Msg) -> Msg) = update {
        val list = msgs[roomId] ?: return@update this
        val updated = list.map { if (it.id == msgId) f(it) else it }
        val last = updated.lastOrNull()
        copy(
            msgs = msgs + (roomId to updated),
            lastText = if (last != null && last.id == msgId) lastText + (roomId to describeMsg(last)) else lastText,
        )
    }

    fun setDraft(roomId: String, text: String) = update { copy(draft = draft + (roomId to text)) }

    private fun sendMine(room: Room, build: (id: Long, total: Int) -> MineMsg) {
        if (remote != null) return    // 서버 모드에서는 ack 가 오면 넣는다
        val m = build(nextId(), room.members.size)
        addMsg(room.id, m)
        server.messageSent(room.id, m.id, room.members)
    }

    fun send(roomId: String, text: String) {
        val t = text.trim()
        val room = state.room(roomId) ?: return
        if (t.isEmpty()) return
        if (remote != null) {
            val rid = serverRoomId(room) ?: return
            update { copy(draft = draft + (roomId to "")) }
            sendFrame(Wire.frame("text.send", rid, nextRef()).put("text", t))
            return
        }
        update { copy(draft = draft + (roomId to "")) }
        sendMine(room) { id, n -> MyMsg(id, t, n) }
    }

    /** + 메뉴 열기·닫기. 주행 중에는 위치를 보낼 수 없어 열리지 않는다. */
    fun togglePlus() = update { if (driving) copy(plusOpen = false) else copy(plusOpen = !plusOpen) }

    fun closePlus() = update { copy(plusOpen = false) }

    /** + 메뉴의 [내 위치 보내기]: 확인창 없이 바로 보낸다. */
    fun sendMyLocation() {
        val room = state.room(state.roomId) ?: return
        if (state.driving || !state.inChat) return
        update { copy(plusOpen = false) }
        if (remote != null) {
            val rid = serverRoomId(room) ?: return
            val pos = lastPos ?: state.places.values.firstOrNull { Address.withoutProvince(it.addr) == state.myAddr }?.let { it.lat to it.lon }
                ?: return toast("차 위치를 아직 몰라요. 안내를 시작하면 보낼 수 있어요")
            sendFrame(Wire.frame("location.send", rid, nextRef()).put("location", Wire.placeJson(SPlace(Address.area(state.myAddr), state.myAddr, pos.first, pos.second))))
            return
        }
        sendMine(room) { id, n -> MyLocMsg(id, state.myAddr, clockText(nowMs()), n) }
    }

    /** [말하기]: 알아들은 말은 바로 내 메시지로 보낸다. 듣는 중에 다시 누르면 멈춘다. */
    fun mic(roomId: String) = listenOnce { send(roomId, it) }

    private fun listenOnce(onHeard: (String) -> Unit) {
        if (state.listening) {
            micJob?.cancel()
            return
        }
        speaker.stop()
        update { copy(listening = true) }
        micJob = scope.launch {
            var text: String? = null
            var failure: String? = null
            try {
                text = withTimeoutOrNull(LISTEN_MAX_MS) { voice.listen() }
            } catch (e: VoiceException) {
                failure = e.reason
            } finally {
                update { copy(listening = false) }
                flushHeldSpeech()
            }
            when {
                failure != null -> toast(failure)
                text.isNullOrBlank() -> toast("잘 못 들었어요. 다시 말해 주세요")
                else -> onHeard(text.trim())
            }
        }
    }

    /** 한 줄을 소리 내어 읽는다. 말하기(듣는 중)에는 마이크에 읽는 소리가 들어가지 않게 끝난 뒤에 읽는다. */
    private fun speakLine(line: String) {
        if (state.listening) heldSpeech += line else speaker.speak(line)
    }

    /** 가족이 보낸 글을 소리 내어 읽는다: "엄마님의 메시지: 도착하면 전화해 줘". */
    private fun announce(who: String, text: String) {
        val body = speakable(text) ?: return
        speakLine("${who}님의 메시지: $body")
    }

    private fun flushHeldSpeech() {
        heldSpeech.forEach(speaker::speak)
        heldSpeech.clear()
    }

    private fun onServerEvent(e: ServerEvent) {
        if (remote != null) return    // 서버 모드에서는 더미 서버의 흉내(읽음 · 가족 메시지)를 쓰지 않는다
        when (e) {
            is ServerEvent.Read -> update {
                val list = msgs[e.roomId] ?: return@update this
                copy(msgs = msgs + (e.roomId to list.map { m -> if (m is MineMsg && m.id == e.msgId) m.withRead(e.who) else m }))
            }
            is ServerEvent.LocationReceived -> receive(e.spec)
            is ServerEvent.MessageReceived -> receiveText(e)
        }
    }

    /** 나간 방(목록에 없는 방)으로 오는 것은 무시한다. */
    private fun receiveText(e: ServerEvent.MessageReceived) {
        if (!state.signedIn || state.rooms.none { it.id == e.roomId }) return
        val inRoom = state.inChat && state.roomId == e.roomId
        addMsg(e.roomId, TextMsg(nextId(), e.who, e.text))
        if (!inRoom) update { copy(newIn = newIn + (e.roomId to ((newIn[e.roomId] ?: 0) + 1))) }
        announce(e.who, e.text)
    }

    private fun receive(spec: IncomingSpec) {
        if (!state.signedIn || state.rooms.none { it.id == spec.roomId }) return
        val inRoom = state.inChat && state.roomId == spec.roomId
        addMsg(spec.roomId, LocMsg(nextId(), spec.who, spec.kind, spec.placeId, spec.from, spec.min, spec.pct, spec.eta, time = clockText(nowMs())))
        if (!inRoom) update { copy(newIn = newIn + (spec.roomId to ((newIn[spec.roomId] ?: 0) + 1))) }
        // 가족이 위치·경로를 공유하면 소리로 알린다: "민지님이 경로 공유를 시작했습니다" / "엄마님이 위치 공유를 했습니다"
        speakLine(if (spec.kind == LocKind.DEST) "${spec.who}님이 경로 공유를 시작했습니다" else "${spec.who}님이 위치 공유를 했습니다")
    }

    /** (개발용) 가족 폰에서 위치·글을 보낸 것처럼 만든다. */
    fun simulateIncoming(key: String) = server.simulateIncoming(key)

    /**
     * (개발용, 시연 전) 「우리 가족」 방을 처음 상태로 돌린다: 서버에서 그 방의 대화 · 경로 공유를 지우고 최근 목적지를 처음 값으로 되돌린다.
     * 서버(local)를 쓸 때만 된다. 이 방에서 공유 중이면 안내를 끝낸 뒤에 하도록 막는다.
     */
    fun resetFamilyData() {
        val api = remote ?: return toast("서버에 연결했을 때만 쓸 수 있어요")
        val room = state.rooms.firstOrNull { it.name == FAMILY_ROOM_NAME } ?: return toast("「$FAMILY_ROOM_NAME」 방이 없어요")
        val rid = room.id.toLongOrNull() ?: return
        if (state.sharing == room.id || state.pending?.roomId == room.id) return toast("이 방에서 공유 중이에요. 안내를 끝내고 다시 눌러 주세요")
        scope.launch {
            runCatching { api.resetRoom(rid) }
                .onSuccess { clearRoom(rid); toast("「$FAMILY_ROOM_NAME」 방을 처음 상태로 돌렸어요") }
                .onFailure { toast(if (it is com.example.tokcar.remote.RemoteRejected) it.message ?: "정리하지 못했어요" else "정리하지 못했어요") }
        }
    }

    /** 서버가 방을 처음 상태로 돌렸다(room.cleared): 그 방의 대화 · 읽음 · 공유 표시를 비우고, 최근 목적지는 서버에서 다시 받아 방 목적지로 쓴다. */
    private fun clearRoom(rid: Long) {
        val id = "$rid"
        if (state.room(id) == null) return
        if (state.pending?.roomId == id) cancelPending()
        serverRoute.remove(id); routeNeedsStart -= id
        lastSeen[rid] = 0L; readUpTo[rid] = mutableMapOf(); readSent.remove(rid)
        update {
            copy(
                msgs = msgs + (id to emptyList()), lastText = lastText + (id to ""),
                rooms = rooms.map { if (it.id == id) it.copy(unread = 0) else it },
                sharing = if (sharing == id) null else sharing,
                hist = hist - id, destByRoom = destByRoom - id,
            )
        }
        val api = remote ?: return
        scope.launch {
            val ps = runCatching { RemoteMapper.placesOf(api.places(rid)) }.getOrNull() ?: return@launch
            update {
                copy(
                    places = places + ps.associateBy(Place::id), hist = hist + (id to ps.map { it.id }),
                    destByRoom = ps.firstOrNull()?.let { destByRoom + (id to it.id) } ?: destByRoom,
                )
            }
        }
    }

    // ═══════════════ 친구·새 방·대화상대 ═══════════════
    fun openTab(tab: Tab) = update { copy(tab = tab, modal = null, plusOpen = false) }
    fun closeModal() = update { copy(modal = null) }

    fun openFriend(id: String) = update { copy(modal = Modal.FriendProfile(id)) }

    /** 친구 프로필의 [1:1 채팅]. 1:1 방도 첫 메시지를 보내야 목록에 저장된다. */
    fun openDm(friendId: String) {
        if (remote != null) return remoteDm(friendId)
        val f = state.friend(friendId) ?: return
        val rid = "dm-${f.id}"
        if (state.room(rid) == null) {
            val room = Room(rid, f.name, f.placeId, f.color, f.ini, listOf(f.name), unread = 0, time = "방금")
            update { copy(drafts = drafts + (rid to Draft(room, null)), msgs = msgs + (rid to emptyList())) }
        }
        openRoom(rid)
    }

    fun openNewRoom() = update { copy(modal = Modal.NewRoom()) }

    fun toggleNewMember(friendId: String) = update {
        val m = modal as? Modal.NewRoom ?: return@update this
        copy(modal = m.copy(selected = if (friendId in m.selected) m.selected - friendId else m.selected + friendId))
    }

    fun setNewRoomName(text: String) = update {
        val m = modal as? Modal.NewRoom ?: return@update this
        copy(modal = m.copy(name = text.take(20)))
    }

    /** [방 만들기]: 목록에는 아직 넣지 않는다(첫 메시지를 보내야 저장). */
    fun createRoom() {
        val m = state.modal as? Modal.NewRoom ?: return
        if (m.selected.isEmpty()) return
        if (remote != null) return remoteCreateRoom(m.name.trim(), m.selected)
        val names = m.selected.mapNotNull { state.friend(it)?.name }
        val name = m.name.trim().ifEmpty { autoName(names) }
        val id = "room-${++roomSeq}"
        val color = ROOM_COLORS[(state.rooms.size + state.drafts.size) % ROOM_COLORS.size]
        val room = Room(id, name, null, color, name.take(1), names, unread = 0, time = "방금")
        val sys = Josa.ga(state.myName) + " 방을 만들었어요 · ${names.joinToString(", ")} 초대"
        update { copy(drafts = drafts + (id to Draft(room, sys)), msgs = msgs + (id to emptyList()), hist = hist + (id to emptyList()), modal = null) }
        openRoom(id)
    }

    fun openDrawer() = update { copy(drawer = true, plusOpen = false) }
    fun closeDrawer() = update { copy(drawer = false) }

    fun openInvite() = update { copy(drawer = false, modal = Modal.Invite()) }

    fun toggleInvite(friendId: String) = update {
        val m = modal as? Modal.Invite ?: return@update this
        copy(modal = m.copy(selected = if (friendId in m.selected) m.selected - friendId else m.selected + friendId))
    }

    /** [초대하기]: 고른 친구를 방에 넣는다. 저장된 방이면 안내 글을 남기고, 초안 방이면 인원만 바꾼다. */
    fun inviteDone() {
        val m = state.modal as? Modal.Invite ?: return
        val rid = state.roomId ?: return
        if (remote != null) return remoteInvite(rid, m.selected)
        val names = m.selected.mapNotNull { state.friend(it)?.name }
        if (names.isEmpty()) return
        val d = state.drafts[rid]
        if (d != null) {
            update { copy(drafts = drafts + (rid to d.copy(room = d.room.copy(members = d.room.members + names))), modal = null) }
        } else {
            update { copy(rooms = rooms.map { if (it.id == rid) it.copy(members = it.members + names) else it }, modal = null) }
            addMsg(rid, SysMsg(nextId(), Josa.ga(state.myName) + " 대화상대를 초대했어요 · ${names.joinToString(", ")}"))
        }
        toast("${names.size}명을 초대했어요")
    }

    fun askLeave() = update { copy(drawer = false, modal = Modal.Leave) }

    /** [나가기]: 방이 목록에서 사라진다. 그 방의 공유 카운트·공유도 끝낸다. */
    fun leaveRoom() {
        val id = state.roomId ?: return
        if (remote != null) return remoteLeave(id)
        if (state.pending?.roomId == id) cancelPending()
        if (state.sharing == id) {
            update { copy(sharing = null) }
            server.shareStopped(id)
        }
        update {
            copy(
                rooms = rooms.filterNot { it.id == id }, drafts = drafts - id, tmpTop = if (tmpTop == id) null else tmpTop,
                modal = null, drawer = false, inChat = false, roomId = null,
            )
        }
        toast("채팅방을 나갔어요")
    }

    // ───── 친구 추가 ─────
    fun openAddFriend() = update { copy(modal = Modal.AddFriend()) }

    /** 번호 입력: 하이픈을 자동으로 넣고, 번호를 고치면 이전 검색 결과는 지운다. */
    fun setAddTel(text: String) = update {
        val m = modal as? Modal.AddFriend ?: return@update this
        copy(modal = m.copy(tel = formatTyping(Countries.of("KR"), text).take(13), res = null))
    }

    fun findFriend() {
        if (notYet()) return
        val m = state.modal as? Modal.AddFriend ?: return
        val d = Phone.digits(m.tel)
        if (!Phone.isValid(d) || state.driving) return
        val res = server.lookup(d, Phone.digits(state.myPhone), state.friends)
        update { copy(modal = m.copy(res = res)) }
    }

    fun addFoundFriend() {
        val res = (state.modal as? Modal.AddFriend)?.res as? PhoneLookup.Found ?: return
        val f = res.friend
        update { copy(friends = friends + Friend(f.id, res.tel, f.name, f.ini, f.color, f.status, "home"), modal = null) }
        toast("${Josa.eul(f.name)} 친구로 추가했어요")
    }

    fun inviteSms() {
        update { copy(modal = null) }
        toast("앱 설치 링크가 담긴 초대 문자를 보냈어요 (더미)")
    }

    // ═══════════════ 회원가입·로그인 ═══════════════
    private inline fun updateLogin(f: LoginState.() -> LoginState) = update { copy(login = login.f()) }

    fun startLogin(mode: LoginMode) {
        if (remote != null && mode != LoginMode.LOGIN && notYet()) return
        startLoginLocal(mode)
    }

    private fun startLoginLocal(mode: LoginMode) = updateLogin {
        when (mode) {
            LoginMode.LOGIN -> LoginState(mode = mode, step = LoginStep.IDPW, left = FamilyServer.LOCK_N)
            LoginMode.SIGNUP, LoginMode.FINDID -> LoginState(mode = mode, step = LoginStep.PHONE)
            LoginMode.RESETPW -> LoginState(mode = mode, step = LoginStep.RID, rid = id.ifEmpty { rid })
        }
    }

    fun loginBack() = updateLogin {
        val l = copy(err = null)
        when (step) {
            LoginStep.IDPW -> LoginState()
            LoginStep.PHONE -> if (mode == LoginMode.FINDID) LoginState(mode = LoginMode.LOGIN, step = LoginStep.IDPW, left = FamilyServer.LOCK_N) else LoginState()
            LoginStep.RID, LoginStep.FDONE -> LoginState(mode = LoginMode.LOGIN, step = LoginStep.IDPW, left = FamilyServer.LOCK_N)
            LoginStep.CODE -> if (mode == LoginMode.RESETPW) l.copy(step = LoginStep.RID) else l.copy(step = LoginStep.PHONE, code = "")
            LoginStep.ACCOUNT, LoginStep.NEWPW -> l.copy(step = LoginStep.CODE)
            LoginStep.PROFILE -> l.copy(step = LoginStep.ACCOUNT)
            LoginStep.TERMS -> l.copy(step = LoginStep.PROFILE)
            LoginStep.WELCOME -> this
        }
    }

    fun openCountry() = update { copy(modal = Modal.Country) }

    fun pickCountry(code: String) = update {
        copy(login = login.copy(country = code, phone = formatTyping(Countries.of(code), login.phone), err = null), modal = null)
    }

    /** 번호 입력: 나라별 모양으로 하이픈·띄어쓰기를 자동으로 넣는다. */
    fun setLoginPhone(text: String) = updateLogin { copy(phone = formatTyping(Countries.of(country), text).take(18), err = null) }

    fun setLoginCode(text: String) = updateLogin { copy(code = text.filter(Char::isDigit).take(6)) }
    fun setLoginName(text: String) = updateLogin { copy(name = text.take(12)) }

    /** 로그인 아이디 입력. 잠긴 아이디면 바로 잠김 안내가 보이게 남은 횟수를 같이 읽는다. */
    fun setLoginId(text: String) {
        val v = text.take(16)
        updateLogin { copy(id = v, err = null, left = if (remote != null) FamilyServer.LOCK_N else server.triesLeft(v.trim().lowercase())) }
    }

    /** 회원가입 아이디 입력(영문 소문자로 바꿈). */
    fun setNewId(text: String) = updateLogin { copy(id = text.lowercase().filter { it.code < 128 && !it.isWhitespace() }.take(16)) }

    fun setRid(text: String) = updateLogin { copy(rid = text.take(16), err = null) }
    fun setPw(text: String) = updateLogin { copy(pw = text.take(20), err = null) }
    fun setPw2(text: String) = updateLogin { copy(pw2 = text.take(20)) }
    fun toggleShowPw() = updateLogin { copy(showPw = !showPw) }

    private fun loginKey(): String = state.login.let { PhoneIntl.e164(Countries.of(it.country), it.phone) }

    /** [인증번호 받기]: 회원가입은 이미 가입된 번호, 아이디 찾기는 가입 안 된 번호면 안내를 띄운다. */
    fun sendCode() {
        val l = state.login
        if (!PhoneIntl.isValid(Countries.of(l.country), l.phone) || l.busy) return
        val key = loginKey()
        val known = server.registeredName(key) != null
        if (l.mode == LoginMode.SIGNUP && known) { updateLogin { copy(err = LoginErr.KNOWN) }; return }
        if (l.mode == LoginMode.FINDID && !known) { updateLogin { copy(err = LoginErr.UNKNOWN) }; return }
        requestCode(key)
    }

    private fun requestCode(key: String) {
        scope.launch {
            updateLogin { copy(busy = true) }
            server.sendCode(key)
            updateLogin { copy(step = LoginStep.CODE, code = "", err = null, busy = false) }
        }
    }

    fun verify() {
        val l = state.login
        if (!Regex("^\\d{6}$").matches(l.code) || l.busy) return
        val key = loginKey()
        scope.launch {
            updateLogin { copy(busy = true) }
            val ok = server.verify(key, l.code)
            updateLogin { copy(busy = false) }
            if (!ok) return@launch toast("인증번호가 맞지 않아요")
            when (l.mode) {
                LoginMode.SIGNUP -> updateLogin { copy(step = LoginStep.ACCOUNT, id = "", idCheck = null, pw = "", pw2 = "") }
                LoginMode.RESETPW -> updateLogin { copy(step = LoginStep.NEWPW, pw = "", pw2 = "") }
                LoginMode.FINDID -> updateLogin { copy(step = LoginStep.FDONE, found = server.findIds(key)) }
                else -> Unit
            }
        }
    }

    fun resendCode() {
        val key = loginKey()
        scope.launch { server.sendCode(key) }
        toast("인증번호를 다시 보냈어요")
    }

    fun editPhone() = updateLogin { copy(step = LoginStep.PHONE, code = "") }

    /** [중복 확인]. */
    fun checkId() {
        val id = state.login.id
        if (!Rules.ID.matches(id)) return
        updateLogin { copy(idCheck = IdCheck(id, !server.idTaken(id))) }
    }

    /** 계정 만들기 [다음]: 아이디(중복 확인 끝)와 비밀번호가 모두 맞으면 이름으로. */
    fun accountNext() {
        val l = state.login
        val idOk = l.idCheck?.let { it.ok && it.value == l.id } == true
        if (!(idOk && Rules.pw(l.pw, l.id).ok && l.pw == l.pw2)) return
        updateLogin { copy(step = LoginStep.PROFILE) }
    }

    /** [로그인]: 연속 5번 틀리면 잠긴다. */
    fun doLogin() {
        val l = state.login
        val id = l.id.trim().lowercase()
        if (id.isEmpty() || l.pw.isEmpty()) return
        if (remote != null) return remoteLogin(id, l.pw)
        when (val r = server.login(id, l.pw)) {
            is LoginResult.Ok -> finishAuth(r.account, joined = false)
            is LoginResult.Bad -> updateLogin { copy(pw = "", err = LoginErr.BAD, left = r.left) }
            LoginResult.Locked -> updateLogin { copy(pw = "", err = LoginErr.BAD, left = 0) }
        }
    }

    /** 비밀번호 찾기: 아이디 -> 가입한 번호로 인증번호. */
    fun resetSend() {
        val id = state.login.rid.trim().lowercase()
        if (id.isEmpty()) return
        val a = server.account(id)
        if (a == null) { updateLogin { copy(err = LoginErr.NOID) }; return }
        val c = Countries.of(a.country)
        updateLogin { copy(rid = id, country = a.country, phone = formatTyping(c, a.phone), err = null) }
        requestCode(a.key)
    }

    /** 새 비밀번호 [비밀번호 바꾸기]: 잠금도 풀리고 로그인 화면으로. */
    fun newPwNext() {
        val l = state.login
        if (!(Rules.pw(l.pw, l.rid).ok && l.pw == l.pw2)) return
        server.resetPassword(l.rid, l.pw)
        update { copy(login = LoginState(mode = LoginMode.LOGIN, step = LoginStep.IDPW, id = l.rid, left = FamilyServer.LOCK_N)) }
        toast("비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요")
    }

    fun profileNext() {
        if (state.login.name.isBlank()) return
        updateLogin { copy(step = LoginStep.TERMS) }
    }

    fun toggleAgree(key: String) = updateLogin {
        copy(agree = when (key) {
            "tos" -> agree.copy(tos = !agree.tos)
            "priv" -> agree.copy(priv = !agree.priv)
            "loc" -> agree.copy(loc = !agree.loc)
            else -> agree.copy(mkt = !agree.mkt)
        })
    }

    fun agreeAll() = updateLogin { copy(agree = if (agree.all) Agree() else Agree(true, true, true, true)) }

    /** [가입하기]: 필수 약관 3개에 동의해야 한다. */
    fun join() {
        val l = state.login
        if (!l.agree.required || l.name.isBlank()) return
        val c = Countries.of(l.country)
        val a = Account(l.id, l.pw, loginKey(), c.code, l.phone.filter(Char::isDigit), l.name.trim())
        server.createAccount(a)
        finishAuth(a, joined = true)
    }

    private fun finishAuth(a: Account, joined: Boolean) {
        val c = Countries.of(a.country)
        val phone = if (c.code == "KR") "0" + PhoneIntl.national(c, a.phone) else a.key.filter { it.isDigit() }
        val s = Session(a.id, a.name, phone, PhoneIntl.mask(c, a.phone))
        update { copy(myName = s.name, myId = s.id, myPhone = s.phone, phoneMask = s.phoneMask, signedIn = true, tab = Tab.CHAT, login = LoginState()) }
        onSessionChanged(s)
        toast(a.name + if (joined) "님, 가입을 환영해요" else "님, 연결됐어요")
    }

    fun logout() {
        endRoute()
        if (remote != null) scope.launch { remote.logout() }
        signOutLocal()
    }

    private fun signOutLocal() {
        loadJob?.cancel()
        if (remote != null) {
            remote.disconnect()
            remote.token = null
            onTokenChanged(null)
            clearRemote()
            update { copy(rooms = emptyList(), friends = emptyList(), msgs = emptyMap(), lastText = emptyMap(), hist = emptyMap(), readRooms = emptySet(), newIn = emptyMap()) }
        }
        update { copy(signedIn = false, login = LoginState(), inChat = false, modal = null, plusOpen = false, drawer = false) }
        onSessionChanged(null)
    }

    fun privacy() = toast("개인정보 처리방침은 다음 단계에서 다뤄요")

    // ═══════════════ 내 자리(출발 주소) ═══════════════
    private var addrAt: Pair<Double, Double>? = null
    private var addrJob: Job? = null

    /**
     * 내비가 알려 준 차의 자리. 주소는 경로 공유 카드의 「출발」 · 내 위치 보내기에 쓴다(더미 seed 주소 대신).
     * Pleos 는 주소를 「수정구 시흥동」처럼 동까지만 줘서, [addressLookup] 이 있으면 좌표를 도로명 주소로 바꾼다.
     * 같은 자리(30m 안)면 다시 묻지 않는다. 「경기도」 같은 도 이름은 뺀다.
     */
    private fun onHere(e: NaviEvent.Here) {
        lastPos = e.lat to e.lon
        val lookup = addressLookup
        val navAddr = Address.withoutProvince(e.address.trim())
        if (lookup == null) {
            if (navAddr.isNotEmpty() && navAddr != state.myAddr) update { copy(myAddr = navAddr) }
            return
        }
        val at = addrAt
        if (at != null && distanceM(at.first, at.second, e.lat, e.lon) < ADDR_SAME_M) return
        addrAt = e.lat to e.lon
        addrJob?.cancel()
        addrJob = scope.launch {
            val found = lookup.addressOf(e.lat, e.lon)?.let(Address::withoutProvince)?.takeIf { it.isNotBlank() } ?: navAddr
            if (found.isNotEmpty() && found != state.myAddr) update { copy(myAddr = found) }
            if (found.isEmpty()) addrAt = null    // 못 찾았으면 다음 자리 값에서 다시
        }
    }

    // ═══════════════ 가족 서버(서버 모드) ═══════════════

    /** 2단계 기능(가입 · 방 만들기 · 친구 추가 …)은 서버 모드에서 아직 막는다. 막았으면 true. */
    private fun notYet(): Boolean {
        if (remote == null) return false
        update { copy(modal = null) }
        toast("이 기능은 다음 단계에서 서버에 붙어요")
        return true
    }

    private fun nextRef() = "c-${++refSeq}"

    private fun serverRoomId(room: Room): Long? = room.id.toLongOrNull() ?: run {
        toast("이 기능은 다음 단계에서 서버에 붙어요")
        null
    }

    private fun sendFrame(frame: JSONObject): Boolean {
        val ok = remote?.send(frame) == true
        if (!ok) toast("서버에 연결되지 않았어요. 잠시 뒤 다시 해 주세요")
        return ok
    }

    private fun remoteLogin(id: String, pw: String) {
        val api = remote ?: return
        scope.launch {
            updateLogin { copy(busy = true) }
            val r = api.login(id, pw)
            updateLogin { copy(busy = false) }
            when (r) {
                is RemoteLogin.Ok -> {
                    api.token = r.token
                    onTokenChanged(r.token)
                    val s = Session(id, r.me.name, "", "")
                    update { copy(myName = s.name, myId = s.id, myPhone = "", phoneMask = "", signedIn = true, tab = Tab.CHAT, login = LoginState()) }
                    onSessionChanged(s)
                    toast(r.me.name + "님, 연결됐어요")
                    loadJob = scope.launch { afterAuth(r.me) }
                }
                is RemoteLogin.Bad -> updateLogin { copy(pw = "", err = LoginErr.BAD, left = r.left ?: FamilyServer.LOCK_N) }
                RemoteLogin.Locked -> updateLogin { copy(pw = "", err = LoginErr.BAD, left = 0) }
                is RemoteLogin.Failed -> toast(r.reason)
            }
        }
    }

    /** 앱을 다시 켰을 때: 저장된 토큰으로 이어 붙는다. 서버가 모르는 토큰이면 로그인 화면으로, 못 붙으면 5초마다 다시. */
    private suspend fun restore() {
        val api = remote ?: return
        while (true) {
            try {
                afterAuth(api.me())
                return
            } catch (e: RemoteUnauthorized) {
                signOutLocal()
                toast("다시 로그인해 주세요")
                return
            } catch (e: Exception) {
                update { copy(toast = "서버에 연결하지 못했어요. 다시 시도하는 중…") }
                delay(RETRY_MS)
            }
        }
    }

    private suspend fun afterAuth(me: SUser) {
        val api = remote ?: return
        meId = me.id
        names[me.id] = me.name
        try {
            loadAll()
        } catch (e: RemoteUnauthorized) {
            signOutLocal()
            return toast("다시 로그인해 주세요")
        } catch (e: Exception) {
            toast("서버에서 대화를 받아 오지 못했어요")
        }
        api.connect()
    }

    /** 친구 · 방 · 방마다 최근 대화 · 이 방에서 간 곳을 받아 화면 상태로. */
    private suspend fun loadAll() {
        val api = remote ?: return
        val friends = api.friends()
        val rooms = api.rooms()
        friends.forEach { names[it.id] = it.name }
        rooms.forEach { r -> r.members.forEach { names[it.id] = it.name } }
        val newPlaces = mutableMapOf<String, Place>()
        val msgs = mutableMapOf<String, List<Msg>>()
        val hist = mutableMapOf<String, List<String>>()
        for (r in rooms) {
            remember(r)
            val list = api.messages(r.id)
            lastSeen[r.id] = list.maxOfOrNull { it.id } ?: 0L
            msgs["${r.id}"] = list.map { m -> mapper(r.id).map(m).also { newPlaces += it.places.associateBy(Place::id) }.msg }
            val ps = RemoteMapper.placesOf(api.places(r.id))
            newPlaces += ps.associateBy(Place::id)
            hist["${r.id}"] = ps.map { it.id }
        }
        val ordered = rooms.sortedByDescending { it.lastMessage?.id ?: 0L }
        update {
            copy(
                places = places + newPlaces,
                friends = friends.map { Friend("${it.id}", "", it.name, it.name.take(1), RemoteMapper.colorOf(it.id), it.status, null) },
                rooms = rooms.map(::appRoom),
                msgs = msgs,
                lastText = msgs.mapValues { (_, l) -> l.lastOrNull()?.let(::describeMsg).orEmpty() },
                at = emptyMap(),
                act = ordered.withIndex().associate { (i, r) -> "${r.id}" to ordered.size - i },
                actN = 100 + ordered.size,
                hist = hist,
                // 방의 목적지 = 「이 방에서 간 곳」의 가장 최근 곳(서버에 있음). 앱을 새로 깔아도 방마다 최근 목적지가 있게 한다.
                // 이 앱에서 이미 고른 목적지가 있으면 그대로 둔다.
                destByRoom = destByRoom + hist.filter { (id, list) -> list.isNotEmpty() && id !in destByRoom }.mapValues { it.value.first() },
                readRooms = emptySet(),
                newIn = emptyMap(),
            )
        }
        state.roomId?.takeIf { state.inChat }?.let(::markRead)
    }

    /** 서버 방의 멤버 · 읽은 곳을 기억한다(메시지 바꾸기 · 읽음에 씀). */
    private fun remember(r: SRoom) {
        r.members.forEach { names[it.id] = it.name }
        roomOthers[r.id] = r.members.map { it.id }.filter { it != meId }
        readUpTo[r.id] = r.readUpTo.toMutableMap()
        readSent[r.id] = maxOf(readSent[r.id] ?: 0L, r.readUpTo[meId] ?: 0L)
    }

    private fun appRoom(r: SRoom) = Room(
        "${r.id}", r.name, null, RemoteMapper.colorOf(r.id), r.name.take(1),
        r.members.filter { it.id != meId }.map { it.name }, r.unread, r.lastMessage?.let { clockText(it.createdAtMs) }.orEmpty(),
    )

    /**
     * 서버 방을 목록에 넣거나 바꾼다(방 만들기 · 초대 · room.updated). 처음 보는 방이면 메시지를 받아 온다.
     * 받는 사이에 실시간으로 온 메시지와 겹치지 않게 id 로 합친다.
     */
    private fun upsertRoom(r: SRoom) {
        remember(r)
        val id = "${r.id}"
        val known = state.rooms.any { it.id == id }
        val room = appRoom(r)
        update {
            if (known) copy(rooms = rooms.map { if (it.id == id) room.copy(unread = it.unread) else it })
            else copy(rooms = listOf(room) + rooms, msgs = msgs + (id to msgs[id].orEmpty())).bumped(id)
        }
        if (known) return
        val api = remote ?: return
        scope.launch {
            val list = runCatching { api.messages(r.id) }.getOrNull() ?: return@launch
            list.forEach { upsert(it, incoming = false) }
            update {
                val sorted = msgs[id].orEmpty().sortedBy { if (it.id > 0) it.id else Long.MAX_VALUE }
                copy(msgs = msgs + (id to sorted), lastText = lastText + (id to (sorted.lastOrNull()?.let(::describeMsg) ?: "")))
            }
            if (state.inChat && state.roomId == id) markRead(id)
        }
    }

    /** 방에서 빠짐(내가 나감 · room.removed). 그 방의 공유 기록도 지운다(서버가 이미 멈춤). */
    private fun dropRoom(id: String) {
        val rid = id.toLongOrNull() ?: return
        if (state.pending?.roomId == id) cancelPending()
        if (state.sharing == id) update { copy(sharing = null) }
        serverRoute.remove(id); routeNeedsStart -= id
        roomOthers.remove(rid); readUpTo.remove(rid); lastSeen.remove(rid); readSent.remove(rid)
        update {
            copy(
                rooms = rooms.filterNot { it.id == id }, msgs = msgs - id, tmpTop = if (tmpTop == id) null else tmpTop,
                inChat = if (roomId == id) false else inChat, roomId = if (roomId == id) null else roomId,
                modal = if (roomId == id) null else modal, drawer = if (roomId == id) false else drawer,
            )
        }
    }

    private fun friendIds(ids: List<String>): List<Long> = ids.mapNotNull { it.toLongOrNull() }

    private fun remoteCreateRoom(name: String, selected: List<String>) {
        val api = remote ?: return
        scope.launch {
            try {
                val r = api.createRoom(name, friendIds(selected))
                update { copy(modal = null) }
                upsertRoom(r)
                openRoom("${r.id}")
            } catch (e: Exception) {
                toast(remoteFailure(e))
            }
        }
    }

    /** 친구 프로필의 [1:1 채팅]: 그 친구와 둘만 있는 방이 있으면 열고, 없으면 만든다. */
    private fun remoteDm(friendId: String) {
        val f = state.friend(friendId) ?: return
        val uid = friendId.toLongOrNull() ?: return
        val existing = state.rooms.firstOrNull { r -> r.id.toLongOrNull()?.let { roomOthers[it] } == listOf(uid) }
        if (existing != null) {
            update { copy(modal = null) }
            return openRoom(existing.id)
        }
        val api = remote ?: return
        scope.launch {
            try {
                val r = api.createRoom(f.name, listOf(uid))
                update { copy(modal = null) }
                upsertRoom(r)
                openRoom("${r.id}")
            } catch (e: Exception) {
                toast(remoteFailure(e))
            }
        }
    }

    private fun remoteInvite(roomId: String, selected: List<String>) {
        val api = remote ?: return
        val rid = roomId.toLongOrNull() ?: return
        val ids = friendIds(selected)
        if (ids.isEmpty()) return
        scope.launch {
            try {
                upsertRoom(api.invite(rid, ids))
                update { copy(modal = null) }
                toast("${ids.size}명을 초대했어요")
            } catch (e: Exception) {
                toast(remoteFailure(e))
            }
        }
    }

    private fun remoteLeave(roomId: String) {
        val api = remote ?: return
        val rid = roomId.toLongOrNull() ?: return
        scope.launch {
            try {
                api.leave(rid)
                dropRoom(roomId)
                toast("채팅방을 나갔어요")
            } catch (e: Exception) {
                update { copy(modal = null) }
                toast(remoteFailure(e))
            }
        }
    }

    private fun remoteFailure(e: Exception): String = when (e) {
        is com.example.tokcar.remote.RemoteRejected -> e.message ?: "요청을 처리하지 못했어요"
        is RemoteUnauthorized -> "다시 로그인해 주세요"
        else -> "서버에 연결하지 못했어요"
    }

    private fun mapper(roomId: Long) = RemoteMapper(meId, names, roomOthers[roomId].orEmpty(), readUpTo[roomId].orEmpty())

    private fun clearRemote() {
        meId = 0L
        names.clear(); roomOthers.clear(); readUpTo.clear(); lastSeen.clear(); readSent.clear()
        routeStarting.clear(); serverRoute.clear(); routeNeedsStart.clear()
    }

    private fun onRemote(e: RemoteEvent) {
        if (meId == 0L) return
        when (e) {
            RemoteEvent.Connected -> scope.launch { catchUp() }
            RemoteEvent.Disconnected -> Unit
            is RemoteEvent.Ack -> {
                val starting = e.ref?.let { routeStarting.remove(it) }
                if (starting != null) routeStarted(starting.first, starting.second, e.message) else upsert(e.message, incoming = false)
            }
            is RemoteEvent.New -> upsert(e.message, incoming = true)
            is RemoteEvent.Updated -> upsert(e.message, incoming = false)
            is RemoteEvent.Live -> liveOf(e.roomId, e.messageId, e.live)
            is RemoteEvent.Read -> readOf(e.roomId, e.userId, e.msgId)
            is RemoteEvent.RoomUpdated -> upsertRoom(e.room)
            is RemoteEvent.RoomRemoved -> dropRoom("${e.roomId}")
            is RemoteEvent.RoomCleared -> clearRoom(e.roomId)
            is RemoteEvent.Error -> {
                val starting = e.ref?.let { routeStarting.remove(it) }
                if (starting != null && e.code == "ROUTE_ACTIVE") {
                    // 지난번에 끝내지 못한 공유가 서버에 남아 있음(앱을 다시 켠 경우 등): 그것을 멈추고 다시 시작
                    val rid = starting.first.toLongOrNull() ?: return
                    remote?.send(Wire.frame("route.end", rid).put("reason", "STOPPED"))
                    if (state.sharing == starting.first) state.place(state.nav.placeId)?.let { sendRouteStart(starting.first, starting.second, it) }
                } else if (e.code != "NO_ACTIVE_ROUTE") {
                    toast(e.message)
                }
            }
        }
    }

    /** 다시 연결됐을 때 끊긴 사이에 온 메시지를 채운다. */
    private suspend fun catchUp() {
        val api = remote ?: return
        for (rid in roomOthers.keys.toList()) {
            val got = runCatching { api.messages(rid, after = lastSeen[rid] ?: 0L) }.getOrNull() ?: continue
            got.forEach { upsert(it, incoming = true, announce = false) }
        }
    }

    /** 서버 메시지를 넣거나(새것) 같은 id 를 바꾼다(경로 공유 카드 변경 · 내 ack). */
    private fun upsert(m: SMessage, incoming: Boolean, announce: Boolean = true) {
        val roomId = "${m.roomId}"
        if (state.rooms.none { it.id == roomId }) return
        lastSeen[m.roomId] = maxOf(lastSeen[m.roomId] ?: 0L, m.id)
        val out = mapper(m.roomId).map(m)
        if (out.places.isNotEmpty()) update { copy(places = places + out.places.associateBy(Place::id)) }
        if (state.msgs[roomId].orEmpty().any { it.id == m.id }) {
            replaceMsg(roomId, m.id) { out.msg }
            return
        }
        addMsg(roomId, out.msg)
        if (!incoming || m.senderId == meId) return
        if (state.inChat && state.roomId == roomId) markRead(roomId)
        else update { copy(newIn = newIn + (roomId to ((newIn[roomId] ?: 0) + 1))) }
        if (!announce) return
        val who = names[m.senderId] ?: return
        when (m.kind) {
            "TEXT" -> announce(who, m.text.orEmpty())
            "LOCATION" -> speakLine("${who}님이 위치 공유를 했습니다")
            "ROUTE" -> speakLine("${who}님이 경로 공유를 시작했습니다")
        }
    }

    private fun liveOf(roomId: Long, msgId: Long, live: SLive) {
        val min = (live.remainSec + 59) / 60
        val eta = clockText(live.etaAtMs)
        replaceMsg("$roomId", msgId) { m ->
            when (m) {
                is LocMsg -> m.copy(min = min, pct = live.pct, eta = eta)
                is MyRouteMsg -> m.copy(min = min, pct = live.pct, eta = eta, fill = false)
                else -> m
            }
        }
    }

    /**
     * [userId] 가 [msgId] 까지 읽었다. 내 메시지는 읽은 사람에 넣고, 남의 메시지(그 사람이 보낸 것 빼고)는 안 읽은 수를 하나 줄인다.
     * 처음 읽는 구간(이전 읽은 곳 뒤 ~ msgId)만 바꿔서 같은 읽음이 두 번 와도 두 번 줄지 않는다.
     */
    private fun readOf(roomId: Long, userId: Long, msgId: Long) {
        val reads = readUpTo.getOrPut(roomId) { mutableMapOf() }
        val prev = reads[userId] ?: 0L
        if (msgId <= prev) return
        reads[userId] = msgId
        val who = names[userId] ?: return
        val fresh = (prev + 1)..msgId
        update {
            val list = msgs["$roomId"] ?: return@update this
            copy(msgs = msgs + ("$roomId" to list.map { m ->
                when {
                    m.id !in fresh -> m
                    m is MineMsg -> if (userId != meId && who !in m.readBy) m.withRead(who) else m
                    m is TextMsg && m.who != who -> m.copy(unread = (m.unread - 1).coerceAtLeast(0))
                    m is LocMsg && m.who != who -> m.copy(unread = (m.unread - 1).coerceAtLeast(0))
                    else -> m
                }
            }))
        }
    }

    /** 이 방을 보고 있으면 「여기까지 읽음」을 서버에 알린다(늘었을 때만). */
    private fun markRead(roomId: String) {
        val api = remote ?: return
        val rid = roomId.toLongOrNull() ?: return
        val top = state.msgs[roomId].orEmpty().maxOfOrNull { it.id }?.takeIf { it > 0 } ?: return
        if (top <= (readSent[rid] ?: 0L)) return
        if (api.send(Wire.frame("read", rid).put("msgId", top))) readSent[rid] = top
    }

    // ───── 경로 공유 ─────

    private fun sPlace(p: Place) = SPlace(p.short, p.addr.ifBlank { p.fullAddr }, p.lat, p.lon)

    private fun liveNow(): SLive? {
        val pos = lastPos ?: return null
        val n = state.nav
        if (n.remainSec <= 0) return null
        return SLive(pos.first, pos.second, n.remainSec, (n.progress * 100).toInt().coerceIn(0, 100), nowMs() + n.remainSec * 1000L, state.traffic)
    }

    /** 공유 카드를 만들 때: 차 위치를 알면 바로 서버에 시작을 보내고, 모르면 첫 위치가 올 때 보낸다. */
    private fun remoteStartRoute(roomId: String, cardId: Long, dest: Place) {
        if (roomId.toLongOrNull() == null) return
        if (lastPos == null) routeNeedsStart += roomId else sendRouteStart(roomId, cardId, dest)
    }

    private fun sendRouteStart(roomId: String, cardId: Long, dest: Place) {
        val api = remote ?: return
        val rid = roomId.toLongOrNull() ?: return
        val pos = lastPos ?: return
        val ref = nextRef()
        val f = Wire.frame("route.start", rid, ref)
            .put("from", Wire.placeJson(SPlace("", state.myAddr, pos.first, pos.second)))
            .put("dest", Wire.placeJson(sPlace(dest)))
        liveNow()?.let { f.put("live", Wire.liveJson(it)) }
        routeNeedsStart -= roomId
        if (api.send(f)) {
            routeStarting[ref] = roomId to cardId
            lastLiveMs = nowMs()
        } else {
            routeNeedsStart += roomId    // 다시 붙으면 다음 위치 때 보냄
        }
    }

    /** 서버가 경로 공유를 받았다: 앱 카드를 서버 id 카드로 바꾼다. 그 사이 공유를 멈췄으면 바로 끝낸다. */
    private fun routeStarted(roomId: String, cardId: Long, m: SMessage) {
        lastSeen[m.roomId] = maxOf(lastSeen[m.roomId] ?: 0L, m.id)
        val out = mapper(m.roomId).map(m)
        update { copy(places = places + out.places.associateBy(Place::id)) }
        val local = state.msgs[roomId].orEmpty().firstOrNull { it.id == cardId } as? MyRouteMsg
        replaceMsg(roomId, cardId) { (out.msg as? MyRouteMsg)?.copy(state = local?.state ?: (out.msg as MyRouteMsg).state) ?: out.msg }
        if (state.sharing == roomId && local?.state == null) {
            serverRoute[roomId] = m.id
        } else {
            remote?.send(Wire.frame("route.end", m.roomId).put("reason", if (local?.state == ShareEnd.ARRIVED) "ARRIVED" else "STOPPED"))
        }
    }

    private fun remoteProgress(roomId: String) {
        val api = remote ?: return
        if (roomId in routeNeedsStart) {
            val card = lastRouteCard(roomId) ?: return
            state.place(state.nav.placeId)?.let { sendRouteStart(roomId, card.id, it) }
            return
        }
        if (serverRoute[roomId] == null || nowMs() - lastLiveMs < liveGapMs()) return
        val rid = roomId.toLongOrNull() ?: return
        val live = liveNow() ?: return
        val f = Wire.liveJson(live).put("type", "route.live").put("roomId", rid)
        if (api.send(f)) lastLiveMs = nowMs()
    }

    /** 실시간 값을 서버로 보내는 간격. 데모 GPS는 빨리 움직여서 2초마다(서버도 같은 공유는 2초에 한 번만 전달), 실제 내비는 5초마다. */
    private fun liveGapMs() = if (state.naviReal) LIVE_GAP_MS else DEMO_LIVE_GAP_MS

    private fun remoteEndRoute(roomId: String, end: ShareEnd) {
        routeNeedsStart -= roomId
        serverRoute.remove(roomId) ?: return
        val rid = roomId.toLongOrNull() ?: return
        remote?.send(Wire.frame("route.end", rid).put("reason", end.name))
    }

    /** 이 방에서 경로 안내를 시작했다: 서버의 「이 방에서 간 곳」에 넣고, 돌아온 목록(최신 3개)으로 바꾼다. */
    private fun remoteGuidedTo(roomId: String, placeId: String) {
        val api = remote ?: return
        val rid = roomId.toLongOrNull() ?: return
        val p = state.places[placeId] ?: return
        scope.launch {
            val list = runCatching { api.addPlace(rid, sPlace(p)) }.getOrNull() ?: return@launch
            val ps = RemoteMapper.placesOf(list)    // 안내를 시작한 곳을 넣은 뒤 돌아온 목록도 같은 칸 중복을 정리해야 방금 고른 「집」이 덮어씌워지지 않는다
            update { copy(places = places + ps.associateBy(Place::id), hist = hist + (roomId to ps.map { it.id })) }
        }
    }

    // ═══════════════ 토스트 ═══════════════
    fun toast(text: String) {
        toastJob?.cancel()
        update { copy(toast = text) }
        toastJob = scope.launch {
            delay(TOAST_MS)
            update { copy(toast = null) }
        }
    }

    fun release() {
        gateway.release()
        naviJob?.cancel()
        shareJob?.cancel()
        micJob?.cancel()
        speaker.release()
        voice.release()
    }

    companion object {
        const val SHARE_WAIT_SEC = 15
        const val FAMILY_ROOM_NAME = "우리 가족"    // 개발용 방 정리가 찾는 방 이름(서버 DevSeed 의 가계정 방)
        const val LISTEN_MAX_MS = 15_000L
        private const val SAME_PLACE_M = 300.0
        private const val SAME_ROUTE_DEST_M = 100.0    // 안내 중인 목적지와 이만큼 안이면 내비가 알린 목적지는 같은 곳으로 본다

        /**
         * 내비가 알려 준 좌표와 같은 곳으로 볼 앱의 장소. 없으면 null(새 장소를 만든다).
         * 방 목적지로 쓰는 장소([roomDests])가 [SAME_ROUTE_DEST_M] 안에 있으면 그중 가장 가까운 것을 먼저 쓴다.
         * 내비는 좌표를 도로에 맞춰 몇 m 다르게 알려 주므로 그냥 가장 가까운 장소를 고르면, 예전에 내비가 알려 준 값으로 만들어 둔
         * 별도 장소(이름이 주소 글자, 좌표는 내비 값 그대로)가 「집」보다 가까워서 대신 뽑힌다.
         */
        internal fun matchPlace(places: Map<String, Place>, roomDests: Collection<String>, lat: Double, lon: Double): Place? {
            fun dist(p: Place) = distanceM(p.lat, p.lon, lat, lon)
            roomDests.mapNotNull { places[it] }.filter { dist(it) <= SAME_ROUTE_DEST_M }.minByOrNull(::dist)?.let { return it }
            return places.values.filter { dist(it) <= SAME_PLACE_M }.minByOrNull(::dist)
        }
        private const val SEARCH_WAIT_MS = 180_000L
        const val EXTERNAL_PLACE_PREFIX = "ext:"
        fun externalPlaceId(lat: Double, lon: Double) = "$EXTERNAL_PLACE_PREFIX%.4f,%.4f".format(java.util.Locale.US, lat, lon)
        private const val TOAST_MS = 2200L
        /** 서버로 경로 공유 실시간 값을 보내는 간격. */
        const val LIVE_GAP_MS = 5_000L
        const val DEMO_LIVE_GAP_MS = 2_000L
        private const val ADDR_SAME_M = 30.0
        private const val RETRY_MS = 5_000L
        val ROOM_COLORS = listOf(0xFFBE185D, 0xFF0E7490, 0xFF4338CA, 0xFFC2410C, 0xFF0F766E, 0xFF5B3FD0, 0xFF92600A)
    }
}

/** 방의 목적지로 정하고 "이 방에서 간 곳"과 내비 최근 목적지에 기록한다(시안의 setDest 규칙). 초안 방도 같다. */
private fun UiState.withRoomDest(room: Room, placeId: String): UiState = copy(
    destByRoom = destByRoom + (room.id to placeId),
    hist = hist + (room.id to (listOf(placeId) + histOf(room).filter { it != placeId }).take(5)),
    recent = if (placeId in favs) recent else (listOf(placeId) + recent.filter { it != placeId }).take(3),
)
