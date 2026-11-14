package com.example.tokcar

import com.example.tokcar.core.FakeVoiceInput
import com.example.tokcar.core.LoginMode
import com.example.tokcar.core.Session
import com.example.tokcar.core.TokStore
import com.example.tokcar.core.UiState
import com.example.tokcar.data.FakeServer
import com.example.tokcar.data.LocKind
import com.example.tokcar.data.LocMsg
import com.example.tokcar.data.MyMsg
import com.example.tokcar.data.MyRouteMsg
import com.example.tokcar.data.Place
import com.example.tokcar.data.SeedLoader
import com.example.tokcar.data.ShareEnd
import com.example.tokcar.data.TextMsg
import com.example.tokcar.navi.NaviEvent
import com.example.tokcar.navi.NaviGateway
import com.example.tokcar.remote.RemoteApi
import com.example.tokcar.remote.RemoteEvent
import com.example.tokcar.remote.RemoteLogin
import com.example.tokcar.remote.RemoteUnauthorized
import com.example.tokcar.remote.SFriend
import com.example.tokcar.remote.SLive
import com.example.tokcar.remote.SMessage
import com.example.tokcar.remote.SPlace
import com.example.tokcar.remote.SRoom
import com.example.tokcar.remote.SRoute
import com.example.tokcar.remote.SUser
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 가족 서버 흉내: 정해 둔 값으로 답하고, 앱이 보낸 프레임을 모은다. */
private class FakeRemote : RemoteApi {
    val flow = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<RemoteEvent> = flow
    override var token: String? = null
    val sent = mutableListOf<JSONObject>()
    val added = mutableListOf<SPlace>()
    var connected = false
    var loggedOut = false
    var meFails: Exception? = null

    val papa = SUser(1, "아빠")
    val minji = SUser(2, "민지")
    var msgs = listOf(
        SMessage(5, 1, 2, "TEXT", T0, "아빠 치킨 사와", null, null),
        SMessage(6, 1, 1, "TEXT", T0, "알았어", null, null),
        SMessage(7, 1, 2, "LOCATION", T0, null, SPlace("정자동", "성남시 분당구 정자일로 95", 37.3671, 127.1086), null),
    )

    override suspend fun login(loginId: String, password: String) =
        if (password == "right") RemoteLogin.Ok("tok", papa) else RemoteLogin.Bad(3)

    override suspend fun logout() { loggedOut = true }
    override suspend fun me(): SUser = meFails?.let { throw it } ?: papa
    override suspend fun friends() = listOf(SFriend(2, "민지", "시험 기간"))
    override suspend fun rooms() = listOf(SRoom(1, "우리 가족", listOf(papa, minji), mapOf(1L to 6L, 2L to 6L), msgs.last(), 1))
    override suspend fun messages(roomId: Long, after: Long?) = (if (roomId == 9L) msgs9 else msgs).filter { after == null || it.id > after }
    var roomPlaces = listOf(SPlace("이마트 분당점", "성남시 분당구 성남대로 331", 37.36, 127.11))
    override suspend fun places(roomId: Long) = roomPlaces
    val resets = mutableListOf<Long>()
    override suspend fun resetRoom(roomId: Long) { resets += roomId }
    var addResult: List<SPlace>? = null    // 서버가 돌려주는 최신 목록(없으면 방금 넣은 곳 하나)
    override suspend fun addPlace(roomId: Long, place: SPlace): List<SPlace> { added += place; return addResult ?: listOf(place) }
    val created = mutableListOf<Pair<String, List<Long>>>()
    val invited = mutableListOf<Pair<Long, List<Long>>>()
    val left = mutableListOf<Long>()
    var reject: Exception? = null
    override suspend fun createRoom(name: String, memberIds: List<Long>): SRoom {
        reject?.let { throw it }
        created += name to memberIds
        val others = memberIds.map { SUser(it, if (it == 2L) "민지" else "엄마") }
        val sys = SMessage(40, 9, null, "SYSTEM", T0, "아빠가 방을 만들었어요 · ${others.joinToString(", ") { it.name }} 초대", null, null)
        msgs9 = listOf(sys)
        return SRoom(9, name.ifBlank { others.joinToString(", ") { it.name } }, listOf(papa) + others, emptyMap(), sys, 0)
    }
    var msgs9 = listOf<SMessage>()
    override suspend fun invite(roomId: Long, userIds: List<Long>): SRoom {
        reject?.let { throw it }
        invited += roomId to userIds
        return SRoom(roomId, "우리 가족", listOf(papa, minji, SUser(3, "엄마")), mapOf(1L to 6L, 2L to 6L), null, 0)
    }
    override suspend fun leave(roomId: Long) { left += roomId }
    override fun connect() { connected = true }
    override fun disconnect() { connected = false }
    override fun send(frame: JSONObject): Boolean { sent += frame; return connected }

    fun sentOf(type: String) = sent.filter { it.getString("type") == type }

    companion object {
        const val T0 = 1_700_000_000_000L
    }
}

private class Gw : NaviGateway {
    override val label = "가짜"
    override val maxWaypoints = 5
    val flow = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<NaviEvent> = flow
    override fun start() {}
    override fun release() {}
    override fun requestRoute(dest: Place, vias: List<Place>) {}
    override fun addWaypoint(via: Place, position: Int) {}
    override fun removeWaypoint(position: Int) {}
    override fun cancelRoute() {}
    override fun openSearchScreen(keyword: String) {}
}

private class RFixture(scope: CoroutineScope, session: Session?, token: String?) {
    val seed = SeedLoader.fromJson(File("src/main/assets/dummy/seed.json").readText())
    val remote = FakeRemote()
    val gw = Gw()
    var now = FakeRemote.T0
    var savedToken: String? = token
    var savedSession: Session? = session
    val store = TokStore(
        scope, FakeServer(seed, scope), { gw }, voice = FakeVoiceInput("", 100), session = session,
        onSessionChanged = { savedSession = it }, nowMs = { now },
        remote = remote, remoteToken = token, onTokenChanged = { savedToken = it },
    )
    val ui: UiState get() = store.ui.value
}

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteStoreTest {
    private fun TestScope.fixture(session: Session? = null, token: String? = null) = RFixture(backgroundScope, session, token).also { runCurrent() }

    private fun TestScope.loggedIn(): RFixture {
        val t = fixture()
        t.store.startLogin(LoginMode.LOGIN)
        t.store.setLoginId("papa"); t.store.setPw("right")
        t.store.doLogin(); runCurrent()
        return t
    }

    /**
     * 서버 장소 id 는 좌표로 만들어서, 「집」과 내비가 같은 자리에 주소 글자 이름으로 알린 곳이 같은 id 가 된다.
     * 서버 목록은 최신순이라 가장 최근에 쓴 「집」이 이겨야 한다(예전엔 뒤쪽 「경기도 … (양지동)」이 덮어썼다).
     */
    @Test
    fun placesInTheSameCoordinateCellKeepTheNewestName() = runTest {
        val t = fixture()
        t.remote.roomPlaces = listOf(
            SPlace("집", "성남시 수정구 산성대로 501", 37.456205, 127.162272),
            SPlace("경기도 성남시 수정구 산성대로 501 (양지동)", "수정구 양지동", 37.456212017, 127.162282375),
            SPlace("서현역", "성남시 분당구 서현로 170", 37.385, 127.123),
        )
        t.store.startLogin(LoginMode.LOGIN)
        t.store.setLoginId("papa"); t.store.setPw("right"); t.store.doLogin(); runCurrent()
        val hist = t.ui.hist.getValue("1")
        assertEquals("같은 칸은 한 곳으로", 2, hist.size)
        assertEquals("집", t.ui.places.getValue(hist.first()).name)
        assertEquals("방 목적지도 집", "집", t.ui.destOf(t.ui.rooms.single())?.name)
    }

    /**
     * 「집」으로 안내를 시작하면 서버에 넣고 최신 목록을 돌려받는데, 그 목록에 같은 칸의 옛 「경기도 … (양지동)」이 뒤에 있어도
     * 방금 고른 「집」의 이름이 그것으로 덮어씌워지면 안 된다(안내가 끝나면 「양지동」으로 바뀌던 문제).
     */
    @Test
    fun startingGuidanceKeepsTheChosenNameWhenTheServerListHasASameCellPlace() = runTest {
        val home = SPlace("집", "성남시 수정구 산성대로 501", 37.456205, 127.162272)
        val echo = SPlace("경기도 성남시 수정구 산성대로 501 (양지동)", "수정구 양지동", 37.456212017, 127.162282375)
        val t = fixture()
        t.remote.roomPlaces = listOf(home, echo)
        t.remote.addResult = listOf(home, echo)
        t.store.startLogin(LoginMode.LOGIN)
        t.store.setLoginId("papa"); t.store.setPw("right"); t.store.doLogin(); runCurrent()
        val id = t.ui.hist.getValue("1").first()
        t.store.setDest("1", id); runCurrent()
        assertEquals("집", t.ui.places.getValue(id).name)
        assertEquals("목록에도 같은 칸이 한 번만", 1, t.ui.hist.getValue("1").count { it == id })
    }

    /** (개발용) 「우리 가족 데이터 정리」: 서버에 방 초기화를 요청하고, 그 방의 대화 · 안 읽은 수를 비우고 최근 목적지를 서버 값으로 다시 받는다. */
    @Test
    fun resetFamilyDataClearsTheRoomAndReloadsItsRecentPlaces() = runTest {
        val t = loggedIn()
        assertTrue(t.ui.msgs.getValue("1").isNotEmpty())
        assertTrue(t.ui.rooms.single().unread > 0)
        t.remote.roomPlaces = listOf(SPlace("집", "성남시 수정구 산성대로 501", 37.456205, 127.162272))
        t.store.resetFamilyData(); runCurrent()
        assertEquals(listOf(1L), t.remote.resets)
        assertTrue(t.ui.msgs.getValue("1").isEmpty())
        assertEquals(0, t.ui.rooms.single().unread)
        assertEquals("집", t.ui.destOf(t.ui.rooms.single())?.name)
    }

    /** 다른 기기(폰)가 정리해서 서버가 room.cleared 를 보내도 같은 결과다. */
    @Test
    fun roomClearedEventEmptiesTheRoom() = runTest {
        val t = loggedIn()
        t.remote.flow.emit(RemoteEvent.RoomCleared(1)); runCurrent()
        assertTrue(t.ui.msgs.getValue("1").isEmpty())
        assertEquals(0, t.ui.rooms.single().unread)
    }

    @Test
    fun loginLoadsFriendsRoomsAndMessagesFromServer() = runTest {
        val t = fixture()
        assertTrue("서버 모드에선 더미 방이 없음", t.ui.rooms.isEmpty())
        t.store.startLogin(LoginMode.LOGIN)
        t.store.setLoginId("papa"); t.store.setPw("wrong")
        t.store.doLogin(); runCurrent()
        assertFalse(t.ui.signedIn)
        assertEquals(3, t.ui.login.left)

        t.store.setPw("right"); t.store.doLogin(); runCurrent()
        assertTrue(t.ui.signedIn)
        assertEquals("tok", t.savedToken)
        assertTrue(t.remote.connected)
        assertEquals(listOf("민지"), t.ui.friends.map { it.name })
        val room = t.ui.rooms.single()
        assertEquals("1", room.id)
        assertEquals(listOf("민지"), room.members)
        assertEquals(1, room.unread)

        val msgs = t.ui.msgs.getValue("1")
        assertEquals("아빠 치킨 사와", (msgs[0] as TextMsg).text)
        assertEquals("민지", (msgs[0] as TextMsg).who)
        val mine = msgs[1] as MyMsg
        assertEquals("민지가 6까지 읽어서 내 6번은 읽음", setOf("민지"), mine.readBy)
        val loc = msgs[2] as LocMsg
        assertEquals(LocKind.HERE, loc.kind)
        assertEquals("정자동", t.ui.places.getValue(loc.placeId).name)
        assertEquals("이 방에서 간 곳도 서버 것", "이마트 분당점", t.ui.places.getValue(t.ui.hist.getValue("1").single()).name)
        // 방의 목적지는 서버 기록의 가장 최근 곳: 목록 카드가 「아직 없어요」가 아니다
        assertEquals("이마트 분당점", t.ui.destOf(room)?.name)
    }

    @Test
    fun textGoesOverSocketAndShowsOnAckThenReadArrives() = runTest {
        val t = loggedIn()
        t.store.openRoom("1"); runCurrent()
        val read = t.remote.sentOf("read").single()
        assertEquals("방을 열면 마지막 메시지까지 읽음", 7L, read.getLong("msgId"))
        // 남의 메시지에도 안 읽은 수: 민지가 보낸 7번은 아빠(나)가 아직 안 읽어서 1, 서버가 내 읽음을 돌려주면 0
        assertEquals(0, (t.ui.msgs.getValue("1")[0] as TextMsg).unread)
        assertEquals(1, (t.ui.msgs.getValue("1")[2] as LocMsg).unread)
        t.remote.flow.emit(RemoteEvent.Read(1, 1, 7)); runCurrent()
        assertEquals(0, (t.ui.msgs.getValue("1")[2] as LocMsg).unread)
        t.remote.flow.emit(RemoteEvent.Read(1, 1, 7)); runCurrent()    // 같은 읽음이 또 와도 그대로
        assertEquals(0, (t.ui.msgs.getValue("1")[2] as LocMsg).unread)

        t.store.send("1", " 지금 출발 "); runCurrent()
        val f = t.remote.sentOf("text.send").single()
        assertEquals(1L, f.getLong("roomId"))
        assertEquals("지금 출발", f.getString("text"))
        assertEquals("ack 전엔 아직 안 보임", 3, t.ui.msgs.getValue("1").size)

        t.remote.flow.emit(RemoteEvent.Ack(f.getString("ref"), SMessage(10, 1, 1, "TEXT", FakeRemote.T0, "지금 출발", null, null))); runCurrent()
        val mine = t.ui.msgs.getValue("1").last() as MyMsg
        assertEquals(10L, mine.id)
        assertEquals(1, mine.unreadCount)

        t.remote.flow.emit(RemoteEvent.Read(1, 2, 10)); runCurrent()
        assertEquals(0, (t.ui.msgs.getValue("1").last() as MyMsg).unreadCount)
    }

    @Test
    fun incomingCountsUnreadOutsideAndReadsInside() = runTest {
        val t = loggedIn()
        t.remote.flow.emit(RemoteEvent.New(SMessage(11, 1, 2, "TEXT", FakeRemote.T0, "언제 와?", null, null))); runCurrent()
        assertEquals(1, t.ui.newIn["1"])
        assertEquals("언제 와?", t.ui.lastText["1"])
        t.store.openRoom("1"); runCurrent()
        assertEquals(11L, t.remote.sentOf("read").last().getLong("msgId"))
        t.remote.flow.emit(RemoteEvent.New(SMessage(12, 1, 2, "TEXT", FakeRemote.T0, "?", null, null))); runCurrent()
        assertEquals("방 안에서 받으면 바로 읽음", 12L, t.remote.sentOf("read").last().getLong("msgId"))
    }

    @Test
    fun routeShareStartsOnFirstPositionStreamsLiveAndEndsOnArrival() = runTest {
        val t = loggedIn()
        val dest = t.ui.places.keys.first { !it.startsWith("ext:") }
        t.store.setDest("1", dest); runCurrent()
        assertEquals("안내를 시작한 곳을 서버에 넣음", t.ui.places.getValue(dest).short, t.remote.added.single().name)
        t.gw.flow.emit(NaviEvent.Guiding); runCurrent()
        advanceTimeBy(TokStore.SHARE_WAIT_SEC * 1000L + 100); runCurrent()
        assertEquals("1", t.ui.sharing)
        assertTrue("차 위치를 모르면 아직 시작 안 보냄", t.remote.sentOf("route.start").isEmpty())

        t.gw.flow.emit(NaviEvent.Progress(600, 1200, 5000, 37.38, 127.12, 0.0)); runCurrent()
        val start = t.remote.sentOf("route.start").single()
        assertEquals(37.38, start.getJSONObject("from").getDouble("lat"), 1e-9)
        assertEquals(600, start.getJSONObject("live").getInt("remainSec"))
        val local = t.ui.msgs.getValue("1").last() as MyRouteMsg
        assertTrue(local.id < 0)

        val server = SMessage(20, 1, 1, "ROUTE", FakeRemote.T0, null, null,
            SRoute("ACTIVE", SPlace("", "판교", 37.38, 127.12), SPlace("목적지", "주소", 37.3, 127.1), SLive(37.38, 127.12, 600, 50, FakeRemote.T0 + 600_000, null)))
        t.remote.flow.emit(RemoteEvent.Ack(start.getString("ref"), server)); runCurrent()
        assertTrue("카드가 서버 id 로 바뀜", t.ui.msgs.getValue("1").any { it is MyRouteMsg && it.id == 20L })
        assertTrue(t.ui.msgs.getValue("1").none { it.id == local.id })

        // 5초 안의 위치는 안 보내고, 5초가 지나면 보냄
        t.gw.flow.emit(NaviEvent.Progress(590, 1200, 4900, 37.379, 127.119, 0.0)); runCurrent()
        assertTrue(t.remote.sentOf("route.live").isEmpty())
        t.now += TokStore.LIVE_GAP_MS
        t.gw.flow.emit(NaviEvent.Progress(580, 1200, 4800, 37.378, 127.118, 0.0)); runCurrent()
        val live = t.remote.sentOf("route.live").single()
        assertEquals(580, live.getInt("remainSec"))
        // 차의 내 카드도 진행 정보마다 바뀜(서버는 보낸 차에게 route.live 를 돌려주지 않음)
        assertEquals(10, (t.ui.msgs.getValue("1").first { it.id == 20L } as MyRouteMsg).min)
        assertEquals(37.378, live.getDouble("carLat"), 1e-9)

        t.gw.flow.emit(NaviEvent.Arrived); runCurrent()
        val end = t.remote.sentOf("route.end").single()
        assertEquals("ARRIVED", end.getString("reason"))
        assertEquals(ShareEnd.ARRIVED, (t.ui.msgs.getValue("1").first { it.id == 20L } as MyRouteMsg).state)
    }

    @Test
    fun familyRouteCardFollowsLiveAndEnd() = runTest {
        val t = loggedIn()
        val card = SMessage(30, 1, 2, "ROUTE", FakeRemote.T0, null, null,
            SRoute("ACTIVE", SPlace("", "서현로 210", 37.38, 127.12), SPlace("민지 학원", "정자일로 95", 37.3671, 127.1086), null))
        t.remote.flow.emit(RemoteEvent.New(card)); runCurrent()
        val m = t.ui.msgs.getValue("1").last() as LocMsg
        assertEquals(LocKind.DEST, m.kind)
        assertEquals("민지 학원", t.ui.places.getValue(m.placeId).name)

        t.remote.flow.emit(RemoteEvent.Live(1, 30, SLive(37.37, 127.11, 300, 70, FakeRemote.T0 + 300_000, null))); runCurrent()
        val live = t.ui.msgs.getValue("1").last() as LocMsg
        assertEquals(5, live.min)
        assertEquals(70, live.pct)

        t.remote.flow.emit(RemoteEvent.Updated(card.copy(route = card.route!!.copy(state = "ARRIVED")))); runCurrent()
        assertEquals(ShareEnd.ARRIVED, (t.ui.msgs.getValue("1").last() as LocMsg).end)
        assertEquals("민지가 도착했어요", t.ui.lastText["1"])
    }

    @Test
    fun signupIsStillBlockedWithANotice() = runTest {
        val t = loggedIn()
        t.store.startLogin(LoginMode.SIGNUP)
        assertEquals("이 기능은 다음 단계에서 서버에 붙어요", t.ui.toast)
    }

    @Test
    fun createInviteLeaveAndDmGoThroughTheServer() = runTest {
        val t = loggedIn()
        // 방 만들기: 서버가 만든 방이 목록에 생기고 안내 글이 보임
        t.store.openNewRoom(); t.store.toggleNewMember("2"); t.store.createRoom(); runCurrent()
        assertEquals("" to listOf(2L), t.remote.created.single())
        assertEquals("9", t.ui.roomId)
        assertTrue(t.ui.inChat)
        assertEquals("민지", t.ui.room("9")!!.name)
        assertEquals("아빠가 방을 만들었어요 · 민지 초대", (t.ui.msgs.getValue("9").single() as com.example.tokcar.data.SysMsg).text)
        // 같은 방 소식이 실시간으로 또 와도(room.updated) 하나만
        t.remote.flow.emit(RemoteEvent.RoomUpdated(SRoom(9, "민지", listOf(t.remote.papa, t.remote.minji), emptyMap(), null, 0))); runCurrent()
        assertEquals(1, t.ui.rooms.count { it.id == "9" })

        // 1:1: 민지와 둘만 있는 방(9)이 이미 있으니 새로 안 만들고 그 방을 엶
        t.store.back()
        t.store.openDm("2"); runCurrent()
        assertEquals(1, t.remote.created.size)
        assertEquals("9", t.ui.roomId)

        // 초대: 우리 가족에 엄마
        t.store.openRoom("1")
        t.store.openInvite(); t.store.toggleInvite("3"); t.store.inviteDone(); runCurrent()
        assertEquals(1L to listOf(3L), t.remote.invited.single())
        assertEquals(listOf("민지", "엄마"), t.ui.room("1")!!.members)

        // 거절은 서버 안내 글 그대로
        t.remote.reject = com.example.tokcar.remote.RemoteRejected("NOT_FRIEND", "친구만 초대할 수 있어요")
        t.store.openInvite(); t.store.toggleInvite("3"); t.store.inviteDone(); runCurrent()
        assertEquals("친구만 초대할 수 있어요", t.ui.toast)
        t.remote.reject = null

        // 나가기
        t.store.askLeave(); t.store.leaveRoom(); runCurrent()
        assertEquals(listOf(1L), t.remote.left)
        assertNull(t.ui.room("1"))
        assertFalse(t.ui.inChat)

        // 다른 기기에서 나간 방(room.removed)도 사라짐
        t.remote.flow.emit(RemoteEvent.RoomRemoved(9)); runCurrent()
        assertNull(t.ui.room("9"))
    }

    @Test
    fun savedTokenRestoresOrSignsOutWhenServerForgotIt() = runTest {
        val s = Session("papa", "아빠", "", "")
        val ok = fixture(s, "tok")
        assertTrue(ok.ui.signedIn)
        assertEquals("우리 가족", ok.ui.rooms.single().name)

        // 서버를 다시 켜서 토큰을 모르면 로그인 화면으로(복원은 init 에서 예약만 되고 runCurrent 때 돈다)
        val t = RFixture(backgroundScope, s, "old")
        t.remote.meFails = RemoteUnauthorized()
        runCurrent()
        assertFalse(t.ui.signedIn)
        assertNull(t.savedToken)
        assertNull(t.savedSession)
    }

    @Test
    fun naviPositionBecomesRoadAddressForTheRouteCard() = runTest {
        val seed = SeedLoader.fromJson(File("src/main/assets/dummy/seed.json").readText())
        val gw = Gw()
        var asked = 0
        val store = TokStore(backgroundScope, FakeServer(seed, backgroundScope), { gw }, voice = FakeVoiceInput("", 100),
            addressLookup = { _, _ -> asked++; "경기도 성남시 수정구 창업로40번길 20" })
        runCurrent()
        gw.flow.emit(NaviEvent.Here(37.4127212, 127.094681, "수정구 시흥동")); runCurrent()
        assertEquals("성남시 수정구 창업로40번길 20", store.ui.value.myAddr)
        gw.flow.emit(NaviEvent.Here(37.41273, 127.09469, "수정구 시흥동")); runCurrent()
        assertEquals("같은 자리(30m 안)는 다시 묻지 않음", 1, asked)
    }

    @Test
    fun logoutDropsTokenAndSocket() = runTest {
        val t = loggedIn()
        t.store.logout(); runCurrent()
        assertFalse(t.ui.signedIn)
        assertFalse(t.remote.connected)
        assertTrue(t.remote.loggedOut)
        assertNull(t.savedToken)
        assertTrue(t.ui.rooms.isEmpty())
    }
}
